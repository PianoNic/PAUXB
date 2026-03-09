package ch.pianonic.pauxb.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ch.pianonic.pauxb.bridge.TermuxBridge
import ch.pianonic.pauxb.terminal.TerminalSession

@Composable
fun TerminalScreen(
    session: TerminalSession,
    bridge: TermuxBridge,
    modifier: Modifier = Modifier
) {
    val output by session.output.collectAsState()
    val isRunning by session.isRunning.collectAsState()
    var commandInput by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    // Auto-scroll to bottom when output changes
    LaunchedEffect(output) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    // Start session if not running
    LaunchedEffect(Unit) {
        if (!isRunning) {
            session.start(bridge, useDebian = false)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
    ) {
        // Terminal output area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp)
                .verticalScroll(scrollState)
        ) {
            Text(
                text = output.ifEmpty { "Starting shell...\n" },
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                color = Color(0xFFCCCCCC),
                lineHeight = 18.sp
            )
        }

        // Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2D2D2D))
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            TerminalButton("CTRL") { session.sendKey('\u0003') } // Ctrl+C
            TerminalButton("TAB") { session.sendCommand("\t") }
            TerminalButton("ESC") { session.sendKey('\u001B') }
            TerminalButton("↑") { session.sendCommand("\u001B[A") }
            TerminalButton("↓") { session.sendCommand("\u001B[B") }
            TerminalButton("CLR") { session.clear() }
        }

        // Command input
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF252525))
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$ ",
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF4EC9B0),
                fontSize = 14.sp
            )

            BasicTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = Color.White
                ),
                cursorBrush = SolidColor(Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (commandInput.isNotBlank()) {
                            session.sendCommand(commandInput)
                            commandInput = ""
                        }
                    }
                ),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            )

            TextButton(
                onClick = {
                    if (commandInput.isNotBlank()) {
                        session.sendCommand(commandInput)
                        commandInput = ""
                    }
                }
            ) {
                Text("RUN", color = Color(0xFF4EC9B0))
            }
        }
    }
}

@Composable
private fun TerminalButton(label: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        modifier = Modifier.height(32.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = Color(0xFFAAAAAA)
        )
    }
}
