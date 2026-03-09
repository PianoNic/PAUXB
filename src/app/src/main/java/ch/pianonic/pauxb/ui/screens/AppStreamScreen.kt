package ch.pianonic.pauxb.ui.screens

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import ch.pianonic.pauxb.vnc.VncClient
import ch.pianonic.pauxb.vnc.VncView

/**
 * Full-screen display for a streaming Linux app via VNC.
 * Adapts to window size changes (e.g. Samsung DeX resizing).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppStreamScreen(
    appName: String,
    vncPort: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val vncClient = remember { VncClient() }
    val context = LocalContext.current
    var showTopBar by remember { mutableStateOf(true) }

    // Track configuration changes for responsive resizing
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp
    val screenHeightDp = configuration.screenHeightDp
    val density = LocalDensity.current

    // Keep screen on while streaming
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    LaunchedEffect(vncPort) {
        vncClient.connect(port = vncPort)
    }

    DisposableEffect(Unit) {
        onDispose {
            vncClient.disconnect()
        }
    }

    // When screen size changes, notify the VNC client for potential resize
    LaunchedEffect(screenWidthDp, screenHeightDp) {
        val widthPx = with(density) { screenWidthDp.dp.roundToPx() }
        val heightPx = with(density) { screenHeightDp.dp.roundToPx() }
        vncClient.onViewportResized(widthPx, heightPx)
    }

    val isConnected by vncClient.connected.collectAsState()

    if (showTopBar) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(appName) },
                    navigationIcon = {
                        IconButton(onClick = {
                            vncClient.disconnect()
                            onBack()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        if (isConnected) {
                            Text(
                                text = "Connected",
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                        TextButton(onClick = { showTopBar = false }) {
                            Text("Fullscreen")
                        }
                    }
                )
            },
            modifier = modifier
        ) { padding ->
            VncView(
                vncClient = vncClient,
                modifier = Modifier.padding(padding),
                onToggleFullscreen = { showTopBar = !showTopBar }
            )
        }
    } else {
        // True fullscreen - no top bar
        Box(modifier = Modifier.fillMaxSize()) {
            VncView(
                vncClient = vncClient,
                modifier = Modifier.fillMaxSize(),
                onToggleFullscreen = { showTopBar = !showTopBar }
            )
        }
    }
}
