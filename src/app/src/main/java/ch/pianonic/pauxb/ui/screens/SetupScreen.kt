package ch.pianonic.pauxb.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    modifier: Modifier = Modifier
) {
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
                    text = setupStatus,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = when {
                        setupStatus.contains("COMPLETE") -> MaterialTheme.colorScheme.primary
                        setupStatus.contains("ERROR") -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                if (isSettingUp) {
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
                SetupStep("1", "Install proot-distro & VNC in Termux")
                SetupStep("2", "Install Debian Linux environment")
                SetupStep("3", "Configure X11/VNC display streaming")
                SetupStep("4", "Install PAUXB bridge daemon")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onRunSetup,
            enabled = !isSettingUp,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            if (isSettingUp) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Setting up...")
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
private fun SetupStep(number: String, description: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 14.sp
                )
            }
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
