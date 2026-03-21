package com.didi.neckposture.fusion

import com.didi.neckposture.domain.HeadPoseSample
import com.didi.neckposture.domain.OrientationSample
import com.didi.neckposture.domain.PostureState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PostureFusionEngineTest {
    @Test
    fun `computes relative flexion using head minus phone pitch`() {
        val engine = PostureFusionEngine(alpha = 1.0f)
        engine.setCalibrationBaseline(0f)

        val reading = engine.fuse(
            head = HeadPoseSample(
                pitchDeg = 20f,
                yawDeg = 1f,
                rollDeg = -2f,
                confidence = 0.9f,
                timestampMs = 10_000L,
            ),
            orientation = OrientationSample(
                pitchDeg = 8f,
                rollDeg = 0f,
                motionScore = 0.1f,
                isReliable = true,
                timestampMs = 10_000L,
            ),
        )

        assertNotNull(reading)
        assertEquals(12f, reading!!.relativeFlexionDeg, 0.01f)
    }

    @Test
    fun `returns unreliable state under high motion`() {
        val engine = PostureFusionEngine(alpha = 1.0f, maxMotionScore = 0.5f)
        engine.setCalibrationBaseline(0f)

        val reading = engine.fuse(
            head = HeadPoseSample(
                pitchDeg = 30f,
                yawDeg = 0f,
                rollDeg = 0f,
                confidence = 0.9f,
                timestampMs = 2_000L,
            ),
            orientation = OrientationSample(
                pitchDeg = 5f,
                rollDeg = 0f,
                motionScore = 1.2f,
                isReliable = true,
                timestampMs = 2_000L,
            ),
        )

        assertEquals(PostureState.UNRELIABLE, reading!!.state)
    }
}
