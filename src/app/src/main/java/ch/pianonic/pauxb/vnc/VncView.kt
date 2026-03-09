package ch.pianonic.pauxb.vnc

import android.view.MotionEvent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

/**
 * Composable that displays a VNC stream and handles touch input.
 * Responsively scales the remote display to fill available space.
 * Double-tap to toggle fullscreen.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun VncView(
    vncClient: VncClient,
    modifier: Modifier = Modifier,
    onToggleFullscreen: (() -> Unit)? = null
) {
    val frame by vncClient.frame.collectAsState()
    val isConnected by vncClient.connected.collectAsState()
    var viewSize by remember { mutableStateOf(IntSize.Zero) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { viewSize = it }
            .pointerInteropFilter { event ->
                if (!isConnected || frame == null) return@pointerInteropFilter false

                val fbW = vncClient.getFrameWidth()
                val fbH = vncClient.getFrameHeight()
                if (fbW == 0 || fbH == 0 || viewSize.width == 0 || viewSize.height == 0) {
                    return@pointerInteropFilter false
                }

                // Calculate scaling to map touch coords to VNC coords
                val scaleX = fbW.toFloat() / viewSize.width
                val scaleY = fbH.toFloat() / viewSize.height
                val scale = maxOf(scaleX, scaleY)

                val displayW = fbW / scale
                val displayH = fbH / scale
                val offsetX = (viewSize.width - displayW) / 2
                val offsetY = (viewSize.height - displayH) / 2

                val vncX = ((event.x - offsetX) * scale).toInt().coerceIn(0, fbW - 1)
                val vncY = ((event.y - offsetY) * scale).toInt().coerceIn(0, fbH - 1)

                val buttonMask = when (event.action) {
                    MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> 1
                    MotionEvent.ACTION_UP -> 0
                    else -> 0
                }

                vncClient.sendPointerEvent(vncX, vncY, buttonMask)
                true
            }
            .let { mod ->
                if (onToggleFullscreen != null) {
                    mod.pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { onToggleFullscreen() }
                        )
                    }
                } else mod
            },
        contentAlignment = Alignment.Center
    ) {
        if (!isConnected) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Text(
                text = "Connecting...",
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        } else if (frame != null) {
            Image(
                bitmap = frame!!.asImageBitmap(),
                contentDescription = "Linux App Display",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
