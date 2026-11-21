package com.pulsetime

import android.content.ComponentName
import android.content.Context
import androidx.wear.tiles.TileService
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester
import com.pulsetime.complication.PulseComplicationService
import com.pulsetime.data.Preset
import com.pulsetime.data.PulseRepository
import com.pulsetime.data.PulseSettings
import com.pulsetime.schedule.PulseScheduler
import com.pulsetime.tile.PulseTileService

/**
 * All user actions (UI, Tile) go through here so that persisting state, re-arming the alarm
 * and refreshing the Tile/Complication always happen together.
 */
object PulseController {

    private const val SNOOZE_MS = 60 * 60_000L

    suspend fun start(context: Context, preset: Preset? = null, refreshTile: Boolean = true) {
        val now = System.currentTimeMillis()
        PulseRepository.update(context) { s ->
            val base = if (preset != null && preset != Preset.CUSTOM) {
                s.copy(
                    preset = preset,
                    intervalMinutes = preset.intervalMinutes,
                    alignment = preset.alignment,
                    microTaps = preset.microTaps
                )
            } else s
            base.copy(active = true, anchorMs = now, snoozeUntilMs = 0L)
        }
        reschedule(context, refreshTile = refreshTile)
    }

    suspend fun stop(context: Context, refreshTile: Boolean = true) {
        PulseRepository.update(context) { it.copy(active = false, nextPulseAtMs = 0L, snoozeUntilMs = 0L) }
        PulseScheduler.cancel(context)
        refreshSurfaces(context, refreshTile)
    }

    suspend fun toggle(context: Context, refreshTile: Boolean = true) {
        if (PulseRepository.get(context).active) stop(context, refreshTile)
        else start(context, preset = null, refreshTile = refreshTile)
    }

    suspend fun toggleSnooze(context: Context) {
        val now = System.currentTimeMillis()
        PulseRepository.update(context) {
            it.copy(snoozeUntilMs = if (it.isSnoozed(now)) 0L else now + SNOOZE_MS)
        }
        reschedule(context)
    }

    /**
     * Apply a settings change. Manual changes to interval/alignment/micro-taps switch the
     * preset to CUSTOM. If running, the alarm is re-armed immediately so the change takes effect.
     */
    suspend fun updateSettings(context: Context, markCustom: Boolean = false, transform: (PulseSettings) -> PulseSettings) {
        val now = System.currentTimeMillis()
        PulseRepository.update(context) { old ->
            val new = transform(old).let { if (markCustom) it.copy(preset = Preset.CUSTOM) else it }
            // Restart the relative timer when its cadence changes so pulses stay meaningful.
            if (new.intervalMinutes != old.intervalMinutes || new.alignment != old.alignment) {
                new.copy(anchorMs = now)
            } else new
        }
        reschedule(context)
    }

    /** Re-arm (or cancel) the single alarm based on persisted state. */
    suspend fun reschedule(
        context: Context,
        afterMs: Long = System.currentTimeMillis(),
        refreshTile: Boolean = true
    ) {
        val s = PulseRepository.get(context)
        if (!s.active) {
            PulseScheduler.cancel(context)
            if (s.nextPulseAtMs != 0L) PulseRepository.update(context) { it.copy(nextPulseAtMs = 0L) }
        } else {
            val next = PulseScheduler.scheduleNext(context, s, afterMs)
            PulseRepository.update(context) { it.copy(nextPulseAtMs = next) }
        }
        refreshSurfaces(context, refreshTile)
    }

    /**
     * Push-based refresh: the Tile and Complication are only re-rendered when state actually
     * changes (start/stop/pulse), never on a polling timer.
     */
    fun refreshSurfaces(context: Context, refreshTile: Boolean = true) {
        if (refreshTile) {
            TileService.getUpdater(context).requestUpdate(PulseTileService::class.java)
        }
        ComplicationDataSourceUpdateRequester
            .create(context, ComponentName(context, PulseComplicationService::class.java))
            .requestUpdateAll()
    }
}
