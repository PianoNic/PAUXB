package ch.pianonic.pauxb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import ch.pianonic.pauxb.bridge.TermuxBridge
import ch.pianonic.pauxb.terminal.TerminalSession
import ch.pianonic.pauxb.ui.screens.*
import ch.pianonic.pauxb.ui.theme.PAUXBTheme

class MainActivity : ComponentActivity() {
    private lateinit var bridge: TermuxBridge
    private val terminalSession = TerminalSession()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bridge = TermuxBridge(this)
        enableEdgeToEdge()

        setContent {
            PAUXBTheme {
                PAUXBApp(bridge = bridge, terminalSession = terminalSession)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        terminalSession.stop()
    }
}

enum class Screen {
    SETUP, APPS, TERMINAL, APP_STREAM
}

@Composable
fun PAUXBApp(
    bridge: TermuxBridge,
    terminalSession: TerminalSession
) {
    var currentScreen by remember { mutableStateOf(Screen.SETUP) }
    var setupStatus by remember { mutableStateOf("Not started") }
    var isSettingUp by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf(listOf(
        LinuxApp(
            id = "xterm",
            name = "XTerm",
            command = "xterm",
            packageName = "xterm",
            vncPort = 5910,
            isRunning = true // already running from our manual test
        )
    )) }
    var streamingApp by remember { mutableStateOf<LinuxApp?>(null) }

    // Check if Termux is installed on launch
    LaunchedEffect(Unit) {
        if (bridge.isTermuxInstalled()) {
            setupStatus = "Termux is installed. Ready to setup."
        } else {
            setupStatus = "Termux not found. Please install Termux from GitHub."
        }
    }

    if (currentScreen == Screen.APP_STREAM && streamingApp != null) {
        AppStreamScreen(
            appName = streamingApp!!.name,
            vncPort = streamingApp!!.vncPort ?: 5910,
            onBack = {
                streamingApp = null
                currentScreen = Screen.APPS
            }
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Build, contentDescription = "Setup") },
                    label = { Text("Setup") },
                    selected = currentScreen == Screen.SETUP,
                    onClick = { currentScreen = Screen.SETUP }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = "Apps") },
                    label = { Text("Apps") },
                    selected = currentScreen == Screen.APPS,
                    onClick = { currentScreen = Screen.APPS }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.PlayArrow, contentDescription = "Terminal") },
                    label = { Text("Terminal") },
                    selected = currentScreen == Screen.TERMINAL,
                    onClick = { currentScreen = Screen.TERMINAL }
                )
            }
        }
    ) { innerPadding ->
        when (currentScreen) {
            Screen.SETUP -> SetupScreen(
                onRunSetup = {
                    isSettingUp = true
                    setupStatus = "Starting setup..."
                    bridge.runSetup()
                },
                onOpenTermux = { bridge.openTermux() },
                setupStatus = setupStatus,
                isSettingUp = isSettingUp,
                modifier = Modifier.padding(innerPadding)
            )

            Screen.APPS -> AppsScreen(
                apps = apps,
                onStartApp = { app ->
                    bridge.startApp(app.id, app.command)
                    apps = apps.map {
                        if (it.id == app.id) it.copy(isRunning = true, vncPort = 5910)
                        else it
                    }
                },
                onStopApp = { app ->
                    bridge.stopApp(app.id)
                    apps = apps.map {
                        if (it.id == app.id) it.copy(isRunning = false, vncPort = null)
                        else it
                    }
                },
                onOpenApp = { app ->
                    streamingApp = app
                    currentScreen = Screen.APP_STREAM
                },
                onInstallApp = { name, pkg, cmd ->
                    bridge.installPackage(pkg)
                    val appId = name.lowercase().replace(" ", "_")
                    apps = apps + LinuxApp(
                        id = appId,
                        name = name,
                        command = cmd,
                        packageName = pkg
                    )
                },
                modifier = Modifier.padding(innerPadding)
            )

            Screen.TERMINAL -> TerminalScreen(
                session = terminalSession,
                modifier = Modifier.padding(innerPadding)
            )

            Screen.APP_STREAM -> {} // Handled above
        }
    }
}
