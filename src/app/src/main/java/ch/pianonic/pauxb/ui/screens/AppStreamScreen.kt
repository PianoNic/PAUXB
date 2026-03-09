package ch.pianonic.pauxb.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.pianonic.pauxb.vnc.VncClient
import ch.pianonic.pauxb.vnc.VncView

/**
 * Full-screen display for a streaming Linux app via VNC.
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

    LaunchedEffect(vncPort) {
        vncClient.connect(port = vncPort)
    }

    DisposableEffect(Unit) {
        onDispose {
            vncClient.disconnect()
        }
    }

    val isConnected by vncClient.connected.collectAsState()

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
                }
            )
        },
        modifier = modifier
    ) { padding ->
        VncView(
            vncClient = vncClient,
            modifier = Modifier.padding(padding)
        )
    }
}
