package com.pulsetime.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.pulsetime.data.Intensity

/** The meaning encoded in a pulse. */
enum class PulseKind {
    /** :00 — one firm long pulse. */
    HOUR,
    /** :30 — two crisp short pulses. */
    HALF,
    /** :15 / :45 — one gentle double-tap. */
    QUARTER,
    /** Any other clock mark (e.g. :20 with a 20-min interval) — one short soft tick. */
    TICK,
    /** Relative timer finished an interval — same feel as HOUR ("something completed"). */
    INTERVAL
}

/**
 * Builds and plays the "Feel the Time" waveforms.
 *
 * Battery note: a vibration is a one-shot request handed to the system vibrator service; we
 * never hold a wakelock or keep a thread alive while it plays.
 */
object HapticEngine {

    /** A segment of a waveform: [durationMs] with relative [level] 0f (pause) .. 1f (full). */
    private data class Segment(val durationMs: Long, val level: Float)

    private fun segmentsFor(kind: PulseKind): List<Segment> = when (kind) {
        PulseKind.HOUR, PulseKind.INTERVAL -> listOf(Segment(300, 1f))
        PulseKind.HALF -> listOf(Segment(100, 1f), Segment(100, 0f), Segment(100, 1f))
        PulseKind.QUARTER -> listOf(Segment(60, 0.75f), Segment(80, 0f), Segment(60, 0.75f))
        PulseKind.TICK -> listOf(Segment(80, 0.6f))
    }

    private fun Intensity.amplitude(): Int = when (this) {
        Intensity.GENTLE -> 90
        Intensity.MEDIUM -> 170
        Intensity.STRONG -> 255
    }

    /** Used only on motors without amplitude control: intensity is expressed through length. */
    private fun Intensity.durationScale(): Float = when (this) {
        Intensity.GENTLE -> 0.75f
        Intensity.MEDIUM -> 1.0f
        Intensity.STRONG -> 1.3f
    }

    /**
     * @param microTaps Meeting-Pacer mode: shorter segments and amplitude capped at "gentle"
     *                  so the pulse is felt by the wearer but not heard on a desk.
     */
    fun effectFor(
        kind: PulseKind,
        intensity: Intensity,
        microTaps: Boolean,
        hasAmplitudeControl: Boolean
    ): VibrationEffect {
        val segments = segmentsFor(kind)
        val maxAmp = if (microTaps) minOf(intensity.amplitude(), Intensity.GENTLE.amplitude())
        else intensity.amplitude()
        val lengthScale = (if (microTaps) 0.6f else 1f) *
            (if (hasAmplitudeControl) 1f else intensity.durationScale())

        // Waveform timings start with a 0 ms "off" lead-in so the first value is a buzz.
        val timings = LongArray(segments.size + 1)
        val amplitudes = IntArray(segments.size + 1)
        segments.forEachIndexed { i, seg ->
            val isOn = seg.level > 0f
            timings[i + 1] = if (isOn) (seg.durationMs * lengthScale).toLong().coerceAtLeast(20L)
            else seg.durationMs
            amplitudes[i + 1] = when {
                !isOn -> 0
                hasAmplitudeControl -> (maxAmp * seg.level).toInt().coerceIn(1, 255)
                else -> VibrationEffect.DEFAULT_AMPLITUDE
            }
        }
        return VibrationEffect.createWaveform(timings, amplitudes, /* repeat = */ -1)
    }

    fun play(context: Context, kind: PulseKind, intensity: Intensity, microTaps: Boolean) {
        val vibrator = vibrator(context) ?: return
        if (!vibrator.hasVibrator()) return
        val effect = effectFor(kind, intensity, microTaps, vibrator.hasAmplitudeControl())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // USAGE_ALARM: the user explicitly scheduled these, so they should not be dropped by
            // the "touch feedback" haptics toggle. Quiet Hours / snooze are enforced by us.
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            vibrator.vibrate(effect)
        }
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
}
