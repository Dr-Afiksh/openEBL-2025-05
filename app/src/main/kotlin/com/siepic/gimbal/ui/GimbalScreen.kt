package com.siepic.gimbal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.siepic.gimbal.ble.ConnectionState
import com.siepic.gimbal.ui.theme.GimbalBlack
import com.siepic.gimbal.ui.theme.GimbalDarkGray
import com.siepic.gimbal.ui.theme.GimbalGreen
import com.siepic.gimbal.ui.theme.GimbalLightGray
import com.siepic.gimbal.ui.theme.GimbalMediumGray
import com.siepic.gimbal.ui.theme.GimbalOrange
import com.siepic.gimbal.ui.theme.GimbalRed
import com.siepic.gimbal.viewmodel.GimbalViewModel
import com.siepic.gimbal.viewmodel.Mode
import kotlinx.coroutines.delay

@Composable
fun GimbalScreen(
    onRequestPermissions: () -> Unit,
    permissionsGranted: Boolean,
) {
    val vm: GimbalViewModel = viewModel()
    val mode by vm.mode.collectAsState()
    val conn by vm.connection.collectAsState()
    val error by vm.errors.collectAsState()

    // Auto-clear transient error after 4 s so the user isn't stuck looking at it.
    LaunchedEffect(error) {
        if (error != null) {
            delay(4_000)
            vm.clearError()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GimbalBlack),
    ) {
        StatusBar(
            connection = conn,
            permissionsGranted = permissionsGranted,
            error = error,
            onConnectClick = {
                if (!permissionsGranted) onRequestPermissions() else vm.connectOrDisconnect()
            },
        )
        ModeSelector(currentMode = mode, onSelect = vm::setMode)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (mode) {
                Mode.JOYSTICK -> JoystickPane(vm)
                Mode.TILT -> TiltPane(vm)
                Mode.BUTTONS -> ButtonsPane(vm)
                Mode.COLOR -> ColorPane(vm)
            }
        }
        ActionRow(onAction = vm::tapAction)
    }
}

@Composable
private fun StatusBar(
    connection: ConnectionState,
    permissionsGranted: Boolean,
    error: String?,
    onConnectClick: () -> Unit,
) {
    val (label, color) = when (connection) {
        ConnectionState.CONNECTED -> "CONNECTED" to GimbalGreen
        ConnectionState.SCANNING -> "SCANNING…" to GimbalOrange
        ConnectionState.CONNECTING -> "CONNECTING…" to GimbalOrange
        ConnectionState.DISCONNECTED -> "DISCONNECTED" to GimbalRed
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(GimbalDarkGray)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = color,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            )
            if (error != null) {
                Text(text = error, color = GimbalRed, style = MaterialTheme.typography.bodyMedium)
            } else if (!permissionsGranted) {
                Text(
                    text = "Tap CONNECT to grant Bluetooth permissions",
                    color = GimbalLightGray,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        Button(
            onClick = onConnectClick,
            colors = ButtonDefaults.buttonColors(
                containerColor = GimbalOrange,
                contentColor = GimbalBlack,
            ),
        ) {
            Text(
                text = if (connection == ConnectionState.CONNECTED) "DISCONNECT" else "CONNECT",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun ModeSelector(currentMode: Mode, onSelect: (Mode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeButton("JOYSTICK", currentMode == Mode.JOYSTICK, Modifier.weight(1f)) {
            onSelect(Mode.JOYSTICK)
        }
        ModeButton("TILT", currentMode == Mode.TILT, Modifier.weight(1f)) {
            onSelect(Mode.TILT)
        }
        ModeButton("BUTTONS", currentMode == Mode.BUTTONS, Modifier.weight(1f)) {
            onSelect(Mode.BUTTONS)
        }
        ModeButton("COLOR", currentMode == Mode.COLOR, Modifier.weight(1f)) {
            onSelect(Mode.COLOR)
        }
    }
}

@Composable
private fun ModeButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier.wrapContentHeight(),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) GimbalOrange else GimbalMediumGray,
            contentColor = if (selected) GimbalBlack else GimbalLightGray,
        ),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun ActionRow(onAction: (Char) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionButton("SPEED", '1', Modifier.weight(1f), onAction)
        ActionButton("CENTER", '2', Modifier.weight(1f), onAction)
        ActionButton("LEVEL", '3', Modifier.weight(1f), onAction)
        ActionButton("ON/OFF", '4', Modifier.weight(1f), onAction)
    }
}

@Composable
private fun ActionButton(
    label: String,
    button: Char,
    modifier: Modifier = Modifier,
    onAction: (Char) -> Unit,
) {
    Button(
        onClick = { onAction(button) },
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = GimbalDarkGray,
            contentColor = GimbalLightGray,
        ),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
        )
    }
}
