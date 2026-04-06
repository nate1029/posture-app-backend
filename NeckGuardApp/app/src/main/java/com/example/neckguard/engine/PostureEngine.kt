package com.example.neckguard.engine

import android.util.Log
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Pure Kotlin implementation of the NeckGuard sensory heuristics.
 * Handles the physics, math, and motion rejection without any OS dependencies.
 */
object PostureEngine {
    private const val TAG = "PostureEngine"
    private const val GRAVITY = 9.81f
    private const val MOTION_THRESHOLD = 3.0f
    private const val NECK_SCALE = 0.82f
    private const val ALPHA = 0.96f // Complementary filter constant

    var currentPitch: Float = 90f // Start assuming phone is flat on desk
    private var lastTimestampNS: Long = 0L

    enum class PostureState {
        GOOD, MODERATE, POOR, IDLE, UNKNOWN
    }

    /**
     * Processes a matched set of Gyro+Accel values (simulated or real).
     * @param ax, ay, az Accelerometer data in m/s2
     * @param gx, gy, gz Gyroscope data in rad/s
     * @param timestampNS Event timestamp in nanoseconds
     * @param isLandscape Whether the display is currently rotated
     * @return The classified posture state
     */
    fun processSensorTick(
        ax: Float, ay: Float, az: Float,
        gx: Float, gy: Float, gz: Float,
        timestampNS: Long,
        isLandscape: Boolean
    ): PostureState {
        // 1. Motion Spike Detection
        val magnitude = sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat()
        val motionNoise = abs(magnitude - GRAVITY)

        if (motionNoise > MOTION_THRESHOLD) {
            // Drop reading if they are walking or shaking the phone
            return PostureState.UNKNOWN
        }

        // 2. Landscape Axis Compensation
        // Android's Accelerometer reports +9.81 on Y when upright, but Python script assumed -9.81.
        // We invert the incoming ay to match the Pure Gravity vector format expected by Python.
        val pureGravityY = if (isLandscape) ax else -ay
        val gyroX = if (isLandscape) gy else gx

        // 3. Accelerometer absolute pitch (EXACT neck_posture_sensor.py implementation)
        // Python: cos_angle = max(-1.0, min(1.0, -ay / magnitude))
        //         tilt = math.degrees(math.acos(cos_angle))
        val safeMag = if (magnitude < 0.5f) 0.5f else magnitude
        val cosAngle = (-pureGravityY / safeMag).coerceIn(-1.0f, 1.0f)
        val accelPitch = Math.toDegrees(kotlin.math.acos(cosAngle.toDouble())).toFloat()

        // 4. Complementary Filter (Gyro + Accel)
        if (lastTimestampNS != 0L) {
            val dt = (timestampNS - lastTimestampNS) * 1.0f / 1_000_000_000.0f
            // In Android, tilting the phone backwards (top away from user) yields a NEGATIVE gx.
            // But accelPitch increases (0 to 90) when tilted backwards. 
            // We must invert gx to prevent sensory fighting.
            val gyroDegrees = Math.toDegrees(-gyroX.toDouble()).toFloat()
            currentPitch = ALPHA * (currentPitch + gyroDegrees * dt) + (1.0f - ALPHA) * accelPitch
        } else {
            currentPitch = accelPitch // Seed initial value
        }
        lastTimestampNS = timestampNS
        
        // Safety bound
        currentPitch = currentPitch.coerceIn(0f, 90f)

        // 5. Physiological Neck Mapping
        val neckFlexion = currentPitch * NECK_SCALE

        // 6. Classification
        return when {
            currentPitch > 75f -> PostureState.IDLE // Phone resting flat
            neckFlexion < 15f -> PostureState.GOOD
            neckFlexion < 35f -> PostureState.MODERATE
            else -> PostureState.POOR
        }
    }
    
    fun resetFilter() {
        lastTimestampNS = 0L
    }
}
