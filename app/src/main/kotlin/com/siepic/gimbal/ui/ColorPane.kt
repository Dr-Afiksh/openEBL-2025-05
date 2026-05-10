package com.siepic.gimbal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.siepic.gimbal.ui.theme.GimbalDarkGray
import com.siepic.gimbal.ui.theme.GimbalLightGray
import com.siepic.gimbal.viewmodel.GimbalViewModel

@Composable
fun ColorPane(vm: GimbalViewModel, modifier: Modifier = Modifier) {
    val (r, g, b) = vm.color.collectAsState().value

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(r, g, b)),
        )

        Text(
            text = "RGB  %d  %d  %d".format(r, g, b),
            color = GimbalLightGray,
            style = MaterialTheme.typography.titleMedium,
        )

        ChannelSlider("R", r, Color(255, 80, 80)) { vm.setColor(it, g, b) }
        ChannelSlider("G", g, Color(120, 220, 120)) { vm.setColor(r, it, b) }
        ChannelSlider("B", b, Color(120, 160, 255)) { vm.setColor(r, g, it) }
    }
}

@Composable
private fun ChannelSlider(
    label: String,
    value: Int,
    accent: Color,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = accent,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(end = 12.dp),
        )
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = 0f..255f,
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = GimbalDarkGray,
            ),
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "%3d".format(value),
            color = GimbalLightGray,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}
