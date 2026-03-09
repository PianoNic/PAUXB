package ch.pianonic.pauxb.terminal

import android.util.Log
import ch.pianonic.pauxb.bridge.TermuxBridge
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.*

/**
 * Manages an interactive shell session by communicating with a persistent
 * shell process running inside Termux via shared storage files.
 *
 * Architecture:
 * - A shell daemon runs in Termux, reading commands from /sdcard/.pauxb/term_cmd
 * - Output is appended to /sdcard/.pauxb/term_out
 * - This class polls term_out for new content and sends commands via term_cmd
 */
class TerminalSession {

    companion object {
        private const val TAG = "TerminalSession"
        private const val SHARED_DIR = "/sdcard/.pauxb"
        private const val CMD_FILE = "$SHARED_DIR/term_cmd"
        private const val OUT_FILE = "$SHARED_DIR/term_out"
        private const val PID_FILE = "$SHARED_DIR/term_pid"
        private const val POLL_INTERVAL_MS = 300L
    }

    private var bridge: TermuxBridge? = null
    private var pollJob: Job? = null

    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val outputBuffer = StringBuilder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var lastReadPosition = 0L

    /**
     * Start a shell session via Termux's RUN_COMMAND intent.
     * Launches a persistent shell daemon that communicates through shared files.
     */
    fun start(bridge: TermuxBridge, useDebian: Boolean = false) {
        if (_isRunning.value) return
        this.bridge = bridge

        scope.launch {
            try {
                // Ensure shared directory exists
                val dir = File(SHARED_DIR)
                if (!dir.exists()) dir.mkdirs()

                // Clear previous session files
                File(OUT_FILE).apply { if (exists()) delete() }
                File(CMD_FILE).apply { if (exists()) delete() }
                File(PID_FILE).apply { if (exists()) delete() }

                lastReadPosition = 0L
                synchronized(outputBuffer) {
                    outputBuffer.clear()
                }

                // Shell daemon script that runs inside Termux.
                // It watches for commands written to term_cmd and executes them,
                // redirecting all output to term_out.
                val shellPrefix = if (useDebian) {
                    "proot-distro login debian -- "
                } else {
                    ""
                }

                val envLabel = if (useDebian) "Debian (proot)" else "Termux"
                val d = "$"  // Literal dollar sign for shell variables

                val daemonScript = """
                    mkdir -p $SHARED_DIR
                    > $OUT_FILE
                    > $CMD_FILE
                    echo ${d}${d} > $PID_FILE

                    export TERM=xterm-256color
                    export PS1='${d} '

                    echo "PAUXB Terminal - ${d}(date)" >> $OUT_FILE
                    echo "Environment: $envLabel" >> $OUT_FILE
                    echo "---" >> $OUT_FILE
                    echo "" >> $OUT_FILE

                    while true; do
                        if [ -s $CMD_FILE ]; then
                            CMD=${d}(cat $CMD_FILE)
                            > $CMD_FILE
                            if [ "${d}CMD" = "__EXIT__" ]; then
                                echo "[Session ended]" >> $OUT_FILE
                                break
                            fi
                            echo "${d} ${d}CMD" >> $OUT_FILE
                            ${shellPrefix}bash -c "${d}CMD" >> $OUT_FILE 2>&1
                            echo "" >> $OUT_FILE
                        fi
                        sleep 0.2
                    done
                """.trimIndent()

                val sent = bridge.runInTermux(daemonScript, background = true)

                if (!sent) {
                    appendOutput("Error: Could not start shell. Is Termux installed and configured?\n")
                    return@launch
                }

                _isRunning.value = true
                appendOutput("Connecting to Termux shell...\n")

                // Poll the output file for new content
                pollJob = scope.launch {
                    // Give the daemon a moment to start
                    delay(1500)

                    val outFile = File(OUT_FILE)
                    while (isActive && _isRunning.value) {
                        try {
                            if (outFile.exists() && outFile.length() > lastReadPosition) {
                                val raf = RandomAccessFile(outFile, "r")
                                raf.seek(lastReadPosition)
                                val newBytes = ByteArray((raf.length() - lastReadPosition).toInt())
                                raf.readFully(newBytes)
                                lastReadPosition = raf.length()
                                raf.close()

                                val newText = String(newBytes)
                                if (newText.isNotEmpty()) {
                                    synchronized(outputBuffer) {
                                        outputBuffer.append(newText)
                                        if (outputBuffer.length > 50000) {
                                            outputBuffer.delete(0, outputBuffer.length - 40000)
                                        }
                                        _output.value = outputBuffer.toString()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Poll error: ${e.message}")
                        }
                        delay(POLL_INTERVAL_MS)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start session", e)
                appendOutput("Error: Could not start shell. Is Termux installed?\n")
                _isRunning.value = false
            }
        }
    }

    /**
     * Legacy start method for backward compatibility.
     * Will show an error directing the user to ensure Termux is set up.
     */
    fun start(useDebian: Boolean = false) {
        appendOutput("Error: Terminal requires Termux bridge. Please ensure setup is complete.\n")
    }

    fun sendCommand(command: String) {
        scope.launch {
            try {
                val cmdFile = File(CMD_FILE)
                cmdFile.writeText(command)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send command", e)
                appendOutput("Error: Failed to send command\n")
            }
        }
    }

    fun sendKey(char: Char) {
        // Map control characters to shell commands
        when (char) {
            '\u0003' -> { // Ctrl+C
                // Kill the currently running command by sending interrupt signal
                val d = "$"
                bridge?.runInTermux(
                    "PID=${d}(cat $PID_FILE 2>/dev/null); [ -n \"${d}PID\" ] && pkill -INT -P ${d}PID 2>/dev/null; echo '^C' >> $OUT_FILE",
                    background = true
                )
            }
            '\u001B' -> { // ESC - no-op for now
            }
            else -> {
                sendCommand(char.toString())
            }
        }
    }

    private fun appendOutput(text: String) {
        synchronized(outputBuffer) {
            outputBuffer.append(text)
            _output.value = outputBuffer.toString()
        }
    }

    fun stop() {
        pollJob?.cancel()
        // Signal the daemon to exit
        scope.launch {
            try {
                File(CMD_FILE).writeText("__EXIT__")
            } catch (_: Exception) {}
        }
        _isRunning.value = false
    }

    fun clear() {
        synchronized(outputBuffer) {
            outputBuffer.clear()
            _output.value = ""
        }
        lastReadPosition = 0L
        // Also clear the output file
        scope.launch {
            try {
                File(OUT_FILE).writeText("")
            } catch (_: Exception) {}
        }
    }
}
