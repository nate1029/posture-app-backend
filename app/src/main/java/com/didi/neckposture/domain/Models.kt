package com.didi.neckposture.domain

enum class PostureState {
    GOOD,
    WARNING,
    BAD,
    UNRELIABLE,
}

data class HeadPoseSample(
    val pitchDeg: Float,
    val yawDeg: Float,
    val rollDeg: Float,
    val confidence: Float,
    val timestampMs: Long,
)

data class OrientationSample(
    val pitchDeg: Float,
    val rollDeg: Float,
    val motionScore: Float,
    val isReliable: Boolean,
    val timestampMs: Long,
)

data class PostureReading(
    val relativeFlexionDeg: Float,
    val smoothedFlexionDeg: Float,
    val headPitchDeg: Float,
    val headYawDeg: Float,
    val headRollDeg: Float,
    val phonePitchDeg: Float,
    val confidence: Float,
    val state: PostureState,
    val timestampMs: Long,
)

data class SessionMetrics(
    val totalSamples: Int = 0,
    val goodSamples: Int = 0,
    val warningSamples: Int = 0,
    val badSamples: Int = 0,
    val unreliableSamples: Int = 0,
)
