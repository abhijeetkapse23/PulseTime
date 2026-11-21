package com.pulsetime.presentation.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.pulsetime.data.PulseSettings
import com.pulsetime.data.formatMinuteOfDay

@Composable
fun QuietHoursScreen(
    settings: PulseSettings,
    onToggle: (Boolean) -> Unit,
    onEditStart: () -> Unit,
    onEditEnd: () -> Unit
) {
    PulseScreen {
        item { ListHeader { Text("Quiet Hours") } }
        item {
            SwitchChip(
                label = "Mute at night",
                checked = settings.quietEnabled,
                secondary = "Schedule keeps running",
                onCheckedChange = onToggle
            )
        }
        item {
            ActionChip(
                label = "Starts",
                secondary = settings.quietStartMinute.formatMinuteOfDay(),
                enabled = settings.quietEnabled,
                onClick = onEditStart
            )
        }
        item {
            ActionChip(
                label = "Ends",
                secondary = settings.quietEndMinute.formatMinuteOfDay(),
                enabled = settings.quietEnabled,
                onClick = onEditEnd
            )
        }
        item {
            Text(
                text = "No wake-ups happen during quiet hours — the next alarm is set for after they end.",
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                textAlign = TextAlign.Center,
                fontSize = 11.sp,
                color = MaterialTheme.colors.onSurfaceVariant
            )
        }
    }
}
