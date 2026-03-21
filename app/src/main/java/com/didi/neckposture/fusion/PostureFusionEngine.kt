package com.didi.neckposture.fusion

import com.didi.neckposture.domain.HeadPoseSample
import com.didi.neckposture.domain.OrientationSample
import com.didi.neckposture.domain.PostureClassifier
import com.didi.neckposture.domain.PostureReading

class PostureFusionEngine(
    private val classifier: PostureClassifier = PostureClassifier(),
    private val alpha: Float = 0.25f,
    private val minConfidence: Float = 0.25f,
    private val maxMotionScore: Float = 1.6f,
) {
    private var baselineRelativeDeg: Float? = null
    private var smoothedRelativeDeg = 0f

    fun setCalibrationBaseline(relativeDeg: Float) {
        baselineRelativeDeg = relativeDeg
    }

    fun clearCalibration() {
        baselineRelativeDeg = null
    }

    fun isCalibrated(): Boolean = baselineRelativeDeg != null

    fun fuse(head: HeadPoseSample?, orientation: OrientationSample): PostureReading? {
        if (head == null) return null

        val relative = head.pitchDeg - orientation.pitchDeg
        smoothedRelativeDeg = if (smoothedRelativeDeg == 0f) {
            relative
        } else {
            alpha * relative + (1f - alpha) * smoothedRelativeDeg
        }

        val baseline = baselineRelativeDeg ?: smoothedRelativeDeg
        val delta = (smoothedRelativeDeg - baseline).coerceAtLeast(0f)

        val reliable = orientation.isReliable &&
            orientation.motionScore <= maxMotionScore &&
            head.confidence >= minConfidence
        val confidence = (head.confidence - (orientation.motionScore / 4f)).coerceIn(0f, 1f)
        val state = classifier.classify(deltaDeg = delta, reliable = reliable, timestampMs = head.timestampMs)

        return PostureReading(
            relativeFlexionDeg = delta,
            smoothedFlexionDeg = smoothedRelativeDeg,
            headPitchDeg = head.pitchDeg,
            headYawDeg = head.yawDeg,
            headRollDeg = head.rollDeg,
            phonePitchDeg = orientation.pitchDeg,
            confidence = confidence,
            state = state,
            timestampMs = head.timestampMs,
        )
    }
}
