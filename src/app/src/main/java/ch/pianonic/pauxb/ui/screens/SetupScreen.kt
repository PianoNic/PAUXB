package ch.pianonic.pauxb.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SetupScreen(
    onRunSetup: () -> Unit,
    onOpenTermux: () -> Unit,
    setupStatus: String,
    isSettingUp: Boolean,
    hasRunCommandPermission: Boolean = true,
    onRequestPermission: () -> Unit = {},
    isTermuxReady: Boolean = true,
    modifier: Modifier = Modifier
) {
    // Parse current phase from status string
    val currentPhase = when {
        setupStatus.contains("PHASE:COMPLETE") || setupStatus.contains("SETUP_COMPLETE") -> 5
        setupStatus.contains("PHASE:INSTALL_BRIDGE") -> 4
        setupStatus.contains("PHASE:SETUP_DEBIAN") -> 3
        setupStatus.contains("PHASE:INSTALL_DEBIAN") -> 2
        setupStatus.contains("PHASE:INSTALL_DEPS") || setupStatus.contains("PHASE:UPDATE") -> 1
        isSettingUp -> 0
        else -> -1
    }

    val isComplete = currentPhase >= 5

    // Extract the human-readable message after the phase prefix
    val statusMessage = when {
        setupStatus.contains("SETUP_COMPLETE") -> "Setup complete!"
        setupStatus.contains(" - ") -> setupStatus.substringAfter(" - ")
        else -> setupStatus
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "PAUXB",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "PianoNic's Android Unix Bridge",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Setup Status",
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = statusMessage,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = when {
                        isComplete -> MaterialTheme.colorScheme.primary
                        setupStatus.contains("ERROR") -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                if (isSettingUp && !isComplete) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateContentSize()
                    )
                }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "What this will set up:",
                    style = MaterialTheme.typography.titleSmall
                )
                SetupStep("1", "Install Termux dependencies", completed = currentPhase > 1)
                SetupStep("2", "Install Debian Linux environment", completed = currentPhase > 2)
                SetupStep("3", "Configure X11/VNC display streaming", completed = currentPhase > 3)
                SetupStep("4", "Install PAUXB bridge daemon", completed = currentPhase > 4)
            }
        }

        if (!hasRunCommandPermission) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "Permission Required",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        text = "PAUXB needs permission to run commands in Termux. Tap below to grant it.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Button(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Grant Permission")
                    }
                }
            }
        }

        if (hasRunCommandPermission && !isTermuxReady) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary
                        )
                        Text(
                            text = "Termux Configuration Needed",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Text(
                        text = "Termux needs to allow external apps. Open Termux and run:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "echo 'allow-external-apps = true' >> ~/.termux/termux.properties",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "Then restart Termux and return here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    OutlinedButton(
                        onClick = onOpenTermux,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Termux")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onRunSetup,
            enabled = !isSettingUp && !isComplete && hasRunCommandPermission && isTermuxReady,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            if (isSettingUp && !isComplete) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Setting up...")
            } else if (isComplete) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Setup Complete", fontSize = 18.sp)
            } else {
                Text("Run Setup", fontSize = 18.sp)
            }
        }

        OutlinedButton(
            onClick = onOpenTermux,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Open Termux Terminal")
        }
    }
}

@Composable
private fun SetupStep(number: String, description: String, completed: Boolean = false) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = if (completed) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (completed) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Complete",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text(
                        text = number,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontSize = 14.sp
                    )
                }
            }
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = if (completed) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface
        )
    }
}
