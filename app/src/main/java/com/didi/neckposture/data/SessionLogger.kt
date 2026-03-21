package com.didi.neckposture.data

import com.didi.neckposture.domain.PostureReading
import com.didi.neckposture.domain.PostureState
import com.didi.neckposture.domain.SessionMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SessionLogger {
    private val _metrics = MutableStateFlow(SessionMetrics())
    val metrics: StateFlow<SessionMetrics> = _metrics.asStateFlow()

    fun append(reading: PostureReading) {
        val current = _metrics.value
        val updated = when (reading.state) {
            PostureState.GOOD -> current.copy(
                totalSamples = current.totalSamples + 1,
                goodSamples = current.goodSamples + 1,
            )

            PostureState.WARNING -> current.copy(
                totalSamples = current.totalSamples + 1,
                warningSamples = current.warningSamples + 1,
            )

            PostureState.BAD -> current.copy(
                totalSamples = current.totalSamples + 1,
                badSamples = current.badSamples + 1,
            )

            PostureState.UNRELIABLE -> current.copy(
                totalSamples = current.totalSamples + 1,
                unreliableSamples = current.unreliableSamples + 1,
            )
        }
        _metrics.value = updated
    }
}
