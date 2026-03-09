package ch.pianonic.pauxb.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ch.pianonic.pauxb.bridge.TermuxBridge

data class LinuxApp(
    val id: String,
    val name: String,
    val command: String,
    val packageName: String,
    val vncPort: Int? = null,
    val isRunning: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsScreen(
    apps: List<LinuxApp>,
    discoveredApps: List<TermuxBridge.DiscoveredApp>,
    onStartApp: (LinuxApp) -> Unit,
    onStopApp: (LinuxApp) -> Unit,
    onOpenApp: (LinuxApp) -> Unit,
    onInstallApp: (String, String, String) -> Unit,
    onRefreshApps: () -> Unit,
    onAddToHomeScreen: (LinuxApp) -> Unit,
    onAddDiscoveredApp: (TermuxBridge.DiscoveredApp) -> Unit,
    modifier: Modifier = Modifier
) {
    var showInstallDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Linux Apps") },
                actions = {
                    IconButton(onClick = onRefreshApps) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showInstallDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add App")
            }
        },
        modifier = modifier
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Running / configured apps section
            if (apps.isNotEmpty()) {
                item {
                    Text(
                        text = "My Apps",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
                items(apps) { app ->
                    AppCard(
                        app = app,
                        onStart = { onStartApp(app) },
                        onStop = { onStopApp(app) },
                        onOpen = { onOpenApp(app) },
                        onAddToHome = { onAddToHomeScreen(app) }
                    )
                }
            }

            // Discovered installed apps section
            val addedIds = apps.map { it.id }.toSet()
            val newApps = discoveredApps.filter { it.id !in addedIds }

            if (newApps.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Installed on Debian",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    Text(
                        text = "Apps found in your Debian environment",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(newApps) { app ->
                    DiscoveredAppCard(
                        app = app,
                        onAdd = { onAddDiscoveredApp(app) }
                    )
                }
            }

            if (apps.isEmpty() && newApps.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "No apps found",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Tap + to add manually, or refresh to scan Debian",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedButton(onClick = onRefreshApps) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Scan for apps")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInstallDialog) {
        InstallAppDialog(
            onDismiss = { showInstallDialog = false },
            onInstall = { name, pkg, cmd ->
                onInstallApp(name, pkg, cmd)
                showInstallDialog = false
            }
        )
    }
}

@Composable
private fun AppCard(
    app: LinuxApp,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpen: () -> Unit,
    onAddToHome: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = app.command,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (app.isRunning && app.vncPort != null) {
                    Text(
                        text = "VNC :${app.vncPort}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            if (app.isRunning) {
                IconButton(onClick = onAddToHome) {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = "Add to Home Screen",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                TextButton(onClick = onOpen) {
                    Text("Open")
                }
                IconButton(onClick = onStop) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Stop",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                IconButton(onClick = onAddToHome) {
                    Icon(
                        Icons.Default.Home,
                        contentDescription = "Add to Home Screen",
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
                IconButton(onClick = onStart) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Start",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoveredAppCard(
    app: TermuxBridge.DiscoveredApp,
    onAdd: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = app.command,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (app.description != null) {
                    Text(
                        text = app.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Button(onClick = onAdd) {
                Text("Add")
            }
        }
    }
}

@Composable
private fun InstallAppDialog(
    onDismiss: () -> Unit,
    onInstall: (name: String, packageName: String, command: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var packageName by remember { mutableStateOf("") }
    var command by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Linux App") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    placeholder = { Text("e.g. Firefox") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("Debian Package") },
                    placeholder = { Text("e.g. firefox-esr") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = command,
                    onValueChange = { command = it },
                    label = { Text("Launch Command") },
                    placeholder = { Text("e.g. firefox") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onInstall(name, packageName, command) },
                enabled = name.isNotBlank() && packageName.isNotBlank() && command.isNotBlank()
            ) {
                Text("Install & Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
