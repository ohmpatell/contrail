package com.contrail.contrail.feature.sky

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/** A direction in the world: east, north, up. */
data class Vec3(val e: Double, val n: Double, val u: Double) {
    operator fun plus(o: Vec3) = Vec3(e + o.e, n + o.n, u + o.u)
    operator fun times(k: Double) = Vec3(e * k, n * k, u * k)
    infix fun dot(o: Vec3) = e * o.e + n * o.n + u * o.u
    fun normalized(): Vec3 { val l = sqrt(this dot this).takeIf { it > 1e-9 } ?: 1.0; return Vec3(e / l, n / l, u / l) }
}

/**
 * Where the phone points, from the rotation-vector sensor: the back camera's line of sight,
 * the screen's right and up edges, all in world east/north/up. Smoothed so labels glide.
 */
@Stable
class DeviceOrientation {
    var available by mutableStateOf(true)
    /** Out of the back camera. */
    var forward by mutableStateOf(Vec3(0.0, 1.0, 0.0))
    var right by mutableStateOf(Vec3(1.0, 0.0, 0.0))
    var up by mutableStateOf(Vec3(0.0, 0.0, 1.0))

    /**
     * Compass heading the phone faces: where the camera points when you hold it up, or
     * where the top edge points when it lies flat.
     */
    val headingDeg: Float
        get() {
            val v = if (abs(forward.u) < 0.8) forward else up
            return ((Math.toDegrees(atan2(v.e, v.n)) + 360) % 360).toFloat()
        }

    internal fun update(r: FloatArray, smoothing: Double) {
        // Rotation matrix columns are the device axes in world coordinates (x east, y north, z up).
        val x = Vec3(r[0].toDouble(), r[3].toDouble(), r[6].toDouble())
        val y = Vec3(r[1].toDouble(), r[4].toDouble(), r[7].toDouble())
        val z = Vec3(-r[2].toDouble(), -r[5].toDouble(), -r[8].toDouble())
        val k = smoothing
        forward = (forward * (1 - k) + z * k).normalized()
        right = (right * (1 - k) + x * k).normalized()
        up = (up * (1 - k) + y * k).normalized()
    }
}

@Composable
fun rememberDeviceOrientation(enabled: Boolean = true): DeviceOrientation {
    val context = LocalContext.current
    val state = remember { DeviceOrientation() }
    DisposableEffect(enabled) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) ?: sm?.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
        if (!enabled || sm == null || sensor == null) {
            state.available = sensor != null
            return@DisposableEffect onDispose { }
        }
        val matrix = FloatArray(9)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                state.update(matrix, 0.18)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }
    return state
}
