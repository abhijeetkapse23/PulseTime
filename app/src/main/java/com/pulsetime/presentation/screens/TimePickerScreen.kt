package com.pulsetime.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.SwipeToDismissBoxState
import androidx.wear.compose.foundation.edgeSwipeToDismiss
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Picker
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.rememberPickerState

private const val MINUTE_STEP = 5

/**
 * Hour + minute (5-min steps) picker. Wrapped in edgeSwipeToDismiss so vertical/horizontal
 * drags on the pickers never accidentally dismiss the screen; swipe-back works from the edge.
 */
@Composable
fun TimePickerScreen(
    title: String,
    initialMinuteOfDay: Int,
    swipeState: SwipeToDismissBoxState,
    onConfirm: (Int) -> Unit
) {
    val hourState = rememberPickerState(initialNumberOfOptions = 24, initiallySelectedOption = initialMinuteOfDay / 60)
    val minuteState = rememberPickerState(
        initialNumberOfOptions = 60 / MINUTE_STEP,
        initiallySelectedOption = (initialMinuteOfDay % 60) / MINUTE_STEP
    )
    // Only the focused column reacts to drags; tap a column to focus it.
    var focused by remember { mutableIntStateOf(0) }

    Scaffold(
        modifier = Modifier.edgeSwipeToDismiss(swipeState),
        timeText = { TimeText() }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(title, fontSize = 13.sp, color = MaterialTheme.colors.primary)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Picker(
                    state = hourState,
                    contentDescription = "Hour",
                    readOnly = focused != 0,
                    onSelected = { focused = 0 },
                    modifier = Modifier.size(width = 56.dp, height = 100.dp)
                ) { option ->
                    Text("%02d".format(option), fontSize = 28.sp)
                }
                Text(":", fontSize = 28.sp, modifier = Modifier.padding(horizontal = 4.dp))
                Picker(
                    state = minuteState,
                    contentDescription = "Minute",
                    readOnly = focused != 1,
                    onSelected = { focused = 1 },
                    modifier = Modifier.size(width = 56.dp, height = 100.dp)
                ) { option ->
                    Text("%02d".format(option * MINUTE_STEP), fontSize = 28.sp)
                }
            }
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    onConfirm(hourState.selectedOption * 60 + minuteState.selectedOption * MINUTE_STEP)
                },
                modifier = Modifier.size(MinTouch)
            ) {
                Text("✓", fontSize = 20.sp)
            }
            Spacer(Modifier.width(1.dp))
        }
    }
}
