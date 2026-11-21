package com.pulsetime.data

/** How pulse times are computed. */
enum class Alignment {
    /** Fire on wall-clock boundaries counted from local midnight (:00, :15, :30, :45 ...). */
    CLOCK,

    /** Fire every N minutes counted from the moment the session started. */
    RELATIVE
}

enum class Intensity(val label: String) {
    GENTLE("Gentle"),
    MEDIUM("Medium"),
    STRONG("Strong")
}

/** One-tap mode presets shown on the home screen. */
enum class Preset(
    val label: String,
    val intervalMinutes: Int,
    val alignment: Alignment,
    val microTaps: Boolean,
    val description: String
) {
    ALL_DAY_ANCHOR("All-Day Anchor", 60, Alignment.CLOCK, false, "Hourly, on the hour"),
    DEEP_FOCUS("Deep Focus", 30, Alignment.RELATIVE, false, "Every 30m from start"),
    MEETING_PACER("Meeting Pacer", 15, Alignment.RELATIVE, true, "15m micro-taps"),
    CUSTOM("Custom", 0, Alignment.CLOCK, false, "Your own settings")
}

/** Immutable snapshot of everything persisted in DataStore. */
data class PulseSettings(
    val active: Boolean = false,
    val preset: Preset = Preset.ALL_DAY_ANCHOR,
    val intervalMinutes: Int = 60,
    val alignment: Alignment = Alignment.CLOCK,
    val intensity: Intensity = Intensity.MEDIUM,
    val microTaps: Boolean = false,
    val quietEnabled: Boolean = true,
    /** Minutes after local midnight, e.g. 22:30 -> 1350. */
    val quietStartMinute: Int = 22 * 60 + 30,
    val quietEndMinute: Int = 7 * 60,
    val autoPauseOffWrist: Boolean = false,
    /** Epoch ms; pulses are suppressed until this instant. 0 = not snoozed. */
    val snoozeUntilMs: Long = 0L,
    /** Epoch ms the RELATIVE timer counts from. */
    val anchorMs: Long = 0L,
    /** Epoch ms of the currently armed alarm, 0 if none. Read by Tile & Complication. */
    val nextPulseAtMs: Long = 0L
) {
    val intervalMs: Long get() = intervalMinutes * 60_000L
    fun isSnoozed(nowMs: Long): Boolean = snoozeUntilMs > nowMs

    companion object {
        const val MIN_INTERVAL = 5
        const val MAX_INTERVAL = 120
        val QUICK_INTERVALS = listOf(15, 30, 45, 60)
    }
}

fun Int.formatMinuteOfDay(): String = "%02d:%02d".format(this / 60, this % 60)
