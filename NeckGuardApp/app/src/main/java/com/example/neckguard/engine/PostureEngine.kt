package com.example.neckguard.engine

import android.util.Log
import com.example.neckguard.RemoteConfigManager
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * Pure Kotlin implementation of the NudgeUp sensory heuristics.
 * Handles the physics, math, and motion rejection without any OS dependencies.
 */
object PostureEngine {
    private const val TAG = "PostureEngine"
    private const val GRAVITY = 9.81f
    private const val MOTION_THRESHOLD = 3.0f
    private const val NECK_SCALE = 0.82f
    private const val ALPHA = 0.96f // Complementary filter constant

    // @Volatile because [currentPitch] is written on the sensor thread and
    // read by NeckGuardService.sendPostureAlert() which sometimes runs on a
    // different thread (e.g. via the alarm-restart path). Without the
    // happens-before edge, ARM can return a stale value to readers on
    // another core.
    @Volatile var currentPitch: Float = 90f // Start assuming phone is flat on desk
    private var lastTimestampNS: Long = 0L

    enum class PostureState {
        GOOD, MODERATE, POOR, IDLE, UNKNOWN
    }

    /**
     * Processes a matched set of Gyro+Accel values (simulated or real).
     * @param ax, ay, az Accelerometer data in m/s2
     * @param gx, gy, gz Gyroscope data in rad/s
     * @param timestampNS Event timestamp in nanoseconds
     * @param surfaceRotation Android Surface rotation mapping
     * @return The classified posture state
     */
    fun processSensorTick(
        ax: Float, ay: Float, az: Float,
        gx: Float, gy: Float, gz: Float,
        timestampNS: Long,
        surfaceRotation: Int
    ): PostureState {
        // 1. Motion Spike Detection
        val magnitude = sqrt((ax * ax + ay * ay + az * az).toDouble()).toFloat()
        val motionNoise = abs(magnitude - GRAVITY)

        if (motionNoise > MOTION_THRESHOLD) {
            // Drop reading if they are walking or shaking the phone
            return PostureState.UNKNOWN
        }

        // 1.5 Lying in bed / Screen facing down rejection
        // Z-axis points out from the screen. If acceleration on Z is strongly negative,
        // the screen is facing the floor (e.g. user lying on back holding phone above face).
        if (az < -2.0f) {
            currentPitch = 90f // Reset filter to prevent weird jumps when sitting back up
            // Also clear the timestamp so the very next non-face-down sample
            // re-seeds the complementary filter from accelPitch directly,
            // instead of integrating a multi-second `dt * gyro` term that
            // would produce a wild pitch and a spurious POOR/GOOD reading.
            lastTimestampNS = 0L
            return PostureState.IDLE
        }

        // 2. Landscape Axis Compensation
        val pureGravityY: Float
        val pureGravityZ = az // Consistent for screen-depth
        val gyroX: Float

        when (surfaceRotation) {
            android.view.Surface.ROTATION_90 -> {
                pureGravityY = ax
                gyroX = -gy
            }
            android.view.Surface.ROTATION_270 -> {
                pureGravityY = -ax
                gyroX = gy
            }
            android.view.Surface.ROTATION_180 -> {
                pureGravityY = -ay
                gyroX = -gx
            }
            else -> {
                // ROTATION_0 (Portrait): positive Y is UP along the screen.
                // Accelerometer pushes UP against gravity, so ay is +9.81 when standing upright.
                pureGravityY = ay
                gyroX = gx
            }
        }

        // 3. Accelerometer signed pitch
        // atan2 mathematically correctly distinguishes leaning FORWARD (positive Z) vs leaning BACKWARD (negative Z)
        val accelPitch = Math.toDegrees(kotlin.math.atan2(pureGravityZ.toDouble(), pureGravityY.toDouble())).toFloat()

        // 4. Complementary Filter (Gyro + Accel)
        if (lastTimestampNS != 0L) {
            val dt = (timestampNS - lastTimestampNS) * 1.0f / 1_000_000_000.0f
            // Gyroscope is rad/s.
            val gyroDegrees = Math.toDegrees(gyroX.toDouble()).toFloat()
            currentPitch = ALPHA * (currentPitch + gyroDegrees * dt) + (1.0f - ALPHA) * accelPitch
        } else {
            currentPitch = accelPitch // Seed initial value
        }
        lastTimestampNS = timestampNS
        
        if (currentPitch.isNaN()) {
            currentPitch = accelPitch
        }

        // Safety bound: allow negative (leaning back) but cap extremes
        currentPitch = currentPitch.coerceIn(-90f, 90f)

        // 5. Physiological Neck Mapping
        // Floor negative pitch to 0. Leaning backward is 0 flexion (perfect posture).
        val neckFlexion = if (currentPitch < 0) 0f else currentPitch * NECK_SCALE

        // 6. Classification — moderate/poor boundary is configurable via Remote Config
        val slouchThreshold = RemoteConfigManager.slouchAngleThreshold
        return when {
            currentPitch > 75f        -> PostureState.IDLE // Phone resting flat
            neckFlexion < 15f         -> PostureState.GOOD
            neckFlexion < slouchThreshold -> PostureState.MODERATE
            else                      -> PostureState.POOR
        }
    }
    
    fun resetFilter() {
        lastTimestampNS = 0L
    }
}
