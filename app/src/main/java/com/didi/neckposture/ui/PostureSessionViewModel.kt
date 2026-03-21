package com.didi.neckposture.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.didi.neckposture.data.SessionLogger
import com.didi.neckposture.domain.HeadPoseSample
import com.didi.neckposture.domain.OrientationSample
import com.didi.neckposture.domain.PostureReading
import com.didi.neckposture.feedback.FeedbackController
import com.didi.neckposture.fusion.PostureFusionEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PostureUiState(
    val running: Boolean = false,
    val latestReading: PostureReading? = null,
    val hapticEnabled: Boolean = true,
    val soundEnabled: Boolean = false,
    val calibrationInProgress: Boolean = false,
    val calibrationProgress: Float = 0f,
)

class PostureSessionViewModel(application: Application) : AndroidViewModel(application) {
    private val fusionEngine = PostureFusionEngine()
    private val logger = SessionLogger()
    private val feedbackController = FeedbackController(application)

    private val _uiState = MutableStateFlow(PostureUiState())
    val uiState: StateFlow<PostureUiState> = _uiState.asStateFlow()
    val metrics = logger.metrics

    private var latestHead: HeadPoseSample? = null
    private var latestOrientation: OrientationSample? = null
    private var loopStarted = false

    fun startSession() {
        _uiState.update { it.copy(running = true) }
        if (!loopStarted) {
            loopStarted = true
            startFusionLoop()
        }
    }

    fun stopSession() {
        _uiState.update { it.copy(running = false) }
    }

    fun onHeadSample(sample: HeadPoseSample?) {
        latestHead = sample
    }

    fun onOrientationSample(sample: OrientationSample) {
        latestOrientation = sample
    }

    fun setHapticEnabled(enabled: Boolean) {
        _uiState.update { it.copy(hapticEnabled = enabled) }
    }

    fun setSoundEnabled(enabled: Boolean) {
        _uiState.update { it.copy(soundEnabled = enabled) }
    }

    fun calibrate() {
        viewModelScope.launch {
            _uiState.update { it.copy(calibrationInProgress = true, calibrationProgress = 0f) }
            val snapshots = mutableListOf<Float>()
            val steps = 10
            repeat(steps) { idx ->
                val head = latestHead
                val orientation = latestOrientation
                if (head != null && orientation != null) {
                    snapshots.add(head.pitchDeg - orientation.pitchDeg)
                }
                _uiState.update { it.copy(calibrationProgress = (idx + 1) / steps.toFloat()) }
                delay(1_000L)
            }
            if (snapshots.isNotEmpty()) {
                fusionEngine.setCalibrationBaseline(snapshots.average().toFloat())
            }
            _uiState.update { it.copy(calibrationInProgress = false, calibrationProgress = 0f) }
        }
    }

    private fun startFusionLoop() {
        viewModelScope.launch {
            while (true) {
                if (_uiState.value.running) {
                    val orientation = latestOrientation
                    if (orientation != null) {
                        val reading = fusionEngine.fuse(latestHead, orientation)
                        if (reading != null) {
                            logger.append(reading)
                            _uiState.update { it.copy(latestReading = reading) }
                            feedbackController.triggerIfNeeded(
                                state = reading.state,
                                hapticEnabled = _uiState.value.hapticEnabled,
                                soundEnabled = _uiState.value.soundEnabled,
                            )
                        }
                    }
                }
                delay(1_000L)
            }
        }
    }
}
