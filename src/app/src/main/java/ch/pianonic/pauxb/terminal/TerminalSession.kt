package ch.pianonic.pauxb.terminal

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.*

/**
 * Manages a local shell session for the embedded terminal.
 * Runs commands through Termux's shell environment.
 */
class TerminalSession {

    companion object {
        private const val TAG = "TerminalSession"
        private const val MAX_BUFFER_LINES = 1000
    }

    private var process: Process? = null
    private var writer: BufferedWriter? = null
    private var readerJob: Job? = null

    private val _output = MutableStateFlow("")
    val output: StateFlow<String> = _output

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning

    private val outputBuffer = StringBuilder()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Start a shell session.
     * Attempts to use Termux's shell, falls back to sh.
     */
    fun start(useDebian: Boolean = false) {
        if (_isRunning.value) return

        scope.launch {
            try {
                val cmd = if (useDebian) {
                    arrayOf(
                        "/data/data/com.termux/files/usr/bin/bash",
                        "-c",
                        "proot-distro login debian"
                    )
                } else {
                    arrayOf("/data/data/com.termux/files/usr/bin/bash", "-l")
                }

                val env = arrayOf(
                    "HOME=/data/data/com.termux/files/home",
                    "PREFIX=/data/data/com.termux/files/usr",
                    "TERM=xterm-256color",
                    "PATH=/data/data/com.termux/files/usr/bin:/system/bin",
                    "LD_LIBRARY_PATH=/data/data/com.termux/files/usr/lib",
                    "LANG=en_US.UTF-8"
                )

                val pb = ProcessBuilder(*cmd)
                pb.environment().clear()
                for (e in env) {
                    val parts = e.split("=", limit = 2)
                    pb.environment()[parts[0]] = parts[1]
                }
                pb.redirectErrorStream(true)

                process = pb.start()
                writer = BufferedWriter(OutputStreamWriter(process!!.outputStream))
                _isRunning.value = true

                // Read output
                val reader = BufferedReader(InputStreamReader(process!!.inputStream))
                readerJob = scope.launch {
                    try {
                        val buf = CharArray(4096)
                        while (isActive) {
                            val n = reader.read(buf)
                            if (n == -1) break
                            val text = String(buf, 0, n)
                            synchronized(outputBuffer) {
                                outputBuffer.append(text)
                                // Trim buffer if too large
                                if (outputBuffer.length > 50000) {
                                    outputBuffer.delete(0, outputBuffer.length - 40000)
                                }
                                _output.value = outputBuffer.toString()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Reader error", e)
                    } finally {
                        _isRunning.value = false
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start session", e)
                appendOutput("Error: Could not start shell. Is Termux installed?\n")
                _isRunning.value = false
            }
        }
    }

    fun sendCommand(command: String) {
        scope.launch {
            try {
                writer?.write(command + "\n")
                writer?.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send command", e)
            }
        }
    }

    fun sendKey(char: Char) {
        scope.launch {
            try {
                writer?.write(char.code)
                writer?.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send key", e)
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
        readerJob?.cancel()
        try {
            writer?.close()
            process?.destroy()
        } catch (_: Exception) {}
        process = null
        writer = null
        _isRunning.value = false
    }

    fun clear() {
        synchronized(outputBuffer) {
            outputBuffer.clear()
            _output.value = ""
        }
    }
}
