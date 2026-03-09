package ch.pianonic.pauxb

import android.content.Intent
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var bridge: TermuxBridge
    private val terminalSession = TerminalSession()

    // For shortcut-launched apps
    private var launchAppId: String? = null
    private var launchAppName: String? = null
    private var launchAppCommand: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bridge = TermuxBridge(this)
        enableEdgeToEdge()

        handleLaunchIntent(intent)

        setContent {
            PAUXBTheme {
                PAUXBApp(
                    bridge = bridge,
                    terminalSession = terminalSession,
                    launchAppId = launchAppId,
                    launchAppName = launchAppName,
                    launchAppCommand = launchAppCommand
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun handleLaunchIntent(intent: Intent?) {
        if (intent?.action == "ch.pianonic.pauxb.LAUNCH_APP") {
            launchAppId = intent.getStringExtra("app_id")
            launchAppName = intent.getStringExtra("app_name")
            launchAppCommand = intent.getStringExtra("app_command")
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
    terminalSession: TerminalSession,
    launchAppId: String? = null,
    launchAppName: String? = null,
    launchAppCommand: String? = null
) {
    var currentScreen by remember { mutableStateOf(Screen.SETUP) }
    var setupStatus by remember { mutableStateOf("Not started") }
    var isSettingUp by remember { mutableStateOf(false) }
    var apps by remember { mutableStateOf(listOf<LinuxApp>()) }
    var discoveredApps by remember { mutableStateOf(listOf<TermuxBridge.DiscoveredApp>()) }
    var streamingApp by remember { mutableStateOf<LinuxApp?>(null) }
    val scope = rememberCoroutineScope()

    // Check if Termux is installed and scan for apps on launch
    LaunchedEffect(Unit) {
        if (bridge.isTermuxInstalled()) {
            setupStatus = "Termux is installed. Ready to setup."
            // Scan for installed apps
            discoveredApps = bridge.getInstalledApps()
        } else {
            setupStatus = "Termux not found. Please install Termux from GitHub."
        }
    }

    // Handle shortcut launch - start the app and go directly to stream
    LaunchedEffect(launchAppId) {
        if (launchAppId != null && launchAppName != null && launchAppCommand != null) {
            bridge.startApp(launchAppId, launchAppCommand)
            val app = LinuxApp(
                id = launchAppId,
                name = launchAppName,
                command = launchAppCommand,
                packageName = "",
                vncPort = 5910,
                isRunning = true
            )
            // Add to list if not already there
            if (apps.none { it.id == launchAppId }) {
                apps = apps + app
            } else {
                apps = apps.map {
                    if (it.id == launchAppId) it.copy(isRunning = true, vncPort = 5910)
                    else it
                }
            }
            streamingApp = app
            currentScreen = Screen.APP_STREAM
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
                discoveredApps = discoveredApps,
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
                onRefreshApps = {
                    // Trigger scan in Termux, then read results after it completes
                    bridge.scanInstalledApps()
                    scope.launch {
                        delay(3000) // Wait for scan to complete
                        discoveredApps = bridge.getInstalledApps()
                    }
                },
                onAddToHomeScreen = { app ->
                    bridge.createAppShortcut(app.id, app.name, app.command)
                },
                onAddDiscoveredApp = { discovered ->
                    val appId = discovered.id
                    if (apps.none { it.id == appId }) {
                        apps = apps + LinuxApp(
                            id = appId,
                            name = discovered.name,
                            command = discovered.command,
                            packageName = discovered.desktopFile.removeSuffix(".desktop")
                        )
                    }
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
