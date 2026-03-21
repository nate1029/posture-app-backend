package com.didi.neckposture.domain

class PostureClassifier(
    private val warningThresholdDeg: Float = 8f,
    private val badThresholdDeg: Float = 15f,
    private val holdMs: Long = 5_000L,
) {
    private var pendingState: PostureState = PostureState.GOOD
    private var pendingSince: Long = 0L
    private var currentState: PostureState = PostureState.GOOD

    fun classify(deltaDeg: Float, reliable: Boolean, timestampMs: Long): PostureState {
        if (!reliable) {
            currentState = PostureState.UNRELIABLE
            pendingState = PostureState.UNRELIABLE
            pendingSince = timestampMs
            return currentState
        }

        val target = when {
            deltaDeg > badThresholdDeg -> PostureState.BAD
            deltaDeg > warningThresholdDeg -> PostureState.WARNING
            else -> PostureState.GOOD
        }

        if (target == currentState) {
            pendingState = target
            pendingSince = timestampMs
            return currentState
        }

        if (target != pendingState) {
            pendingState = target
            pendingSince = timestampMs
            return currentState
        }

        if ((timestampMs - pendingSince) >= holdMs) {
            currentState = target
        }
        return currentState
    }
}
