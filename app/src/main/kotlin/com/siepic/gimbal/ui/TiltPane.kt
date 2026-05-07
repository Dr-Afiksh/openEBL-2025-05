package com.siepic.gimbal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.siepic.gimbal.protocol.ProtocolMode
import com.siepic.gimbal.ui.theme.GimbalGreen
import com.siepic.gimbal.ui.theme.GimbalLightGray
import com.siepic.gimbal.ui.theme.GimbalOrange
import com.siepic.gimbal.ui.theme.GimbalRed
import com.siepic.gimbal.viewmodel.GimbalViewModel

@Composable
fun TiltPane(vm: GimbalViewModel, modifier: Modifier = Modifier) {
    val tilt by vm.tilt.collectAsState()
    val armed by vm.armed.collectAsState()
    val protocol by vm.protocol.collectAsState()

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "TILT YOUR PHONE TO AIM",
            color = GimbalOrange,
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            text = "Pitch: %+.2f g    Roll: %+.2f g".format(tilt.y, tilt.x),
            color = GimbalLightGray,
            style = MaterialTheme.typography.titleMedium,
        )

        Button(
            onClick = { vm.toggleArmed() },
            colors = ButtonDefaults.buttonColors(
                containerColor = if (armed) GimbalGreen else GimbalRed,
            ),
            modifier = Modifier.width(240.dp).height(60.dp),
        ) {
            Text(
                text = if (armed) "ARMED — TAP TO DISARM" else "ARM (DISARMED)",
                style = MaterialTheme.typography.labelLarge,
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Joystick protocol: ${if (protocol == ProtocolMode.LEGACY_FLOAT_A) "!A floats" else "!J bytes (firmware patch needed)"}",
                color = GimbalLightGray,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Switch(
            checked = protocol == ProtocolMode.SIMPLE_J,
            onCheckedChange = { useJ ->
                vm.setProtocol(if (useJ) ProtocolMode.SIMPLE_J else ProtocolMode.LEGACY_FLOAT_A)
            },
        )
    }
}
