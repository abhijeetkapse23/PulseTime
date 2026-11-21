package com.pulsetime.schedule

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.pulsetime.data.PulseSettings
import java.time.ZoneId

/**
 * Owns the single pending alarm.
 *
 * Battery design:
 *  - Exactly ONE alarm is ever armed. No foreground service, no Handler/coroutine loop.
 *    Between pulses the app process can be fully killed; the system wakes us only at the
 *    exact boundary via AlarmManager.
 *  - setExactAndAllowWhileIdle() is used so pulses still land in Doze. The system rate-limits
 *    these (~one per 9 min per app in deep Doze), which is fine for >= 15-min intervals; very
 *    short custom intervals may be delivered slightly late while the watch is idle.
 *  - Quiet Hours & Snooze are skipped at scheduling time (see PulseMath), so there are
 *    no silent wake-ups overnight.
 */
object PulseScheduler {

    const val ACTION_PULSE = "com.pulsetime.action.PULSE"
    const val EXTRA_FIRE_AT = "fire_at"
    private const val REQUEST_CODE = 1001

    /** Arms the next alarm strictly after [afterMs]. Returns its epoch ms. */
    fun scheduleNext(context: Context, s: PulseSettings, afterMs: Long = System.currentTimeMillis()): Long {
        val fireAt = PulseMath.nextPulse(afterMs, s, ZoneId.systemDefault())
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pendingIntent(context, fireAt)
        if (canScheduleExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
        } else {
            // Graceful fallback if the user revoked exact alarms (API 31-32): inexact but
            // still Doze-aware. The UI shows a chip to re-grant the permission.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pi)
        }
        return fireAt
    }

    fun cancel(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(pendingIntent(context, 0L))
    }

    fun canScheduleExact(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    fun isQuietNow(s: PulseSettings, atMs: Long = System.currentTimeMillis()): Boolean =
        PulseMath.isQuiet(atMs, s, ZoneId.systemDefault())

    private fun pendingIntent(context: Context, fireAt: Long): PendingIntent {
        // Same request code + FLAG_UPDATE_CURRENT => re-arming replaces, never stacks alarms.
        val intent = Intent(context, PulseAlarmReceiver::class.java)
            .setAction(ACTION_PULSE)
            .putExtra(EXTRA_FIRE_AT, fireAt)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
