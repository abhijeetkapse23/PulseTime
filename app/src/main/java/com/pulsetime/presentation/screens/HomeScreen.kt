package com.pulsetime.presentation.screens

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.pulsetime.data.Preset
import com.pulsetime.data.PulseSettings
import com.pulsetime.data.formatMinuteOfDay
import com.pulsetime.schedule.PulseScheduler
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val clockFmt = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun HomeScreen(
    settings: PulseSettings,
    onToggle: () -> Unit,
    onPreset: (Preset) -> Unit,
    onSnooze: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenQuiet: () -> Unit
) {
    val context = LocalContext.current

    // Refresh the countdown text twice a minute — only while this screen is composed (screen on).
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(30_000)
        }
    }

    val status = when {
        !settings.active -> "Paused"
        settings.isSnoozed(now) -> "Snoozed until ${fmt(settings.snoozeUntilMs)}"
        PulseScheduler.isQuietNow(settings, now) -> "Quiet hours"
        settings.nextPulseAtMs > now -> {
            val mins = (settings.nextPulseAtMs - now + 59_999L) / 60_000L
            "Next pulse in ${mins}m"
        }
        else -> "Pulsing…"
    }

    PulseScreen {
        item {
            Text(
                text = status,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                textAlign = TextAlign.Center,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colors.onBackground
            )
        }
        item {
            Text(
                text = if (settings.active) "Every ${settings.intervalMinutes}m · ${settings.preset.label}"
                else "${settings.intervalMinutes}m · ${settings.alignment.name.lowercase()}",
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                color = MaterialTheme.colors.onSurfaceVariant
            )
        }
        item {
            Button(
                onClick = onToggle,
                modifier = Modifier.padding(vertical = 6.dp).height(MinTouch + 8.dp).fillMaxWidth(0.6f),
                colors = if (settings.active) ButtonDefaults.secondaryButtonColors()
                else ButtonDefaults.primaryButtonColors()
            ) {
                Text(if (settings.active) "Pause" else "Start", fontWeight = FontWeight.Bold)
            }
        }
        if (settings.active) {
            item {
                SwitchChip(
                    label = "Snooze 1 hour",
                    checked = settings.isSnoozed(now),
                    secondary = if (settings.isSnoozed(now)) "Until ${fmt(settings.snoozeUntilMs)}" else null,
                    onCheckedChange = { onSnooze() }
                )
            }
        }
        if (!PulseScheduler.canScheduleExact(context) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            item {
                ActionChip(label = "Allow exact alarms", secondary = "Pulses may drift without it") {
                    context.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        }
        item { ListHeader { Text("Modes") } }
        Preset.entries.filter { it != Preset.CUSTOM }.forEach { preset ->
            item {
                ActionChip(
                    label = preset.label,
                    secondary = preset.description,
                    selected = settings.active && settings.preset == preset,
                    onClick = { onPreset(preset) }
                )
            }
        }
        item { ListHeader { Text("Setup") } }
        item {
            ActionChip(
                label = "Interval & Feel",
                secondary = "${settings.intervalMinutes}m · ${settings.intensity.label}",
                onClick = onOpenSettings
            )
        }
        item {
            ActionChip(
                label = "Quiet Hours",
                secondary = if (settings.quietEnabled)
                    "${settings.quietStartMinute.formatMinuteOfDay()} – ${settings.quietEndMinute.formatMinuteOfDay()}"
                else "Off",
                onClick = onOpenQuiet
            )
        }
    }
}

private fun fmt(epochMs: Long): String =
    Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(clockFmt)
