package com.pulsetime.schedule

import com.pulsetime.data.Alignment
import com.pulsetime.data.PulseSettings
import com.pulsetime.haptics.PulseKind
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure time math (no Android dependencies) so it can be unit tested on the JVM.
 *
 * Battery note: Quiet Hours and Snooze are applied *here*, when choosing the next alarm, so
 * the watch is not woken at all during those windows — rather than waking up and staying silent.
 */
object PulseMath {

    /** Next raw boundary strictly after [afterMs], ignoring quiet hours / snooze. */
    fun nextBoundary(afterMs: Long, s: PulseSettings, zone: ZoneId): Long {
        val n = s.intervalMinutes.coerceIn(PulseSettings.MIN_INTERVAL, PulseSettings.MAX_INTERVAL)
        return when (s.alignment) {
            Alignment.RELATIVE -> {
                val anchor = if (s.anchorMs > 0) s.anchorMs else afterMs
                val stepMs = n * 60_000L
                val elapsed = (afterMs - anchor).coerceAtLeast(0L)
                anchor + (elapsed / stepMs + 1) * stepMs
            }
            Alignment.CLOCK -> {
                // Work in local wall-clock time so slots stay on :00/:15/... across DST changes.
                val now = LocalDateTime.ofInstant(Instant.ofEpochMilli(afterMs), zone)
                val midnight = now.toLocalDate().atStartOfDay()
                val minuteOfDay = now.toLocalTime().toSecondOfDay() / 60
                var candidate = midnight.plusMinutes(((minuteOfDay / n) + 1L) * n)
                // Intervals that don't divide 24h re-align to the next midnight.
                if (candidate.toLocalDate() != now.toLocalDate()) {
                    candidate = now.toLocalDate().plusDays(1).atStartOfDay()
                }
                var ms = candidate.atZone(zone).toInstant().toEpochMilli()
                // DST fall-back can map a wall time to an instant <= afterMs; step forward.
                if (ms <= afterMs) ms = nextBoundary(afterMs + 60_000L, s, zone)
                ms
            }
        }
    }

    /** Next boundary that is outside Quiet Hours and after any snooze. */
    fun nextPulse(afterMs: Long, s: PulseSettings, zone: ZoneId): Long {
        var t = nextBoundary(afterMs, s, zone)
        repeat(8) { // bounded: each step jumps over a whole window
            when {
                t < s.snoozeUntilMs -> t = nextBoundary(s.snoozeUntilMs - 1, s, zone)
                isQuiet(t, s, zone) -> t = nextBoundary(quietEndAfter(t, s, zone) - 1, s, zone)
                else -> return t
            }
        }
        return t
    }

    fun isQuiet(atMs: Long, s: PulseSettings, zone: ZoneId): Boolean {
        if (!s.quietEnabled || s.quietStartMinute == s.quietEndMinute) return false
        val m = LocalDateTime.ofInstant(Instant.ofEpochMilli(atMs), zone).toLocalTime().toSecondOfDay() / 60
        val start = s.quietStartMinute
        val end = s.quietEndMinute
        return if (start < end) m in start until end else (m >= start || m < end)
    }

    /** First instant at which the quiet window containing [atMs] ends. */
    private fun quietEndAfter(atMs: Long, s: PulseSettings, zone: ZoneId): Long {
        val local = LocalDateTime.ofInstant(Instant.ofEpochMilli(atMs), zone)
        val endTime = LocalTime.of(s.quietEndMinute / 60, s.quietEndMinute % 60)
        var end = local.toLocalDate().atTime(endTime)
        if (!end.isAfter(local)) end = end.plusDays(1)
        return end.atZone(zone).toInstant().toEpochMilli()
    }

    /** Which haptic pattern a pulse at [fireAtMs] should use. */
    fun kindFor(fireAtMs: Long, s: PulseSettings, zone: ZoneId): PulseKind {
        val quarterFriendly = s.intervalMinutes % 15 == 0
        return when (s.alignment) {
            Alignment.CLOCK -> {
                val minute = ZonedDateTime.ofInstant(Instant.ofEpochMilli(fireAtMs), zone).minute
                when (minute) {
                    0 -> PulseKind.HOUR
                    30 -> PulseKind.HALF
                    15, 45 -> PulseKind.QUARTER
                    else -> PulseKind.TICK
                }
            }
            Alignment.RELATIVE -> {
                if (!quarterFriendly || s.anchorMs <= 0) return PulseKind.INTERVAL
                // Encode elapsed time within the hour since start: 15/45 -> quarter, 30 -> half,
                // every full hour -> long. Lets a Meeting Pacer user "feel" how far in they are.
                val elapsedMin = ((fireAtMs - s.anchorMs + 30_000L) / 60_000L) % 60
                when (elapsedMin) {
                    0L -> PulseKind.HOUR
                    30L -> PulseKind.HALF
                    15L, 45L -> PulseKind.QUARTER
                    else -> PulseKind.INTERVAL
                }
            }
        }
    }
}
