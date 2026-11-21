package com.pulsetime.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pulsetime.PulseController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Alarms are wiped on reboot and become wrong when the clock or timezone changes.
 * These broadcasts are exempt from implicit-broadcast limits, so a manifest receiver is the
 * zero-cost way to re-arm the single alarm — nothing runs in between.
 */
class RescheduleReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" -> Unit
            else -> return
        }
        val appContext = context.applicationContext
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                PulseController.reschedule(appContext)
            } catch (t: Throwable) {
                Log.e("RescheduleReceiver", "Reschedule failed", t)
            } finally {
                pending.finish()
            }
        }
    }
}
