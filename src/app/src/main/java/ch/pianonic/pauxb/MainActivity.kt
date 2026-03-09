package ch.pianonic.pauxb

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings as SystemSettings
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import ch.pianonic.pauxb.bridge.TermuxBridge
import ch.pianonic.pauxb.data.AppStorage
import ch.pianonic.pauxb.data.SettingsStorage
import ch.pianonic.pauxb.terminal.TerminalSession
import ch.pianonic.pauxb.ui.screens.*
import ch.pianonic.pauxb.ui.theme.PAUXBTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var bridge: TermuxBridge
    private lateinit var appStorage: AppStorage
    private lateinit var settingsStorage: SettingsStorage
    private val terminalSession = TerminalSession()

    private var launchAppId: String? = null
    private var launchAppName: String? = null
    private var launchAppCommand: String? = null

    private var hasRunCommandPermission = mutableStateOf(false)

    private val runCommandPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasRunCommandPermission.value = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bridge = TermuxBridge(this)
        appStorage = AppStorage(this)
        settingsStorage = SettingsStorage(this)
        enableEdgeToEdge()

        handleLaunchIntent(intent)
        requestStoragePermission()

        hasRunCommandPermission.value = bridge.hasRunCommandPermission()

        setContent {
            val themeMode by settingsStorage.themeMode.collectAsState()
            val dynamicColor by settingsStorage.dynamicColor.collectAsState()
            val permissionGranted by hasRunCommandPermission

            PAUXBTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor
            ) {
                PAUXBApp(
                    bridge = bridge,
                    appStorage = appStorage,
                    settingsStorage = settingsStorage,
                    terminalSession = terminalSession,
                    launchAppId = launchAppId,
                    launchAppName = launchAppName,
                    launchAppCommand = launchAppCommand,
                    hasRunCommandPermission = permissionGranted,
                    onRequestRunCommandPermission = {
                        runCommandPermissionLauncher.launch("com.termux.permission.RUN_COMMAND")
                    }
                )
            }
        }
    }

    private fun requestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                val intent = Intent(SystemSettings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
                    .launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hasRunCommandPermission.value = bridge.hasRunCommandPermission()
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
    SETUP, APPS, TERMINAL, SETTINGS, APP_STREAM
}

@Composable
fun PAUXBApp(
    bridge: TermuxBridge,
    appStorage: AppStorage,
    settingsStorage: SettingsStorage,
    terminalSession: TerminalSession,
    launchAppId: String? = null,
    launchAppName: String? = null,
    launchAppCommand: String? = null,
    hasRunCommandPermission: Boolean = true,
    onRequestRunCommandPermission: () -> Unit = {}
) {
    var currentScreen by remember { mutableStateOf(Screen.SETUP) }
    var setupStatus by remember { mutableStateOf("Not started") }
    var isSettingUp by remember { mutableStateOf(false) }
    var isTermuxReady by remember { mutableStateOf(true) }
    var apps by remember { mutableStateOf(appStorage.loadApps()) }
    var discoveredApps by remember { mutableStateOf(listOf<TermuxBridge.DiscoveredApp>()) }
    var streamingApp by remember { mutableStateOf<LinuxApp?>(null) }
    val scope = rememberCoroutineScope()

    // Save apps whenever the list changes
    LaunchedEffect(apps) {
        appStorage.saveApps(apps)
    }

    // Check if Termux is installed and scan for apps on launch
    LaunchedEffect(Unit) {
        if (bridge.isTermuxInstalled()) {
            isTermuxReady = bridge.isTermuxExternalAppsEnabled()
            // Check if setup was already completed
            bridge.pollSetupStatus()
            delay(1500)
            val existingStatus = bridge.getSetupStatus()
            if (existingStatus.contains("SETUP_COMPLETE") || existingStatus.contains("PHASE:COMPLETE")) {
                setupStatus = existingStatus
                isSettingUp = false
                currentScreen = Screen.APPS
            } else if (isTermuxReady) {
                setupStatus = "Termux is installed. Ready to setup."
            } else {
                setupStatus = "Termux needs configuration. See instructions below."
            }
            discoveredApps = bridge.getInstalledApps()
        } else {
            setupStatus = "Termux not found. Please install Termux from GitHub."
            isTermuxReady = false
        }
    }

    // Re-check Termux readiness when permission changes
    LaunchedEffect(hasRunCommandPermission) {
        if (hasRunCommandPermission && bridge.isTermuxInstalled()) {
            isTermuxReady = bridge.isTermuxExternalAppsEnabled()
            if (isTermuxReady) {
                setupStatus = "Termux is installed. Ready to setup."
            }
        }
    }

    // Handle shortcut launch
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
            appId = streamingApp!!.id,
            onBack = {
                streamingApp = null
                currentScreen = Screen.APPS
            },
            onResizeRequest = { appId, width, height ->
                bridge.resizeApp(appId, width, height)
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
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    selected = currentScreen == Screen.SETTINGS,
                    onClick = { currentScreen = Screen.SETTINGS }
                )
            }
        }
    ) { innerPadding ->
        when (currentScreen) {
            Screen.SETUP -> {
                // Poll setup status while setting up
                if (isSettingUp) {
                    LaunchedEffect(Unit) {
                        while (true) {
                            delay(2000)
                            bridge.pollSetupStatus()
                            delay(1000)
                            val status = bridge.getSetupStatus()
                            if (status != "NOT_SETUP" && status.isNotBlank()) {
                                setupStatus = status
                            }
                            if (status.contains("SETUP_COMPLETE") || status.contains("PHASE:COMPLETE")) {
                                isSettingUp = false
                                break
                            }
                            if (status.contains("ERROR")) {
                                isSettingUp = false
                                break
                            }
                        }
                    }
                }

                SetupScreen(
                    onRunSetup = {
                        isSettingUp = true
                        setupStatus = "Starting setup..."
                        bridge.runSetup()
                    },
                    onOpenTermux = { bridge.openTermux() },
                    setupStatus = setupStatus,
                    isSettingUp = isSettingUp,
                    hasRunCommandPermission = hasRunCommandPermission,
                    onRequestPermission = onRequestRunCommandPermission,
                    isTermuxReady = isTermuxReady,
                    modifier = Modifier.padding(innerPadding)
                )
            }

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
                    bridge.scanInstalledApps()
                    scope.launch {
                        delay(3000)
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
                bridge = bridge,
                modifier = Modifier.padding(innerPadding)
            )

            Screen.SETTINGS -> SettingsScreen(
                settingsStorage = settingsStorage,
                modifier = Modifier.padding(innerPadding)
            )

            Screen.APP_STREAM -> {}
        }
    }
}
