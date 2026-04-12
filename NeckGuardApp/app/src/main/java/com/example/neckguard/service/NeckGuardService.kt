package com.example.neckguard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.neckguard.R
import com.example.neckguard.engine.PostureEngine
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

enum class AppState {
    DORMANT, MONITORING, ALERT_PENDING, CHECKING, RESULT_SHOWN
}

class NeckGuardService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelSensor: Sensor? = null
    private var gyroSensor: Sensor? = null
    private lateinit var prefs: SharedPreferences

    // State Variables
    private var currentState = AppState.DORMANT
    private var screenReceiver: BroadcastReceiver? = null

    // Cumulative screen time tracking
    private var screenOnSessionStart = 0L
    private var cumulativeUsageTimerMs = 0L

    // Engine Gamification Tracking
    private var lastTickMs = 0L
    private var sessionHealthyMs = 0L
    private var sessionSlouchedMs = 0L

    // Posture tracking
    private var poorPostureSessionStart = 0L
    private var lastAlertTimeMs = 0L
    private var isIntentionallyStopped = false

    private var lastGx = 0f
    private var lastGy = 0f
    private var lastGz = 0f

    // ──────────────────────────────────────────────────────────────────
    // Notification Watchdog: runs inside the live service process.
    // If the user swipes the notification away (Android 14+ allows this),
    // the *service* keeps running but the notification vanishes.
    // This handler checks every 8 seconds and re-posts if missing.
    // ──────────────────────────────────────────────────────────────────
    private val watchdogHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (isIntentionallyStopped) return
            val manager = getSystemService(NotificationManager::class.java)
            val hasOurNotif = manager.activeNotifications.any { it.id == NOTIFICATION_ID }
            if (!hasOurNotif) {
                Log.d(TAG, "Watchdog: notification was swiped — re-posting")
                manager.notify(NOTIFICATION_ID, createPersistentNotification())
            }
            watchdogHandler.postDelayed(this, 8000)
        }
    }

    companion object {
        private const val TAG = "NeckGuardService"
        // Changed Channel ID to force channel recreation with new Importance
        private const val CHANNEL_ID = "neckguard_bg_silent_1"
        private const val ALERT_CHANNEL_ID = "neckguard_alert_channel"
        private const val NOTIFICATION_ID = 101
        private const val ALERT_NOTIFICATION_ID = 202
        private const val BATCH_LATENCY_US = 30_000_000 // 30s batching

        fun getUsageThresholdMs(prefs: SharedPreferences): Long {
            return prefs.getLong("IntervalPreferenceMs", 30 * 60 * 1000L)
        }

        fun getSustainedPoorMs(prefs: SharedPreferences): Long {
            val interval = getUsageThresholdMs(prefs)
            if (interval <= 30_000L) return 5000L
            return 2 * 60 * 1000L
        }

        fun getAlertCooldownMs(prefs: SharedPreferences): Long {
            val interval = getUsageThresholdMs(prefs)
            if (interval <= 30_000L) return 10000L
            return 5 * 60 * 1000L
        }
    }

    // ─────────── Lifecycle ───────────

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service Created")
        com.example.neckguard.CrashReporter.initialize(this)

        // EncryptedSharedPreferences can throw if the device is still locked
        // (hardware-backed key unavailable). Fall back to plain prefs so
        // the service can still boot and monitor posture.
        prefs = try {
            com.example.neckguard.SecurePrefs.get(this)
        } catch (e: Exception) {
            Log.w(TAG, "EncryptedSharedPreferences unavailable, falling back: ${e.message}")
            getSharedPreferences("neckguard_prefs_fallback", Context.MODE_PRIVATE)
        }
        cumulativeUsageTimerMs = prefs.getLong("CumulativeUsageMs", 0L)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            // OS restarted us via START_STICKY or our AlarmManager relay.
            // The service was legitimately running before — skip auth, just resume.
            Log.w(TAG, "Service restarted by OS (START_STICKY) — resuming immediately.")
        } else if (intent.action == "STOP_SERVICE") {
            Log.d(TAG, "User explicitly paused the shield.")
            isIntentionallyStopped = true
            watchdogHandler.removeCallbacksAndMessages(null)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        } else {
            // Normal user-initiated start — verify auth.
            val userRepo = com.example.neckguard.data.UserRepository(prefs)
            if (!userRepo.hydrateSession() || !userRepo.hasCompletedOnboarding()) {
                Log.w(TAG, "Auth invalid or onboarding incomplete. Killing ghost service.")
                stopSelf()
                return START_NOT_STICKY
            }
        }

        isIntentionallyStopped = false
        val notification = createPersistentNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        // Start the watchdog that re-posts the notification if swiped
        watchdogHandler.removeCallbacksAndMessages(null)
        watchdogHandler.postDelayed(watchdogRunnable, 8000)

        registerScreenReceiver()
        transitionTo(AppState.MONITORING)

        return START_STICKY
    }

    /** Called when user swipes the app from recents — schedule a restart. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (!isIntentionallyStopped) {
            Log.d(TAG, "App swiped from recents — scheduling restart")
            scheduleRestart()
        }
    }

    // ─────────── Screen ON/OFF receiver ───────────

    private fun registerScreenReceiver() {
        if (screenReceiver == null) {
            screenReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    when (intent.action) {
                        Intent.ACTION_SCREEN_ON -> transitionTo(AppState.MONITORING)
                        Intent.ACTION_SCREEN_OFF -> transitionTo(AppState.DORMANT)
                    }
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_SCREEN_OFF)
            }
            registerReceiver(screenReceiver, filter)
        }
    }

    // ─────────── State machine ───────────

    private fun transitionTo(newState: AppState) {
        if (currentState == newState) return
        Log.d(TAG, "Transitioning State: $currentState -> $newState")

        when (newState) {
            AppState.DORMANT -> {
                Log.d(TAG, "Screen OFF. Unregistering sensors, pausing timer.")
                sensorManager.unregisterListener(this)
                PostureEngine.resetFilter()

                if (screenOnSessionStart != 0L) {
                    val duration = System.currentTimeMillis() - screenOnSessionStart
                    cumulativeUsageTimerMs += duration
                    prefs.edit().putLong("CumulativeUsageMs", cumulativeUsageTimerMs).apply()

                    if (duration > 5_000L) {
                        val dao = com.example.neckguard.data.local.NeckGuardDatabase.getDatabase(this).postureLogDao()
                        val log = com.example.neckguard.data.local.PostureLog(
                            timestampStartMs = screenOnSessionStart,
                            durationMs = duration,
                            healthyMs = sessionHealthyMs,
                            slouchedMs = sessionSlouchedMs
                        )
                        GlobalScope.launch(Dispatchers.IO) { dao.insertLog(log) }
                    }

                    screenOnSessionStart = 0L
                    lastTickMs = 0L
                    sessionHealthyMs = 0L
                    sessionSlouchedMs = 0L
                }
            }
            AppState.MONITORING -> {
                Log.d(TAG, "Screen ON. Registering sensors.")
                screenOnSessionStart = System.currentTimeMillis()
                accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI, BATCH_LATENCY_US) }
                gyroSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI, BATCH_LATENCY_US) }
            }
            AppState.ALERT_PENDING -> {
                sendPostureAlert()
            }
            else -> {}
        }
        currentState = newState
    }

    // ─────────── Sensor processing ───────────

    override fun onSensorChanged(event: SensorEvent) {
        if (currentState != AppState.MONITORING) return

        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            lastGx = event.values[0]
            lastGy = event.values[1]
            lastGz = event.values[2]
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val displayManager = getSystemService(Context.DISPLAY_SERVICE) as android.hardware.display.DisplayManager
            val rotation = displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY)?.rotation ?: android.view.Surface.ROTATION_0

            val state = PostureEngine.processSensorTick(
                ax = event.values[0], ay = event.values[1], az = event.values[2],
                gx = lastGx, gy = lastGy, gz = lastGz,
                timestampNS = event.timestamp, surfaceRotation = rotation
            )

            val now = System.currentTimeMillis()
            if (lastTickMs > 0L) {
                val delta = now - lastTickMs
                if (state == PostureEngine.PostureState.POOR) {
                    sessionSlouchedMs += delta
                } else {
                    sessionHealthyMs += delta
                }
            }
            lastTickMs = now

            if (state == PostureEngine.PostureState.POOR) {
                if (poorPostureSessionStart == 0L) poorPostureSessionStart = now

                val timeInPoor = now - poorPostureSessionStart
                val screenSessionTime = now - screenOnSessionStart
                val totalUsageThisSession = cumulativeUsageTimerMs + screenSessionTime
                val timeSinceLastAlert = now - lastAlertTimeMs

                if (totalUsageThisSession >= getUsageThresholdMs(prefs) &&
                    timeInPoor >= getSustainedPoorMs(prefs) &&
                    timeSinceLastAlert >= getAlertCooldownMs(prefs)) {
                    sendPostureAlert()
                }
            } else if (state == PostureEngine.PostureState.GOOD || state == PostureEngine.PostureState.MODERATE) {
                poorPostureSessionStart = 0L
            }
        }
    }

    // ─────────── Posture alert ───────────

    private fun sendPostureAlert() {
        Log.d(TAG, "FIRING POSTURE ALERT!")

        lastAlertTimeMs = System.currentTimeMillis()
        poorPostureSessionStart = 0L
        cumulativeUsageTimerMs = 0L
        screenOnSessionStart = System.currentTimeMillis()
        prefs.edit().putLong("CumulativeUsageMs", 0L).apply()

        val intent = Intent(this, com.example.neckguard.CheckPostureActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
            putExtra("phone_pitch", com.example.neckguard.engine.PostureEngine.currentPitch)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 0, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Quick check-in \uD83D\uDC4B")
            .setContentText("You've been on your phone for a while. Tap to check your posture.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(ALERT_NOTIFICATION_ID, builder.build())
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?): IBinder? = null

    // ─────────── Cleanup & restart ───────────

    override fun onDestroy() {
        Log.d(TAG, "Service Destroyed")
        watchdogHandler.removeCallbacksAndMessages(null)
        screenReceiver?.let { unregisterReceiver(it) }
        sensorManager.unregisterListener(this)

        if (!isIntentionallyStopped) {
            scheduleRestart()
        }

        super.onDestroy()
    }

    /** Schedules a restart via AlarmManager → BroadcastReceiver relay. */
    private fun scheduleRestart() {
        Log.d(TAG, "Scheduling restart via ServiceRestartReceiver")
        val restartIntent = Intent("com.example.neckguard.RESTART_SERVICE").apply {
            setClass(applicationContext, ServiceRestartReceiver::class.java)
        }
        val pendingIntent = android.app.PendingIntent.getBroadcast(
            this, 1, restartIntent,
            android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val triggerAt = android.os.SystemClock.elapsedRealtime() + 1500
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(
                android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pendingIntent
            )
        }
    }

    // ─────────── Notification helpers ───────────

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // IMPORTANCE_MIN puts the notification in the 'Silent' section
            // In the Silent section, Android NEVER auto-expands notifications.
            val bgChannel = NotificationChannel(CHANNEL_ID, "NeckGuard Active Status", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Keeps the background sensor engine alive."
                setShowBadge(false)
            }

            val alertChannel = NotificationChannel(ALERT_CHANNEL_ID, "Posture Reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Gentle nudges when you have sustained bad posture."
            }

            manager.createNotificationChannel(bgChannel)
            manager.createNotificationChannel(alertChannel)
        }
    }

    private fun createPersistentNotification(): Notification {
        // "Pause Shield" intent — sends STOP_SERVICE so watchdog + alarm are skipped
        val pauseIntent = Intent(this, NeckGuardService::class.java).apply { action = "STOP_SERVICE" }
        val pausePendingIntent = android.app.PendingIntent.getService(
            this, 2, pauseIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        // Tapping the notification body opens the app
        val openAppIntent = Intent(this, com.example.neckguard.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = android.app.PendingIntent.getActivity(
            this, 3, openAppIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NeckGuard Active")
            .setContentText("Monitoring posture silently")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            // PRIORITY_MIN ensures it behaves as a silent background notification on older API levels too
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            // Adding the native action button. Because it's a MIN importance notification,
            // the OS will keep it collapsed in the Silent section by default,
            // revealing the button only when the user manually taps the expand arrow.
            .addAction(android.R.drawable.ic_media_pause, "Pause Shield", pausePendingIntent)
            .build()
    }
}
