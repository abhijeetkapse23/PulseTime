package com.pulsetime.schedule

import com.pulsetime.data.Alignment
import com.pulsetime.data.PulseSettings
import com.pulsetime.haptics.PulseKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class PulseMathTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(h: Int, m: Int, s: Int = 0) =
        LocalDateTime.of(2026, 11, 21, h, m, s).atZone(zone).toInstant().toEpochMilli()
    private fun ms(dt: LocalDateTime) = dt.atZone(zone).toInstant().toEpochMilli()

    private val noQuiet = PulseSettings(active = true, quietEnabled = false)

    @Test fun clockAligned15_goesToNextQuarter() {
        val s = noQuiet.copy(intervalMinutes = 15, alignment = Alignment.CLOCK)
        assertEquals(at(10, 15), PulseMath.nextBoundary(at(10, 7, 30), s, zone))
        assertEquals(at(10, 30), PulseMath.nextBoundary(at(10, 15), s, zone)) // strictly after
    }

    @Test fun clockAligned_oddIntervalRealignsAtMidnight() {
        val s = noQuiet.copy(intervalMinutes = 7, alignment = Alignment.CLOCK)
        val next = PulseMath.nextBoundary(at(23, 58), s, zone)
        assertEquals(ms(LocalDateTime.of(2026, 11, 22, 0, 0)), next)
    }

    @Test fun relative_countsFromAnchor() {
        val s = noQuiet.copy(intervalMinutes = 25, alignment = Alignment.RELATIVE, anchorMs = at(10, 7))
        assertEquals(at(10, 32), PulseMath.nextBoundary(at(10, 10), s, zone))
        assertEquals(at(10, 57), PulseMath.nextBoundary(at(10, 32), s, zone))
    }

    @Test fun quietHours_skipOvernightWithoutWakeups() {
        val s = PulseSettings(
            active = true, intervalMinutes = 60, alignment = Alignment.CLOCK,
            quietEnabled = true, quietStartMinute = 22 * 60 + 30, quietEndMinute = 7 * 60
        )
        assertTrue(PulseMath.isQuiet(at(23, 0), s, zone))
        assertFalse(PulseMath.isQuiet(at(7, 0), s, zone))
        val next = PulseMath.nextPulse(at(22, 10), s, zone)
        assertEquals(ms(LocalDateTime.of(2026, 11, 22, 7, 0)), next)
    }

    @Test fun snooze_pushesPastSnoozeEnd() {
        val s = noQuiet.copy(intervalMinutes = 15, snoozeUntilMs = at(11, 5))
        assertEquals(at(11, 15), PulseMath.nextPulse(at(10, 5), s, zone))
    }

    @Test fun kinds_clockAligned() {
        val s = noQuiet.copy(intervalMinutes = 15, alignment = Alignment.CLOCK)
        assertEquals(PulseKind.HOUR, PulseMath.kindFor(at(11, 0), s, zone))
        assertEquals(PulseKind.HALF, PulseMath.kindFor(at(11, 30), s, zone))
        assertEquals(PulseKind.QUARTER, PulseMath.kindFor(at(11, 45), s, zone))
        assertEquals(PulseKind.TICK, PulseMath.kindFor(at(11, 20), s.copy(intervalMinutes = 20), zone))
    }

    @Test fun kinds_relativeEncodesElapsed() {
        val s = noQuiet.copy(intervalMinutes = 15, alignment = Alignment.RELATIVE, anchorMs = at(10, 7))
        assertEquals(PulseKind.QUARTER, PulseMath.kindFor(at(10, 22), s, zone))
        assertEquals(PulseKind.HALF, PulseMath.kindFor(at(10, 37), s, zone))
        assertEquals(PulseKind.HOUR, PulseMath.kindFor(at(11, 7), s, zone))
        val odd = s.copy(intervalMinutes = 25)
        assertEquals(PulseKind.INTERVAL, PulseMath.kindFor(at(10, 32), odd, zone))
    }
}
