package com.pulsetime.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountDownTimeReference
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.RangedValueComplicationData
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.pulsetime.data.PulseRepository
import com.pulsetime.data.PulseSettings
import com.pulsetime.presentation.MainActivity
import java.time.Instant
import java.util.concurrent.TimeUnit

/**
 * SHORT_TEXT: countdown to the next pulse.
 * RANGED_VALUE: progress arc through the current interval + countdown text.
 *
 * Battery note: the countdown uses TimeDifferenceComplicationText, which the watch face
 * renders against its own clock — no per-minute updates from us are needed.
 */
class PulseComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? {
        val now = System.currentTimeMillis()
        val preview = PulseSettings(active = true, intervalMinutes = 15, nextPulseAtMs = now + 9 * 60_000L)
        return build(type, preview, now, tap = null)
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val s = PulseRepository.get(applicationContext)
        return build(request.complicationType, s, System.currentTimeMillis(), tapIntent())
    }

    private fun build(type: ComplicationType, s: PulseSettings, now: Long, tap: PendingIntent?): ComplicationData? {
        val running = s.active && s.nextPulseAtMs > now
        val text: ComplicationText = when {
            !s.active -> plain("Off")
            s.isSnoozed(now) -> plain("Zzz")
            running -> TimeDifferenceComplicationText.Builder(
                TimeDifferenceStyle.SHORT_SINGLE_UNIT,
                CountDownTimeReference(Instant.ofEpochMilli(s.nextPulseAtMs))
            ).setMinimumTimeUnit(TimeUnit.MINUTES).build()
            else -> plain("•")
        }
        val description = plain(if (running) "Next PulseTime pulse" else "PulseTime paused")

        return when (type) {
            ComplicationType.SHORT_TEXT ->
                ShortTextComplicationData.Builder(text, description)
                    .setTitle(plain("PULSE"))
                    .setTapAction(tap)
                    .build()

            ComplicationType.RANGED_VALUE -> {
                val total = s.intervalMinutes.toFloat()
                val elapsed = if (running) {
                    val remaining = (s.nextPulseAtMs - now) / 60_000f
                    (total - remaining).coerceIn(0f, total)
                } else 0f
                RangedValueComplicationData.Builder(elapsed, 0f, total, description)
                    .setText(text)
                    .setTapAction(tap)
                    .build()
            }

            else -> null
        }
    }

    private fun plain(s: String) = PlainComplicationText.Builder(s).build()

    private fun tapIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
}
