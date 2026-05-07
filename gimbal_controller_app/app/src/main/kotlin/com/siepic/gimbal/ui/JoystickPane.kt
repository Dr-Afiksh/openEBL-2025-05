package com.siepic.gimbal.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.siepic.gimbal.ui.theme.GimbalDarkGray
import com.siepic.gimbal.ui.theme.GimbalLightGray
import com.siepic.gimbal.ui.theme.GimbalMediumGray
import com.siepic.gimbal.ui.theme.GimbalOrange
import com.siepic.gimbal.viewmodel.GimbalViewModel
import kotlin.math.sqrt

private val PadSize = 300.dp
private val KnobSize = 60.dp

@Composable
fun JoystickPane(vm: GimbalViewModel, modifier: Modifier = Modifier) {
    val joy by vm.joy.collectAsState()

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(PadSize)
                .pointerInput(Unit) {
                    val sz = size.width.toFloat()
                    val center = sz / 2f
                    val maxR = sz / 2f - (KnobSize.toPx() / 2f)
                    detectDragGestures(
                        onDragStart = { offset -> updateFromOffset(vm, offset, center, maxR) },
                        onDrag = { change, _ ->
                            updateFromOffset(vm, change.position, center, maxR)
                            change.consume()
                        },
                        onDragEnd = { vm.releaseJoy() },
                        onDragCancel = { vm.releaseJoy() },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val center = Offset(w / 2f, w / 2f)
                val outerR = w / 2f
                drawCircle(GimbalDarkGray, radius = outerR, center = center)
                drawCircle(GimbalMediumGray, radius = outerR * 0.66f, center = center, style = Stroke(width = 2f))
                drawCircle(GimbalMediumGray, radius = outerR * 0.33f, center = center, style = Stroke(width = 2f))

                val knobR = KnobSize.toPx() / 2f
                val maxR = outerR - knobR
                val knobCenter = Offset(
                    x = center.x + joy.x * maxR,
                    y = center.y + joy.y * maxR,
                )
                drawCircle(GimbalOrange, radius = knobR, center = knobCenter)
            }
        }

        Text(
            text = "X: %.2f   Y: %.2f".format(joy.x, joy.y),
            color = GimbalLightGray,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

private fun updateFromOffset(
    vm: GimbalViewModel,
    offset: Offset,
    center: Float,
    maxR: Float,
) {
    if (maxR <= 0f) return
    val dx = (offset.x - center) / maxR
    val dy = (offset.y - center) / maxR
    val mag = sqrt(dx * dx + dy * dy)
    val (cx, cy) = if (mag > 1f) Pair(dx / mag, dy / mag) else Pair(dx, dy)
    vm.updateJoy(cx, cy)
}
