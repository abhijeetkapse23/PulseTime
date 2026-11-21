package com.pulsetime.presentation.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.InlineSlider
import androidx.wear.compose.material.InlineSliderDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.Text
import com.pulsetime.data.Alignment as PulseAlignment
import com.pulsetime.data.Intensity
import com.pulsetime.data.PulseSettings
import com.pulsetime.haptics.HapticEngine
import com.pulsetime.haptics.PulseKind
import com.pulsetime.schedule.OffBodyDetector
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    settings: PulseSettings,
    onUpdate: (markCustom: Boolean, transform: (PulseSettings) -> PulseSettings) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val offBodySupported = remember { OffBodyDetector.isSupported(context) }
    var testing by remember { mutableStateOf(false) }

    PulseScreen {
        item { ListHeader { Text("Interval") } }
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally)
            ) {
                PulseSettings.QUICK_INTERVALS.forEach { m ->
                    CompactChip(
                        modifier = Modifier.size(width = 40.dp, height = 48.dp),
                        onClick = { onUpdate(true) { it.copy(intervalMinutes = m) } },
                        colors = if (settings.intervalMinutes == m) ChipDefaults.primaryChipColors()
                        else ChipDefaults.secondaryChipColors(),
                        label = { Text("$m", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
                    )
                }
            }
        }
        item {
            Text("Custom: ${settings.intervalMinutes} min", textAlign = TextAlign.Center)
        }
        item {
            InlineSlider(
                value = settings.intervalMinutes,
                onValueChange = { v -> onUpdate(true) { it.copy(intervalMinutes = v) } },
                valueProgression = PulseSettings.MIN_INTERVAL..PulseSettings.MAX_INTERVAL step 5,
                decreaseIcon = { Icon(InlineSliderDefaults.Decrease, contentDescription = "Shorter") },
                increaseIcon = { Icon(InlineSliderDefaults.Increase, contentDescription = "Longer") },
                segmented = false
            )
        }
        item {
            SwitchChip(
                label = "Clock-aligned",
                checked = settings.alignment == PulseAlignment.CLOCK,
                secondary = if (settings.alignment == PulseAlignment.CLOCK) "On :00 / :15 / :30 / :45"
                else "Counts from start",
                onCheckedChange = { on ->
                    onUpdate(true) { it.copy(alignment = if (on) PulseAlignment.CLOCK else PulseAlignment.RELATIVE) }
                }
            )
        }

        item { ListHeader { Text("Feel") } }
        Intensity.entries.forEach { level ->
            item {
                ActionChip(
                    label = level.label,
                    selected = settings.intensity == level,
                    onClick = {
                        onUpdate(false) { it.copy(intensity = level) }
                        // Instant preview so the choice is made by feel, not by label.
                        HapticEngine.play(context, PulseKind.HALF, level, settings.microTaps)
                    }
                )
            }
        }
        item {
            ActionChip(
                label = if (testing) "Feeling…" else "Test Feel",
                secondary = ":00 → :30 → :15",
                enabled = !testing,
                selected = true
            ) {
                scope.launch {
                    testing = true
                    listOf(PulseKind.HOUR, PulseKind.HALF, PulseKind.QUARTER).forEach { kind ->
                        HapticEngine.play(context, kind, settings.intensity, settings.microTaps)
                        delay(1_200)
                    }
                    testing = false
                }
            }
        }
        item {
            SwitchChip(
                label = "Micro-taps",
                checked = settings.microTaps,
                secondary = "Ultra-subtle, for meetings",
                onCheckedChange = { on -> onUpdate(true) { it.copy(microTaps = on) } }
            )
        }

        item { ListHeader { Text("Smart pause") } }
        item {
            SwitchChip(
                label = "Pause off-wrist",
                checked = settings.autoPauseOffWrist && offBodySupported,
                enabled = offBodySupported,
                secondary = if (offBodySupported) "Skip pulses when not worn" else "Not supported on this watch",
                onCheckedChange = { on -> onUpdate(false) { it.copy(autoPauseOffWrist = on) } }
            )
        }
    }
}
