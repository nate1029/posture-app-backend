package com.didi.neckposture.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PostureClassifierTest {
    @Test
    fun `keeps good until hold duration passes`() {
        val classifier = PostureClassifier(
            warningThresholdDeg = 8f,
            badThresholdDeg = 15f,
            holdMs = 5_000L,
        )

        val first = classifier.classify(deltaDeg = 10f, reliable = true, timestampMs = 1_000L)
        val second = classifier.classify(deltaDeg = 10f, reliable = true, timestampMs = 5_000L)
        val third = classifier.classify(deltaDeg = 10f, reliable = true, timestampMs = 6_100L)

        assertEquals(PostureState.GOOD, first)
        assertEquals(PostureState.GOOD, second)
        assertEquals(PostureState.WARNING, third)
    }

    @Test
    fun `becomes unreliable immediately when signal unreliable`() {
        val classifier = PostureClassifier()

        val state = classifier.classify(deltaDeg = 20f, reliable = false, timestampMs = 1_000L)

        assertEquals(PostureState.UNRELIABLE, state)
    }
}
