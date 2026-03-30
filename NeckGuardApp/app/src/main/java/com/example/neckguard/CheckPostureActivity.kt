package com.example.neckguard

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * PHASE 3: The Transparent Workaround Activity
 * Opens invisibly, fires the CameraX pipeline, reads head pitch from MLKit, and closes.
 */
class CheckPostureActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private var hasResult = false

    companion object {
        private const val TAG = "CheckPostureAct"
        private const val ALERT_NOTIFICATION_ID = 202
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Invisible Checking Activity Opened")

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            fireFallbackNotification("Camera permission missing.")
            finish()
            return
        }

        cameraExecutor = Executors.newSingleThreadExecutor()
        startCamera()
        
        // Timeout fallback string so the camera doesn't run forever if no face is detected
        window.decorView.postDelayed({
            if (!hasResult) {
                hasResult = true
                fireFallbackNotification("Could not find a face in time.")
                finish()
            }
        }, 5000)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

                // Setup Google ML Kit Face Detector (Runs completely offline!)
                val options = FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .build()
                val detector = FaceDetection.getClient(options)

                // Setup CameraX Image Analysis Stream
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            processImageProxy(detector, imageProxy)
                        }
                    }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, imageAnalyzer
                )

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
                fireFallbackNotification("Camera hardware initialization failed.")
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(detector: com.google.mlkit.vision.face.FaceDetector, imageProxy: ImageProxy) {
        if (hasResult) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            
            detector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty() && !hasResult) {
                        hasResult = true
                        val face = faces[0]
                        val pitch = face.headEulerAngleX // Negative means looking DOWN at the phone
                        
                        Log.d(TAG, "3D Triangulation Output -> Face Pitch: \$pitch")
                        
                        // Mapping the 3D Face output back into the Android Notification
                        val message = when {
                            pitch < -12f -> "You're tilted downwards (\$pitch°). Please lift your screen."
                            pitch < -4f -> "Your neck posture is slightly slouched (\$pitch°)."
                            else -> "Posture looks great! Keep it up."
                        }
                        
                        fireResultNotification(message)
                        finish()
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Face detection failed", e)
                }
                .addOnCompleteListener {
                    imageProxy.close() // Mandatory memory release
                }
        } else {
            imageProxy.close()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
    }

    private fun fireFallbackNotification(reason: String) {
        if (!hasResult) {
            fireResultNotification("Posture check failed (\$reason). Based on the phone sensors, your neck may still be strained.")
            hasResult = true
        }
    }

    private fun fireResultNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)
        
        // BigTextStyle ensures you can drag it down and read the whole message!
        val style = NotificationCompat.BigTextStyle().bigText(text)
        
        val builder = NotificationCompat.Builder(this, "neckguard_alert_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Posture Result")
            .setContentText(text) // For collapsed view
            .setStyle(style) // For expanded view
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        manager.notify(ALERT_NOTIFICATION_ID, builder.build())
    }
}
