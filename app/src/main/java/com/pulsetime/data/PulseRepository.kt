package com.pulsetime.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.pulseDataStore: DataStore<Preferences> by preferencesDataStore(name = "pulse_prefs")

/**
 * Single source of truth for settings. DataStore is process-safe for our single process and
 * reads are cheap, so the alarm receiver, tile and complication all read from here directly
 * instead of keeping anything resident in memory.
 */
object PulseRepository {

    private object Keys {
        val ACTIVE = booleanPreferencesKey("active")
        val PRESET = stringPreferencesKey("preset")
        val INTERVAL = intPreferencesKey("interval_min")
        val ALIGNMENT = stringPreferencesKey("alignment")
        val INTENSITY = stringPreferencesKey("intensity")
        val MICRO = booleanPreferencesKey("micro_taps")
        val QUIET_ON = booleanPreferencesKey("quiet_enabled")
        val QUIET_START = intPreferencesKey("quiet_start")
        val QUIET_END = intPreferencesKey("quiet_end")
        val OFF_WRIST = booleanPreferencesKey("auto_pause_off_wrist")
        val SNOOZE = longPreferencesKey("snooze_until")
        val ANCHOR = longPreferencesKey("anchor")
        val NEXT = longPreferencesKey("next_pulse_at")
    }

    fun flow(context: Context): Flow<PulseSettings> =
        context.applicationContext.pulseDataStore.data.map { it.toSettings() }

    suspend fun get(context: Context): PulseSettings = flow(context).first()

    /** Atomically read-modify-write the whole settings object. Returns the new value. */
    suspend fun update(context: Context, transform: (PulseSettings) -> PulseSettings): PulseSettings {
        var result = PulseSettings()
        context.applicationContext.pulseDataStore.edit { prefs ->
            result = transform(prefs.toSettings())
            prefs.write(result)
        }
        return result
    }

    private fun Preferences.toSettings(): PulseSettings {
        val d = PulseSettings()
        return PulseSettings(
            active = this[Keys.ACTIVE] ?: d.active,
            preset = enumOr(this[Keys.PRESET], d.preset),
            intervalMinutes = (this[Keys.INTERVAL] ?: d.intervalMinutes)
                .coerceIn(PulseSettings.MIN_INTERVAL, PulseSettings.MAX_INTERVAL),
            alignment = enumOr(this[Keys.ALIGNMENT], d.alignment),
            intensity = enumOr(this[Keys.INTENSITY], d.intensity),
            microTaps = this[Keys.MICRO] ?: d.microTaps,
            quietEnabled = this[Keys.QUIET_ON] ?: d.quietEnabled,
            quietStartMinute = this[Keys.QUIET_START] ?: d.quietStartMinute,
            quietEndMinute = this[Keys.QUIET_END] ?: d.quietEndMinute,
            autoPauseOffWrist = this[Keys.OFF_WRIST] ?: d.autoPauseOffWrist,
            snoozeUntilMs = this[Keys.SNOOZE] ?: d.snoozeUntilMs,
            anchorMs = this[Keys.ANCHOR] ?: d.anchorMs,
            nextPulseAtMs = this[Keys.NEXT] ?: d.nextPulseAtMs
        )
    }

    private fun MutablePreferences.write(s: PulseSettings) {
        this[Keys.ACTIVE] = s.active
        this[Keys.PRESET] = s.preset.name
        this[Keys.INTERVAL] = s.intervalMinutes
        this[Keys.ALIGNMENT] = s.alignment.name
        this[Keys.INTENSITY] = s.intensity.name
        this[Keys.MICRO] = s.microTaps
        this[Keys.QUIET_ON] = s.quietEnabled
        this[Keys.QUIET_START] = s.quietStartMinute
        this[Keys.QUIET_END] = s.quietEndMinute
        this[Keys.OFF_WRIST] = s.autoPauseOffWrist
        this[Keys.SNOOZE] = s.snoozeUntilMs
        this[Keys.ANCHOR] = s.anchorMs
        this[Keys.NEXT] = s.nextPulseAtMs
    }

    private inline fun <reified T : Enum<T>> enumOr(name: String?, fallback: T): T =
        name?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: fallback
}
