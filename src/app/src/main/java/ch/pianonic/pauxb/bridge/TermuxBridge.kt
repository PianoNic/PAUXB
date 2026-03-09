package ch.pianonic.pauxb.bridge

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.util.Log
import androidx.core.content.ContextCompat
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
     * Check if the RUN_COMMAND permission is granted.
     * This permission is required to execute commands in Termux.
     */
    fun hasRunCommandPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            "com.termux.permission.RUN_COMMAND"
        ) == PackageManager.PERMISSION_GRANTED
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
     * Check if Termux has allow-external-apps enabled by attempting
     * to resolve the RunCommandService. If the service can't be reached,
     * it likely means allow-external-apps is not set to true.
     */
    fun isTermuxExternalAppsEnabled(): Boolean {
        if (!isTermuxInstalled()) return false
        return try {
            val intent = Intent(ACTION_RUN_COMMAND).apply {
                setClassName(TERMUX_PKG, TERMUX_SERVICE)
            }
            val resolveInfo = context.packageManager.resolveService(intent, 0)
            resolveInfo != null
        } catch (e: Exception) {
            Log.w(TAG, "Could not check Termux external apps status", e)
            false
        }
    }

    /**
     * Execute a command in Termux via the RUN_COMMAND intent.
     * Returns true if the intent was sent successfully, false if it failed
     * (e.g. allow-external-apps not enabled).
     */
    fun runInTermux(command: String, background: Boolean = false): Boolean {
        val intent = Intent(ACTION_RUN_COMMAND).apply {
            setClassName(TERMUX_PKG, TERMUX_SERVICE)
            putExtra("com.termux.RUN_COMMAND_PATH", "/data/data/com.termux/files/usr/bin/bash")
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", command))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", TERMUX_HOME)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", background)
        }

        return try {
            context.startForegroundService(intent)
            true
        } catch (e: Exception) {
            try {
                context.startService(intent)
                true
            } catch (e2: Exception) {
                Log.e(TAG, "Failed to run command in Termux. " +
                    "Ensure allow-external-apps is enabled in Termux.", e2)
                false
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
     * Resize a running app's virtual display to match the new viewport size.
     * Uses xrandr to change the Xvfb resolution dynamically.
     */
    fun resizeApp(appId: String, width: Int, height: Int) {
        bridgeCommand("resize $appId $width $height")
    }

    /**
     * Install a package in Debian with status tracking.
     * Writes progress to /sdcard/.pauxb/install_<pkg>.status
     */
    fun installPackage(packageName: String) {
        val statusFile = "/sdcard/.pauxb/install_${packageName}.status"
        runInTermux(
            "mkdir -p /sdcard/.pauxb && " +
            "echo 'INSTALLING' > $statusFile && " +
            "if proot-distro login debian -- apt-get install -y $packageName >> /sdcard/.pauxb/install_${packageName}.log 2>&1; then " +
            "echo 'INSTALL_COMPLETE' > $statusFile; " +
            "else echo 'INSTALL_FAILED' > $statusFile; fi",
            background = true
        )
    }

    /**
     * Poll the install status for a package.
     */
    suspend fun getInstallStatus(packageName: String): String = withContext(Dispatchers.IO) {
        try {
            val file = File("/sdcard/.pauxb/install_${packageName}.status")
            if (file.exists()) file.readText().trim() else "UNKNOWN"
        } catch (e: Exception) {
            "UNKNOWN"
        }
    }

    /**
     * Get setup status by reading the status file.
     * The setup script writes progress to $PAUXB_DIR/status.
     */
    fun checkStatus() {
        runInTermux("cat $PAUXB_DIR/status 2>/dev/null || echo NOT_SETUP", background = true)
    }

    /**
     * Poll the setup status file and return the current phase.
     * Writes status to /sdcard/pauxb_status.txt for cross-app access.
     */
    fun pollSetupStatus() {
        runInTermux(
            "cat $PAUXB_DIR/status > /sdcard/pauxb_status.txt 2>/dev/null",
            background = true
        )
    }

    /**
     * Read the polled setup status.
     */
    suspend fun getSetupStatus(): String = withContext(Dispatchers.IO) {
        try {
            val file = File("/sdcard/pauxb_status.txt")
            if (file.exists()) file.readText().trim() else "NOT_SETUP"
        } catch (e: Exception) {
            "NOT_SETUP"
        }
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

    /**
     * Scan Debian for installed GUI applications by reading .desktop files.
     * Runs the scan inside Termux/Debian and writes results to a shared file.
     */
    fun scanInstalledApps() {
        val outputFile = "$PAUXB_DIR/installed_apps.txt"
        val scanCmd = """
            DIRS="/usr/share/applications /usr/local/share/applications"
            > /tmp/pauxb_apps.txt
            for dir in ${'$'}DIRS; do
                [ -d "${'$'}dir" ] || continue
                for desktop in "${'$'}dir"/*.desktop; do
                    [ -f "${'$'}desktop" ] || continue
                    name=""; exec_cmd=""; icon=""; no_display=""; comment=""
                    while IFS='=' read -r key value; do
                        case "${'$'}key" in
                            Name) [ -z "${'$'}name" ] && name="${'$'}value" ;;
                            Exec) [ -z "${'$'}exec_cmd" ] && exec_cmd="${'$'}value" ;;
                            Icon) [ -z "${'$'}icon" ] && icon="${'$'}value" ;;
                            Comment) [ -z "${'$'}comment" ] && comment="${'$'}value" ;;
                            NoDisplay) no_display="${'$'}value" ;;
                        esac
                    done < "${'$'}desktop"
                    [ "${'$'}no_display" = "true" ] && continue
                    exec_cmd=${'$'}(echo "${'$'}exec_cmd" | sed 's/ %[fFuUdDnNickvm]//g')
                    [ -z "${'$'}name" ] || [ -z "${'$'}exec_cmd" ] && continue
                    filename=${'$'}(basename "${'$'}desktop")
                    echo "${'$'}{name}|${'$'}{exec_cmd}|${'$'}{icon}|${'$'}{comment}|${'$'}{filename}" >> /tmp/pauxb_apps.txt
                done
            done
            cat /tmp/pauxb_apps.txt
        """.trimIndent()

        val outputPath = context.filesDir.absolutePath + "/installed_apps.txt"
        // Write to multiple locations for accessibility
        runInTermux(
            "RESULT=\$(proot-distro login debian -- bash -c '$scanCmd' 2>/dev/null); " +
            "echo \"\$RESULT\" > /sdcard/pauxb_apps.txt 2>/dev/null; " +
            "echo \"\$RESULT\" > /data/local/tmp/pauxb_apps.txt 2>/dev/null; " +
            "echo \"\$RESULT\" > $PAUXB_DIR/installed_apps.txt 2>/dev/null",
            background = true
        )
    }

    /**
     * Read the cached list of installed GUI apps.
     * Tries multiple locations where the scan output may be written.
     */
    suspend fun getInstalledApps(): List<DiscoveredApp> = withContext(Dispatchers.IO) {
        val paths = listOf(
            File(context.filesDir, "installed_apps.txt"),
            File("/sdcard/pauxb_apps.txt"),
            File("/data/local/tmp/pauxb_apps.txt")
        )

        for (cacheFile in paths) {
            try {
                if (!cacheFile.exists()) continue
                val output = cacheFile.readText()
                if (output.isBlank()) continue
                Log.d(TAG, "Read ${output.lines().size} lines from ${cacheFile.absolutePath}")
                val apps = parseAppList(output)
                if (apps.isNotEmpty()) return@withContext apps
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read from ${cacheFile.absolutePath}: ${e.message}")
            }
        }

        Log.d(TAG, "No cached app list found in any location")
        emptyList()
    }

    private fun parseAppList(output: String): List<DiscoveredApp> {
        return output.lines()
            .filter { it.contains("|") }
            .mapNotNull { line ->
                val parts = line.split("|", limit = 5)
                if (parts.size < 2) return@mapNotNull null
                val name = parts[0].trim()
                val exec = parts[1].trim()
                if (name.isBlank() || exec.isBlank()) return@mapNotNull null

                val icon = parts.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }
                val comment = parts.getOrNull(3)?.trim()?.takeIf { it.isNotBlank() }
                val desktopFile = parts.getOrNull(4)?.trim() ?: "$name.desktop"
                val appId = desktopFile.removeSuffix(".desktop").lowercase().replace(Regex("[^a-z0-9]"), "_")

                DiscoveredApp(
                    id = appId,
                    name = name,
                    command = exec,
                    iconName = icon,
                    description = comment,
                    categories = null,
                    desktopFile = desktopFile
                )
            }
            .sortedBy { it.name }
    }

    /**
     * Create a home screen shortcut for a Linux app.
     * The shortcut launches PAUXB directly into the app's VNC stream.
     */
    fun createAppShortcut(appId: String, appName: String, command: String) {
        val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return

        if (!shortcutManager.isRequestPinShortcutSupported) {
            Log.w(TAG, "Pinned shortcuts not supported")
            return
        }

        val intent = Intent(context, Class.forName("ch.pianonic.pauxb.AppStreamActivity")).apply {
            action = "ch.pianonic.pauxb.LAUNCH_APP"
            putExtra("app_id", appId)
            putExtra("app_name", appName)
            putExtra("app_command", command)
        }

        val shortcut = ShortcutInfo.Builder(context, "pauxb_$appId")
            .setShortLabel(appName)
            .setLongLabel("$appName (Linux)")
            .setIcon(Icon.createWithResource(context, android.R.drawable.sym_def_app_icon))
            .setIntent(intent)
            .build()

        shortcutManager.requestPinShortcut(shortcut, null)
    }

    data class DiscoveredApp(
        val id: String,
        val name: String,
        val command: String,
        val iconName: String?,
        val description: String?,
        val categories: String?,
        val desktopFile: String
    )
}
