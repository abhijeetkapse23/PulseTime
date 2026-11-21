package com.pulsetime.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pulsetime.PulseController
import com.pulsetime.data.PulseRepository
import com.pulsetime.haptics.HapticEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Wakes for one pulse: decide -> vibrate -> arm next alarm -> finish.
 * goAsync() keeps the broadcast (and its implicit wakelock) alive only for this short
 * suspend work — typically well under one second.
 */
class PulseAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != PulseScheduler.ACTION_PULSE) return
        val appContext = context.applicationContext
        val pending = goAsync()

        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val s = PulseRepository.get(appContext)
                if (!s.active) return@launch

                val now = System.currentTimeMillis()
                val fireAt = intent.getLongExtra(PulseScheduler.EXTRA_FIRE_AT, now)
                val zone = ZoneId.systemDefault()

                // A pulse that arrives very late (e.g. watch was off) would mislead the wearer
                // about the time, so it is dropped rather than played.
                val tooLate = now - fireAt > LATE_TOLERANCE_MS
                val muted = tooLate ||
                    s.isSnoozed(now) ||
                    PulseMath.isQuiet(fireAt, s, zone) || // safety net if settings changed
                    (s.autoPauseOffWrist && OffBodyDetector.isOffBody(appContext))

                if (!muted) {
                    HapticEngine.play(appContext, PulseMath.kindFor(fireAt, s, zone), s.intensity, s.microTaps)
                }

                // Arm the next alarm from max(now, fireAt) so an alarm delivered a hair early
                // can never re-schedule the same boundary.
                PulseController.reschedule(appContext, afterMs = maxOf(now, fireAt))
            } catch (t: Throwable) {
                Log.e(TAG, "Pulse handling failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "PulseAlarmReceiver"
        const val LATE_TOLERANCE_MS = 5 * 60_000L
    }
}
