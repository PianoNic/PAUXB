package ch.pianonic.pauxb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import ch.pianonic.pauxb.bridge.TermuxBridge
import ch.pianonic.pauxb.data.SettingsStorage
import ch.pianonic.pauxb.ui.screens.AppStreamScreen
import ch.pianonic.pauxb.ui.theme.PAUXBTheme

/**
 * Standalone activity for streaming a single Linux app via VNC.
 * Uses documentLaunchMode="always" so each shortcut creates a separate
 * window in Samsung DeX, enabling true multi-window Linux app usage.
 */
class AppStreamActivity : ComponentActivity() {

    private lateinit var bridge: TermuxBridge
    private lateinit var settingsStorage: SettingsStorage

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bridge = TermuxBridge(this)
        settingsStorage = SettingsStorage(this)
        enableEdgeToEdge()

        val appId = intent.getStringExtra("app_id") ?: run {
            finish()
            return
        }
        val appName = intent.getStringExtra("app_name") ?: appId
        val appCommand = intent.getStringExtra("app_command") ?: run {
            finish()
            return
        }

        // Start the bridge daemon (idempotent if already running)
        bridge.startBridge()

        // Start the app via bridge
        bridge.startApp(appId, appCommand)

        setContent {
            val themeMode by settingsStorage.themeMode.collectAsState()
            val dynamicColor by settingsStorage.dynamicColor.collectAsState()

            PAUXBTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor
            ) {
                AppStreamScreen(
                    appName = appName,
                    vncPort = 5910,
                    appId = appId,
                    onBack = { finish() },
                    onResizeRequest = { id, width, height ->
                        bridge.resizeApp(id, width, height)
                    }
                )
            }
        }
    }
}
