package ch.pianonic.pauxb.vnc

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Socket

/**
 * Minimal VNC (RFB) client that connects to a local VNC server
 * and produces Bitmap frames for display in the Android UI.
 */
class VncClient {

    companion object {
        private const val TAG = "VncClient"
        private const val RFB_VERSION = "RFB 003.008\n"
    }

    private var socket: Socket? = null
    private var input: DataInputStream? = null
    private var output: DataOutputStream? = null

    private var fbWidth = 0
    private var fbHeight = 0
    private var framebuffer: IntArray = IntArray(0)

    private val _frame = MutableStateFlow<Bitmap?>(null)
    val frame: StateFlow<Bitmap?> = _frame

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun connect(host: String = "127.0.0.1", port: Int) {
        job?.cancel()
        job = scope.launch {
            try {
                doConnect(host, port)
            } catch (e: Exception) {
                Log.e(TAG, "VNC connection error", e)
                _connected.value = false
            }
        }
    }

    private suspend fun doConnect(host: String, port: Int) {
        socket = Socket(host, port)
        input = DataInputStream(socket!!.getInputStream())
        output = DataOutputStream(socket!!.getOutputStream())

        val din = input!!
        val dout = output!!

        // Read server version
        val versionBuf = ByteArray(12)
        din.readFully(versionBuf)
        val serverVersion = String(versionBuf)
        Log.d(TAG, "Server version: $serverVersion")

        // Send client version
        dout.write(RFB_VERSION.toByteArray())
        dout.flush()

        // Security handshake
        val numSecTypes = din.readUnsignedByte()
        if (numSecTypes == 0) {
            val reasonLen = din.readInt()
            val reason = ByteArray(reasonLen)
            din.readFully(reason)
            throw IOException("Connection refused: ${String(reason)}")
        }

        val secTypes = ByteArray(numSecTypes)
        din.readFully(secTypes)

        // Select no auth (type 1)
        dout.writeByte(1) // None
        dout.flush()

        // Security result
        val secResult = din.readInt()
        if (secResult != 0) {
            throw IOException("Security handshake failed: $secResult")
        }

        // ClientInit - shared flag
        dout.writeByte(1) // shared
        dout.flush()

        // ServerInit
        fbWidth = din.readUnsignedShort()
        fbHeight = din.readUnsignedShort()
        framebuffer = IntArray(fbWidth * fbHeight) { Color.BLACK }

        // Pixel format (16 bytes)
        val pixelFormat = ByteArray(16)
        din.readFully(pixelFormat)

        // Name
        val nameLen = din.readInt()
        val nameBuf = ByteArray(nameLen)
        din.readFully(nameBuf)
        Log.d(TAG, "Connected to: ${String(nameBuf)} (${fbWidth}x${fbHeight})")

        // Set pixel format to 32-bit RGBA
        dout.writeByte(0) // SetPixelFormat
        dout.writeByte(0) // padding
        dout.writeByte(0)
        dout.writeByte(0)
        // pixel format: 32bpp, 24depth, big-endian=0, true-color=1
        dout.writeByte(32)  // bits-per-pixel
        dout.writeByte(24)  // depth
        dout.writeByte(0)   // big-endian
        dout.writeByte(1)   // true-color
        dout.writeShort(255) // red-max
        dout.writeShort(255) // green-max
        dout.writeShort(255) // blue-max
        dout.writeByte(16)  // red-shift
        dout.writeByte(8)   // green-shift
        dout.writeByte(0)   // blue-shift
        dout.writeByte(0)   // padding
        dout.writeByte(0)
        dout.writeByte(0)
        dout.flush()

        // Set encodings (Raw + CopyRect)
        dout.writeByte(2) // SetEncodings
        dout.writeByte(0) // padding
        dout.writeShort(2) // number of encodings
        dout.writeInt(1)   // CopyRect
        dout.writeInt(0)   // Raw
        dout.flush()

        _connected.value = true

        // Main loop - request and process frames
        while (coroutineContext.isActive) {
            requestFramebufferUpdate(dout)
            processServerMessage(din)
            emitFrame()
            delay(16) // ~60fps cap
        }
    }

    private fun requestFramebufferUpdate(dout: DataOutputStream) {
        try {
            dout.writeByte(3) // FramebufferUpdateRequest
            dout.writeByte(1) // incremental
            dout.writeShort(0) // x
            dout.writeShort(0) // y
            dout.writeShort(fbWidth) // width
            dout.writeShort(fbHeight) // height
            dout.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request update", e)
        }
    }

    private fun processServerMessage(din: DataInputStream) {
        try {
            val msgType = din.readUnsignedByte()
            when (msgType) {
                0 -> processFramebufferUpdate(din) // FramebufferUpdate
                1 -> processColorMap(din)
                2 -> { /* Bell - ignore */ }
                3 -> processServerCutText(din)
                else -> Log.w(TAG, "Unknown server message: $msgType")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing message", e)
            throw e
        }
    }

    private fun processFramebufferUpdate(din: DataInputStream) {
        din.readByte() // padding
        val numRects = din.readUnsignedShort()

        for (i in 0 until numRects) {
            val x = din.readUnsignedShort()
            val y = din.readUnsignedShort()
            val w = din.readUnsignedShort()
            val h = din.readUnsignedShort()
            val encoding = din.readInt()

            when (encoding) {
                0 -> processRawRect(din, x, y, w, h)     // Raw
                1 -> processCopyRect(din, x, y, w, h)    // CopyRect
                else -> {
                    Log.w(TAG, "Unsupported encoding: $encoding")
                    return
                }
            }
        }
    }

    private fun processRawRect(din: DataInputStream, x: Int, y: Int, w: Int, h: Int) {
        val pixelData = ByteArray(w * h * 4)
        din.readFully(pixelData)

        for (row in 0 until h) {
            for (col in 0 until w) {
                val srcIdx = (row * w + col) * 4
                val dstIdx = (y + row) * fbWidth + (x + col)
                if (dstIdx < framebuffer.size) {
                    val r = pixelData[srcIdx + 2].toInt() and 0xFF
                    val g = pixelData[srcIdx + 1].toInt() and 0xFF
                    val b = pixelData[srcIdx].toInt() and 0xFF
                    framebuffer[dstIdx] = Color.rgb(r, g, b)
                }
            }
        }
    }

    private fun processCopyRect(din: DataInputStream, x: Int, y: Int, w: Int, h: Int) {
        val srcX = din.readUnsignedShort()
        val srcY = din.readUnsignedShort()

        val temp = IntArray(w * h)
        for (row in 0 until h) {
            for (col in 0 until w) {
                val srcIdx = (srcY + row) * fbWidth + (srcX + col)
                if (srcIdx < framebuffer.size) {
                    temp[row * w + col] = framebuffer[srcIdx]
                }
            }
        }
        for (row in 0 until h) {
            for (col in 0 until w) {
                val dstIdx = (y + row) * fbWidth + (x + col)
                if (dstIdx < framebuffer.size) {
                    framebuffer[dstIdx] = temp[row * w + col]
                }
            }
        }
    }

    private fun processColorMap(din: DataInputStream) {
        din.readByte() // padding
        din.readUnsignedShort() // first color
        val numColors = din.readUnsignedShort()
        // Skip color data (6 bytes per color)
        for (i in 0 until numColors) {
            din.readUnsignedShort()
            din.readUnsignedShort()
            din.readUnsignedShort()
        }
    }

    private fun processServerCutText(din: DataInputStream) {
        din.readByte() // padding
        din.readByte()
        din.readByte()
        val len = din.readInt()
        val text = ByteArray(len)
        din.readFully(text)
    }

    private fun emitFrame() {
        if (fbWidth > 0 && fbHeight > 0) {
            val bitmap = Bitmap.createBitmap(fbWidth, fbHeight, Bitmap.Config.ARGB_8888)
            bitmap.setPixels(framebuffer, 0, fbWidth, 0, 0, fbWidth, fbHeight)
            _frame.value = bitmap
        }
    }

    /**
     * Send pointer (touch/mouse) event
     */
    fun sendPointerEvent(x: Int, y: Int, buttonMask: Int) {
        scope.launch {
            try {
                val dout = output ?: return@launch
                synchronized(dout) {
                    dout.writeByte(5) // PointerEvent
                    dout.writeByte(buttonMask)
                    dout.writeShort(x)
                    dout.writeShort(y)
                    dout.flush()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send pointer event", e)
            }
        }
    }

    /**
     * Send key event
     */
    fun sendKeyEvent(key: Int, down: Boolean) {
        scope.launch {
            try {
                val dout = output ?: return@launch
                synchronized(dout) {
                    dout.writeByte(4) // KeyEvent
                    dout.writeByte(if (down) 1 else 0)
                    dout.writeShort(0) // padding
                    dout.writeInt(key)
                    dout.flush()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send key event", e)
            }
        }
    }

    fun disconnect() {
        job?.cancel()
        try {
            socket?.close()
        } catch (_: Exception) {}
        socket = null
        input = null
        output = null
        _connected.value = false
    }

    fun getFrameWidth() = fbWidth
    fun getFrameHeight() = fbHeight

    /**
     * Called when the Android viewport size changes (e.g. DeX window resize).
     * Stores the new viewport dimensions for potential future use
     * (e.g. requesting the VNC server to resize via xrandr).
     */
    fun onViewportResized(widthPx: Int, heightPx: Int) {
        Log.d(TAG, "Viewport resized to ${widthPx}x${heightPx}")
        // Future: could send a DesktopSize pseudo-encoding request
        // or trigger an xrandr resize via the bridge daemon
    }
}
