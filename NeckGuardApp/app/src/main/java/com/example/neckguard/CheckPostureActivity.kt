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
                Log.e(TAG, "Use case binding failed", exc)
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

                val phonePitch = intent.getFloatExtra("phone_pitch", 45f)
                val trueNeckPitch = (phonePitch + facePitchExtracted) * 0.82f

                if (com.example.neckguard.BuildConfig.DEBUG) {
                    Log.d(TAG, "WebApp Logic Match -> Phone: $phonePitch° | Face: $facePitchExtracted° | True Flexion: $trueNeckPitch°")
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

                fireResultNotification(message)
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
        fireResultNotification("Posture check failed ($reason). Based on the phone sensors, your neck may still be strained.")
    }

    private fun fireResultNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)

        val style = NotificationCompat.BigTextStyle().bigText(text)

        val builder = NotificationCompat.Builder(this, "neckguard_alert_channel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Posture Result")
            .setContentText(text)
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setTimeoutAfter(RESULT_NOTIFICATION_TIMEOUT_MS)

        manager.notify(ALERT_NOTIFICATION_ID, builder.build())
    }

    private fun generateNotificationMessage(vibe: String, pitch: Float): String {
        val type = when {
            pitch > 35f -> "high_risk"
            pitch > 15f -> "moderate"
            else -> "great"
        }
        val pitchStr = String.format("%.1f", pitch)

        val messages = when (vibe) {
            "Chaotic — memes, gen-z humour, unhinged energy" -> when (type) {
                "high_risk" -> listOf("Bro your neck is at $pitchStr° 💀 Are you trying to become a shrimp?", "Bestie plz sit up, $pitchStr° flexion is giving goblin energy.", "Your spine is screaming rn ($pitchStr°). Fix it before you evolve into a prawn 🦐.")
                "moderate" -> listOf("Ayo, $pitchStr° is a bit sus. Straighten up.", "You're slipping into gremlin mode ($pitchStr°). Pull it back.", "Mild slouch detected ($pitchStr°). Let's not make it worse.")
                else -> listOf("Posture check passed! You actually look like a human being.", "Slay! Your spine is serving absolute straightness.", "No notes, your posture is immaculate rn.")
            }
            "Hype mode — motivational, hustle, 'you got this'" -> when (type) {
                "high_risk" -> listOf("High Risk ($pitchStr°)! You're better than this, straighten that back and conquer the day!", "Don't let bad posture defeat you! $pitchStr° flexion is a setback, let's bounce back!", "You are a warrior, but right now your neck ($pitchStr°) says otherwise. Head up!")
                "moderate" -> listOf("Keep the momentum going! Fix that $pitchStr° slouch and stay sharp.", "You're slipping a bit ($pitchStr°). Reset and get back to grinding with good posture!", "Almost there! Just straighten up a bit from $pitchStr° and you're golden.")
                else -> listOf("That's what I'm talking about! Great posture, keep crushing it!", "Posture on point! You're unstoppable right now.", "Perfect alignment! Keep that head held high.")
            }
            "Calm & mindful — gentle, soft, no pressure" -> when (type) {
                "high_risk" -> listOf("Your neck is carrying a lot of tension right now ($pitchStr°). Let's gently sit up and take a deep breath.", "It looks like you're leaning in quite deeply ($pitchStr°). Please remember to be kind to your spine.", "Take a gentle pause. Your neck is flexed at $pitchStr°, let's slowly bring it back to a comfortable center.")
                "moderate" -> listOf("A gentle reminder to check your posture ($pitchStr°). Small adjustments make a big difference.", "You seem to be slightly slouching ($pitchStr°). Let's gently reset your shoulders.", "Whenever you're ready, try to ease back into a more upright position ($pitchStr°).")
                else -> listOf("Your posture is beautiful right now. Take a deep breath and enjoy this alignment.", "Wonderful alignment. Your spine is balanced and at ease.", "You are sitting perfectly. Keep honoring your body's natural shape.")
            }
            else -> when (type) { // "Just the facts" or default
                "high_risk" -> listOf("High Risk Mode ($pitchStr° flexion): You are heavily slouched. Please sit up.", "Critical flexion detected: $pitchStr°. Immediate correction recommended.", "Warning: $pitchStr° neck flexion. This exceeds safe ergonomic limits.")
                "moderate" -> listOf("Moderate Risk ($pitchStr° flexion): Your neck is slightly crouched.", "Notice: $pitchStr° flexion. Adjust your posture to prevent strain.", "Alert: $pitchStr° flexion detected. Straighten your back.")
                else -> listOf("Posture looks great! Keep it up.", "Posture check passed: normal alignment.", "Status: Healthy alignment. No correction needed.")
            }
        }
        return messages.random()
    }

    companion object {
        private const val TAG = "CheckPostureAct"
        private const val ALERT_NOTIFICATION_ID = 202
        private const val RESULT_NOTIFICATION_TIMEOUT_MS = 90_000L // 90 seconds
    }
}
