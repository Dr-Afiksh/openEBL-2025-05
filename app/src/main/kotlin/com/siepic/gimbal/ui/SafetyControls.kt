package com.siepic.gimbal.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.siepic.gimbal.ui.theme.GimbalDarkGray
import com.siepic.gimbal.ui.theme.GimbalLightGray
import com.siepic.gimbal.ui.theme.GimbalOrange
import com.siepic.gimbal.viewmodel.GimbalViewModel

/**
 * Shared safety knobs visible in both joystick and tilt panes:
 *   - Invert X / Invert Y (per-axis direction toggles)
 *   - Max output slider (clamps the magnitude of what's sent to the firmware)
 *
 * All three are wired to the same ViewModel state, so changes in one pane are
 * immediately reflected in the other. The slew-rate limiter is also shared
 * (no UI control — it's a fixed safety constant).
 */
@Composable
fun SafetyControls(vm: GimbalViewModel, modifier: Modifier = Modifier) {
    val invertX by vm.invertX.collectAsState()
    val invertY by vm.invertY.collectAsState()
    val amplitude by vm.amplitude.collectAsState()

    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LabeledSwitch("Invert X", invertX) { vm.setInvertX(it) }
            LabeledSwitch("Invert Y", invertY) { vm.setInvertY(it) }
        }
        Text(
            text = "Max output: ${(amplitude * 100).toInt()}%",
            color = GimbalLightGray,
            style = MaterialTheme.typography.bodyMedium,
        )
        Slider(
            value = amplitude,
            onValueChange = vm::setAmplitude,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = GimbalOrange,
                activeTrackColor = GimbalOrange,
                inactiveTrackColor = GimbalDarkGray,
            ),
        )
    }
}

@Composable
private fun LabeledSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = GimbalLightGray,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(end = 8.dp),
        )
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = GimbalOrange,
                checkedTrackColor = GimbalDarkGray,
                uncheckedThumbColor = GimbalLightGray,
                uncheckedTrackColor = GimbalDarkGray,
            ),
        )
    }
}
