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
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * PHASE 3: The Transparent Workaround Activity
 * Opens invisibly, fires the CameraX pipeline, reads head pitch from MLKit, and closes.
 */
class CheckPostureActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private var hasResult = java.util.concurrent.atomic.AtomicBoolean(false)

    // Single in-flight detector job at a time. ML Kit explicitly states "do
    // not call process() on a previous task that has not yet completed";
    // CameraX's STRATEGY_KEEP_ONLY_LATEST mostly protects us, but we can
    // still race during the first frame before back-pressure kicks in.
    // (B-09)
    private val detectorBusy = java.util.concurrent.atomic.AtomicBoolean(false)

    // Held as a field so we can close() it in onDestroy. Previously it was a local
    // variable inside startCamera() which meant the native TFLite model and detector
    // native memory leaked until the process died.
    private var faceDetector: FaceDetector? = null
    private var cameraProvider: ProcessCameraProvider? = null

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
        lifecycleScope.launch {
            delay(5000)
            if (hasResult.compareAndSet(false, true)) {
                fireFallbackNotification("Could not find a face in time.")
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                val provider: ProcessCameraProvider = cameraProviderFuture.get()
                cameraProvider = provider

                val options = FaceDetectorOptions.Builder()
                    .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                    .build()
                val detector = FaceDetection.getClient(options)
                faceDetector = detector

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor) { imageProxy ->
                            processImageProxy(detector, imageProxy)
                        }
                    }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                provider.unbindAll()
                provider.bindToLifecycle(
                    this, cameraSelector, imageAnalyzer
                )

            } catch (exc: Exception) {
                // Route through LogX so the REAL bind exception reaches Crashlytics
                // on Play builds (raw Log.e only lands in logcat, invisible to us).
                LogX.e(TAG, "Camera bind failed — falling back to sensors", exc)
                if (hasResult.compareAndSet(false, true)) {
                    fireFallbackNotification("Camera hardware initialization failed.")
                    finish()
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processImageProxy(detector: FaceDetector, imageProxy: ImageProxy) {
        // Bail if we've already produced a result, the activity is finishing,
        // or the previous detector task is still in flight. Each of these
        // cases is an immediate close-and-return so back-pressure isn't
        // implicitly held in ML Kit's task queue. (B-09 + B-10)
        if (hasResult.get() || isFinishing || isDestroyed) {
            imageProxy.close()
            return
        }
        if (!detectorBusy.compareAndSet(false, true)) {
            // Another frame is still being processed; drop this one.
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            detectorBusy.set(false)
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        detector.process(image)
            .addOnSuccessListener { faces ->
                // The Activity may have torn down between submission and
                // callback — bail out before we touch any system service.
                // (B-10)
                if (isFinishing || isDestroyed || hasResult.get()) return@addOnSuccessListener
                if (faces.isEmpty() || !hasResult.compareAndSet(false, true)) return@addOnSuccessListener

                val face = faces[0]
                // In Android ML Kit, EulerX is POSITIVE for chin UP, NEGATIVE for chin DOWN.
                // When you look down at a phone you flex your neck forward. We want that to
                // be a positive addition.
                val facePitchExtracted = -face.headEulerAngleX
                val faceYaw = face.headEulerAngleY
                val faceRoll = face.headEulerAngleZ

                if (kotlin.math.abs(faceYaw) > 25f || kotlin.math.abs(faceRoll) > 25f) {
                    val message = "Asymmetric Posture Detected: Your head is tilted or turned sideways. Please straighten your neck to avoid uneven strain."
                    fireResultNotification(message)
                    finish()
                    return@addOnSuccessListener
                }
                // Use LIVE sensor pitch (not the stale value from when the
                // notification was created, which may be minutes old).
                val livePitch = com.example.neckguard.engine.PostureEngine.currentPitch

                // Apply the SAME baseline subtraction and scaling as PostureEngine
                // so both systems agree on what "good" and "bad" mean.
                val baselinePitch = 10f // Must match PostureEngine.BASELINE_PITCH
                val sensorFlexion = ((livePitch - baselinePitch).coerceAtLeast(0f)) * 0.85f

                // The face camera gives us real data the sensor doesn't have.
                // Add 30% of the face pitch as a small refinement — enough to
                // catch genuine looking-down posture, but not so much that it
                // dominates and contradicts the sensor engine.
                val trueNeckPitch = sensorFlexion + (facePitchExtracted * 0.3f)

                if (com.example.neckguard.BuildConfig.DEBUG) {
                    Log.d(TAG, "Unified Math -> LivePitch: ${String.format("%.1f", livePitch)}° | SensorFlexion: ${String.format("%.1f", sensorFlexion)}° | FacePitch: ${String.format("%.1f", facePitchExtracted)}° | TrueNeck: ${String.format("%.1f", trueNeckPitch)}°")
                }

                val prefs = SecurePrefs.get(this)
                val vibe = prefs.getString("UserVibe", "") ?: ""
                val message = generateNotificationMessage(vibe, trueNeckPitch)

                // Track manual check
                val total = prefs.getInt("ManualChecksTotalToday", 0) + 1
                val bad = prefs.getInt("ManualChecksBadToday", 0) + if (trueNeckPitch > 15f) 1 else 0
                prefs.edit()
                    .putInt("ManualChecksTotalToday", total)
                    .putInt("ManualChecksBadToday", bad)
                    .apply()

                fireResultNotification(message, trueNeckPitch)
                finish()
            }
            .addOnFailureListener { e ->
                if (isFinishing || isDestroyed) return@addOnFailureListener
                Log.e(TAG, "Face detection failed", e)
            }
            .addOnCompleteListener {
                // Always release the busy flag and the proxy, regardless of
                // success / failure / activity-tearing-down. ML Kit
                // guarantees this listener is called exactly once per task,
                // so the AtomicBoolean cannot get stuck set. (B-11)
                detectorBusy.set(false)
                imageProxy.close()
            }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Free native resources. ML Kit's FaceDetector holds a TFLite interpreter
        // and a Play Services connection; CameraX holds camera device handles.
        try { cameraProvider?.unbindAll() } catch (_: Throwable) {}
        cameraProvider = null
        try { faceDetector?.close() } catch (_: Throwable) {}
        faceDetector = null
        if (::cameraExecutor.isInitialized) {
            cameraExecutor.shutdown()
        }
    }

    private fun fireFallbackNotification(reason: String) {
        Log.d(TAG, "Camera path unavailable ($reason) — falling back to sensor pitch")
        // The camera is only a refinement; the accel/gyro sensors are the primary
        // posture signal and are what triggered this check. So when the camera
        // can't give us a face reading, fall back to the live sensor pitch —
        // this still surfaces the tilt/kg data and the "Why this happened?"
        // button whenever posture is actually bad, instead of a dead error.
        val livePitch = com.example.neckguard.engine.PostureEngine.currentPitch
        val baselinePitch = 10f // Must match PostureEngine.BASELINE_PITCH
        val sensorFlexion = ((livePitch - baselinePitch).coerceAtLeast(0f)) * 0.85f

        val prefs = SecurePrefs.get(this)
        val vibe = prefs.getString("UserVibe", "") ?: ""
        val message = generateNotificationMessage(vibe, sensorFlexion)

        fireResultNotification(message, sensorFlexion)
    }

    private fun fireResultNotification(text: String, neckPitch: Float = 0f) {
        val manager = getSystemService(NotificationManager::class.java)
        
        val isBadPosture = neckPitch > 15f
        
        // Spinal load estimate (Hansraj 2014)
        val spinalLoadKg = when {
            neckPitch < 15f -> 12
            neckPitch < 25f -> 18
            neckPitch < 35f -> 22
            neckPitch < 45f -> 27
            else            -> 32
        }

        // Incorporate scientific data directly in the result notification
        val scientificText = if (isBadPosture) {
            "$text\n\nSensors picked up a ~${neckPitch.toInt()}° neck tilt. That's ~${spinalLoadKg}kg on your cervical spine!"
        } else {
            text
        }

        val style = NotificationCompat.BigTextStyle().bigText(scientificText)

        val postureState = when {
            neckPitch > 35f -> "POOR"
            neckPitch > 15f -> "MODERATE"
            else            -> "GOOD"
        }

        // Main tap -> opens dashboard
        val mainIntent = android.content.Intent(this, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = android.app.PendingIntent.getActivity(
            this, 1002, mainIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, "neckguard_alert_channel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Posture Result")
            .setContentText(scientificText)
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(mainPendingIntent)
            .setTimeoutAfter(RESULT_NOTIFICATION_TIMEOUT_MS)

        if (isBadPosture) {
            // Bad posture -> "Why this happened?" -> PostureInsightActivity
            val insightIntent = android.content.Intent(this, PostureInsightActivity::class.java).apply {
                putExtra("neck_pitch", neckPitch)
                putExtra("spinal_load_kg", spinalLoadKg)
                putExtra("posture_state", postureState)
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val insightPendingIntent = android.app.PendingIntent.getActivity(
                this, 1003, insightIntent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(0, "Why this happened? \uD83D\uDD0D", insightPendingIntent)
        } else {
            // Good posture -> exercise CTA (keep existing behavior)
            val prefs = SecurePrefs.get(this)
            val repository = com.example.neckguard.data.UserRepository(prefs)
            val assignedList = repository.assignedExercisesList
            val completedList = repository.completedExercisesTodayList
            val targetExercise = assignedList.firstOrNull { !completedList.contains(it) }
                ?: assignedList.firstOrNull() ?: "Chin Tuck"

            val exerciseIntent = android.content.Intent(this, MainActivity::class.java).apply {
                action = "OPEN_EXERCISE"
                putExtra("exercise_name", targetExercise)
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val exercisePendingIntent = android.app.PendingIntent.getActivity(
                this, 1001, exerciseIntent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(0, "Try $targetExercise \uD83D\uDCAA", exercisePendingIntent)
        }

        manager.notify(ALERT_NOTIFICATION_ID, builder.build())
    }

    private fun generateNotificationMessage(vibe: String, pitch: Float): String {
        val type = when {
            pitch > 35f -> "high_risk"
            pitch > 15f -> "moderate"
            else -> "great"
        }

        // ── Merged pool — all vibes blended for maximum variety ──────
        val messages = when (type) {
            "high_risk", "moderate" -> NotificationPool.badPostureMessages
            else -> NotificationPool.goodPostureMessages
        }

        // ── Avoid back-to-back repeats ──────────────────────────────
        val prefKey = "lastResultMsgIdx_$type"
        val prefs = getSharedPreferences("NeckGuardPrefs", MODE_PRIVATE)
        val lastIdx = prefs.getInt(prefKey, -1)
        var idx = messages.indices.random()
        while (idx == lastIdx && messages.size > 1) idx = messages.indices.random()
        prefs.edit().putInt(prefKey, idx).apply()

        return messages[idx]
    }

    companion object {
        private const val TAG = "CheckPostureAct"
        private const val ALERT_NOTIFICATION_ID = 202
        private const val RESULT_NOTIFICATION_TIMEOUT_MS = 90_000L // 90 seconds
    }
}
