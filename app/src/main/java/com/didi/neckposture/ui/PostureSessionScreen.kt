package com.didi.neckposture.ui

import android.annotation.SuppressLint
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.didi.neckposture.camera.CameraAnalyzer
import com.didi.neckposture.domain.HeadPoseSample
import com.didi.neckposture.pose.HeadPoseEstimator
import com.didi.neckposture.sensors.OrientationProvider
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@Composable
fun PostureSessionScreen(
    viewModel: PostureSessionViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val metrics by viewModel.metrics.collectAsState()

    val orientationProvider = remember {
        OrientationProvider(context) { sample ->
            viewModel.onOrientationSample(sample)
        }
    }

    DisposableEffect(Unit) {
        orientationProvider.start()
        onDispose {
            orientationProvider.stop()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CameraPreview(
            onHeadPose = viewModel::onHeadSample,
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp),
        )

        Text(
            text = "State: ${uiState.latestReading?.state ?: "NO_FACE"}",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(text = "Flexion delta: ${"%.1f".format(uiState.latestReading?.relativeFlexionDeg ?: 0f)} deg")
        Text(
            text = "Head pose p/y/r: ${
                "%.1f".format(uiState.latestReading?.headPitchDeg ?: 0f)
            } / ${
                "%.1f".format(uiState.latestReading?.headYawDeg ?: 0f)
            } / ${
                "%.1f".format(uiState.latestReading?.headRollDeg ?: 0f)
            }",
        )
        Text(text = "Phone pitch: ${"%.1f".format(uiState.latestReading?.phonePitchDeg ?: 0f)} deg")
        Text(text = "Confidence: ${"%.2f".format(uiState.latestReading?.confidence ?: 0f)}")

        if (uiState.calibrationInProgress) {
            Text(text = "Calibrating: ${(uiState.calibrationProgress * 100).toInt()}%")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { if (uiState.running) viewModel.stopSession() else viewModel.startSession() }) {
                Text(if (uiState.running) "Stop Session" else "Start Session")
            }
            Button(onClick = viewModel::calibrate, enabled = !uiState.calibrationInProgress) {
                Text("Calibrate (10s)")
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Haptic")
            Switch(checked = uiState.hapticEnabled, onCheckedChange = viewModel::setHapticEnabled)
            Text("Sound")
            Switch(checked = uiState.soundEnabled, onCheckedChange = viewModel::setSoundEnabled)
        }

        Text("Session samples: ${metrics.totalSamples}")
        Text("Good: ${metrics.goodSamples}  Warning: ${metrics.warningSamples}  Bad: ${metrics.badSamples}")
        Text("Unreliable: ${metrics.unreliableSamples}")
    }
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
private fun CameraPreview(
    onHeadPose: (HeadPoseSample?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    AndroidView(
        modifier = modifier,
        factory = {
            val previewView = PreviewView(context)
            bindCamera(previewView, cameraExecutor, onHeadPose)
            previewView
        },
    )
}

private fun bindCamera(
    previewView: PreviewView,
    analyzerExecutor: ExecutorService,
    onHeadPose: (HeadPoseSample?) -> Unit,
) {
    val context = previewView.context
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener(
        {
            val cameraProvider = cameraProviderFuture.get()
            val preview = androidx.camera.core.Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val estimator = HeadPoseEstimator()
            val analyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(
                        analyzerExecutor,
                        CameraAnalyzer(estimator, onHeadPose),
                    )
                }
            val selector = CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build()

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                context as androidx.lifecycle.LifecycleOwner,
                selector,
                preview,
                analyzer,
            )
        },
        ContextCompat.getMainExecutor(context),
    )
}
