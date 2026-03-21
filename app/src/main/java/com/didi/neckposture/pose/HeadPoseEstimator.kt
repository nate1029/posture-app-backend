package com.didi.neckposture.pose
import androidx.camera.core.ImageProxy
import com.didi.neckposture.domain.HeadPoseSample
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlin.math.max

class HeadPoseEstimator {
    private val detectorOptions = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
        .enableTracking()
        .build()

    private val detector = FaceDetection.getClient(detectorOptions)
    fun estimate(imageProxy: ImageProxy, timestampMs: Long): HeadPoseSample? {
        val mediaImage = imageProxy.image ?: return null
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        val result = Tasks.await(detector.process(image))
        val face = result.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() } ?: return null

        val frameArea = imageProxy.width * imageProxy.height
        val faceArea = face.boundingBox.width() * face.boundingBox.height()
        val areaConfidence = if (frameArea > 0) faceArea.toFloat() / frameArea.toFloat() else 0f
        val confidence = max(0.2f, areaConfidence.coerceIn(0f, 1f))

        return HeadPoseSample(
            pitchDeg = face.headEulerAngleX,
            yawDeg = face.headEulerAngleY,
            rollDeg = face.headEulerAngleZ,
            confidence = confidence,
            timestampMs = timestampMs,
        )
    }
}
