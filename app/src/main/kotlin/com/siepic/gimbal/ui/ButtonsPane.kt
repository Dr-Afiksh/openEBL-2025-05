package com.siepic.gimbal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.siepic.gimbal.ui.theme.GimbalBlack
import com.siepic.gimbal.ui.theme.GimbalOrange
import com.siepic.gimbal.viewmodel.GimbalViewModel

@Composable
fun ButtonsPane(vm: GimbalViewModel, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(horizontalArrangement = Arrangement.Center) {
            Spacer(Modifier.size(96.dp))
            HoldButton(label = "▲") { vm.pressButton('5', it) }
            Spacer(Modifier.size(96.dp))
        }
        Row(horizontalArrangement = Arrangement.Center) {
            HoldButton(label = "◀") { vm.pressButton('7', it) }
            Spacer(Modifier.size(96.dp))
            HoldButton(label = "▶") { vm.pressButton('8', it) }
        }
        Row(horizontalArrangement = Arrangement.Center) {
            Spacer(Modifier.size(96.dp))
            HoldButton(label = "▼") { vm.pressButton('6', it) }
            Spacer(Modifier.size(96.dp))
        }
    }
}

@Composable
private fun HoldButton(label: String, onPress: (pressed: Boolean) -> Unit) {
    Box(
        modifier = Modifier
            .padding(8.dp)
            .size(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(GimbalOrange)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val anyDown = event.changes.any { it.changedToDown() }
                        val anyUp = event.changes.any { it.changedToUp() }
                        if (anyDown) onPress(true)
                        if (anyUp) onPress(false)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = GimbalBlack,
            style = MaterialTheme.typography.titleLarge.copy(fontSize = 32.sp),
        )
    }
}
