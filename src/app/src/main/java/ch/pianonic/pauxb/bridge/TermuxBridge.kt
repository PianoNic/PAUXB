package ch.pianonic.pauxb.bridge

import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.*
import java.io.*

/**
 * Communicates with Termux to manage the Debian proot environment and bridge daemon.
 * Uses Termux's RUN_COMMAND intent API for executing commands.
 */
class TermuxBridge(private val context: Context) {

    companion object {
        private const val TAG = "TermuxBridge"
        private const val TERMUX_PKG = "com.termux"
        private const val TERMUX_SERVICE = "com.termux.app.RunCommandService"
        private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
        private const val TERMUX_HOME = "/data/data/com.termux/files/home"
        private const val PAUXB_DIR = "$TERMUX_HOME/.pauxb"
        private const val SCRIPTS_DIR = "$TERMUX_HOME/.pauxb/scripts"
    }

    data class AppInstance(
        val appId: String,
        val command: String,
        val vncPort: Int,
        val display: Int,
        val running: Boolean
    )

    /**
     * Check if Termux is installed
     */
    fun isTermuxInstalled(): Boolean {
        return try {
            context.packageManager.getPackageInfo(TERMUX_PKG, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Copy bundled scripts to Termux's accessible storage via run-as
     */
    suspend fun deployScripts(): Boolean = withContext(Dispatchers.IO) {
        try {
            val assetManager = context.assets
            val scripts = assetManager.list("scripts") ?: return@withContext false

            for (script in scripts) {
                val content = assetManager.open("scripts/$script").bufferedReader().readText()
                // Write script to app's internal storage first
                val tempFile = File(context.filesDir, script)
                tempFile.writeText(content)
                tempFile.setExecutable(true)
            }

            Log.d(TAG, "Scripts deployed: ${scripts.joinToString()}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deploy scripts", e)
            false
        }
    }

    /**
     * Execute a command in Termux via the RUN_COMMAND intent
     */
    fun runInTermux(command: String, background: Boolean = false) {
        val intent = Intent(ACTION_RUN_COMMAND).apply {
            setClassName(TERMUX_PKG, TERMUX_SERVICE)
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", TERMUX_HOME)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", background)
        }

        try {
            context.startForegroundService(intent)
        } catch (e: Exception) {
            // Fallback to startService
            try {
                context.startService(intent)
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to run command in Termux", e2)
            }
        }
    }

    /**
     * Execute a shell command via exec and return output
     */
    suspend fun execCommand(command: String): String = withContext(Dispatchers.IO) {
        try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", command))
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            process.waitFor()
            if (error.isNotEmpty()) Log.w(TAG, "Command stderr: $error")
            output.trim()
        } catch (e: Exception) {
            Log.e(TAG, "exec failed: $command", e)
            "ERROR: ${e.message}"
        }
    }

    /**
     * Run the initial setup (install Debian, configure environment)
     */
    fun runSetup() {
        // First, copy setup script to Termux home
        val setupScript = context.assets.open("scripts/setup.sh").bufferedReader().readText()
        // Write via Termux command
        val escapedScript = setupScript.replace("'", "'\\''")
        runInTermux("mkdir -p $PAUXB_DIR/scripts && cat > $PAUXB_DIR/scripts/setup.sh << 'PAUXB_EOF'\n$setupScript\nPAUXB_EOF\nchmod +x $PAUXB_DIR/scripts/setup.sh && bash $PAUXB_DIR/scripts/setup.sh")
    }

    /**
     * Start the bridge daemon
     */
    fun startBridge() {
        runInTermux(
            "proot-distro login debian -- bash /opt/pauxb/bridge.sh &",
            background = true
        )
    }

    /**
     * Send a command to the bridge daemon and get result
     */
    fun bridgeCommand(command: String) {
        runInTermux(
            "proot-distro login debian -- bash /opt/pauxb/bridge-cmd.sh $command",
            background = true
        )
    }

    /**
     * Start a Linux app via the bridge
     */
    fun startApp(appId: String, command: String, width: Int = 1280, height: Int = 720) {
        bridgeCommand("start $command $appId $width $height")
    }

    /**
     * Stop a Linux app
     */
    fun stopApp(appId: String) {
        bridgeCommand("stop $appId")
    }

    /**
     * Install a package in Debian
     */
    fun installPackage(packageName: String) {
        runInTermux(
            "proot-distro login debian -- apt-get install -y $packageName"
        )
    }

    /**
     * Get setup status
     */
    fun checkStatus() {
        runInTermux("cat $PAUXB_DIR/status 2>/dev/null || echo NOT_SETUP", background = true)
    }

    /**
     * Open Termux terminal directly
     */
    fun openTermux() {
        val intent = context.packageManager.getLaunchIntentForPackage(TERMUX_PKG)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    /**
     * Open a root shell in Debian
     */
    fun openDebianShell() {
        runInTermux("proot-distro login debian")
    }
}
