package com.pulsetime.schedule

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Off-wrist check using TYPE_LOW_LATENCY_OFFBODY_DETECT (no runtime permission needed).
 *
 * Battery note: we never keep a listener registered. At pulse time we register, take the
 * first (current-state) event the on-change sensor reports, and unregister — a few hundred
 * milliseconds of sensor time per pulse. If the sensor is missing or silent we assume
 * "on wrist" so pulses are never lost by mistake.
 */
object OffBodyDetector {

    private const val TIMEOUT_MS = 600L

    fun isSupported(context: Context): Boolean = sensor(context) != null

    private fun sensorManager(context: Context): SensorManager? =
        context.getSystemService(SensorManager::class.java)

    private fun sensor(context: Context): Sensor? =
        sensorManager(context)?.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT, true)
            ?: sensorManager(context)?.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT)

    suspend fun isOffBody(context: Context): Boolean {
        val sm = sensorManager(context) ?: return false
        val sensor = sensor(context) ?: return false
        return withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        sm.unregisterListener(this)
                        // 1.0 = on body, 0.0 = off body
                        if (cont.isActive) cont.resume(event.values.firstOrNull() == 0f)
                    }

                    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                }
                sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
                cont.invokeOnCancellation { sm.unregisterListener(listener) }
            }
        } ?: false
    }
}
