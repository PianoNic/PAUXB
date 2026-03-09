package ch.pianonic.pauxb.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ch.pianonic.pauxb.data.SettingsStorage
import ch.pianonic.pauxb.data.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsStorage: SettingsStorage,
    modifier: Modifier = Modifier
) {
    val themeMode by settingsStorage.themeMode.collectAsState()
    val dynamicColor by settingsStorage.dynamicColor.collectAsState()
    val defaultVncPort by settingsStorage.defaultVncPort.collectAsState()
    val defaultResolution by settingsStorage.defaultResolution.collectAsState()
    val keepScreenOn by settingsStorage.keepScreenOn.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Appearance section
        Text(
            text = "Appearance",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        // Theme mode
        var themeExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = themeExpanded,
            onExpandedChange = { themeExpanded = it }
        ) {
            OutlinedTextField(
                value = when (themeMode) {
                    ThemeMode.SYSTEM -> "System default"
                    ThemeMode.LIGHT -> "Light"
                    ThemeMode.DARK -> "Dark"
                },
                onValueChange = {},
                readOnly = true,
                label = { Text("Theme") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = themeExpanded,
                onDismissRequest = { themeExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("System default") },
                    onClick = {
                        settingsStorage.setThemeMode(ThemeMode.SYSTEM)
                        themeExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Light") },
                    onClick = {
                        settingsStorage.setThemeMode(ThemeMode.LIGHT)
                        themeExpanded = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Dark") },
                    onClick = {
                        settingsStorage.setThemeMode(ThemeMode.DARK)
                        themeExpanded = false
                    }
                )
            }
        }

        // Dynamic color
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Dynamic color", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Use wallpaper-based colors (Android 12+)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = dynamicColor,
                onCheckedChange = { settingsStorage.setDynamicColor(it) }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Streaming section
        Text(
            text = "Streaming",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        // Default VNC port
        var portText by remember(defaultVncPort) { mutableStateOf(defaultVncPort.toString()) }
        OutlinedTextField(
            value = portText,
            onValueChange = { newValue ->
                portText = newValue
                newValue.toIntOrNull()?.let { port ->
                    if (port in 1024..65535) {
                        settingsStorage.setDefaultVncPort(port)
                    }
                }
            },
            label = { Text("Default VNC port") },
            supportingText = { Text("Port range: 1024-65535") },
            modifier = Modifier.fillMaxWidth()
        )

        // Default resolution
        var resolutionExpanded by remember { mutableStateOf(false) }
        val resolutions = listOf("1280x720", "1920x1080", "2560x1440", "1024x768", "1600x900")
        ExposedDropdownMenuBox(
            expanded = resolutionExpanded,
            onExpandedChange = { resolutionExpanded = it }
        ) {
            OutlinedTextField(
                value = defaultResolution,
                onValueChange = {},
                readOnly = true,
                label = { Text("Default resolution") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = resolutionExpanded) },
                modifier = Modifier
                    .fillMaxWidth()
                    .menuAnchor()
            )
            ExposedDropdownMenu(
                expanded = resolutionExpanded,
                onDismissRequest = { resolutionExpanded = false }
            ) {
                resolutions.forEach { res ->
                    DropdownMenuItem(
                        text = { Text(res) },
                        onClick = {
                            settingsStorage.setDefaultResolution(res)
                            resolutionExpanded = false
                        }
                    )
                }
            }
        }

        // Keep screen on
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Keep screen on", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Prevent display from sleeping while streaming",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = keepScreenOn,
                onCheckedChange = { settingsStorage.setKeepScreenOn(it) }
            )
        }
    }
}
