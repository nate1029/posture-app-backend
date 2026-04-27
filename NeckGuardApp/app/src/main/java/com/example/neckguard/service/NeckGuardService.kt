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
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import android.view.Display
import android.view.Surface
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.neckguard.R
import com.example.neckguard.engine.PostureEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Service-internal state machine. Distinct from [com.example.neckguard.ui.AppState],
 * which models the UI's auth/onboarding lifecycle.
 *
 * Kept lean — the previously-listed CHECKING and RESULT_SHOWN entries were
 * never produced by [NeckGuardService.transitionTo], and the only consumer
 * silently no-op'd on them. Removed to make the actual state graph
 * representable in the type system.
 */
enum class AppState {
    DORMANT, MONITORING, ALERT_PENDING
}

class NeckGuardService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private lateinit var displayManager: DisplayManager
    private var accelSensor: Sensor? = null
    private var gyroSensor: Sensor? = null
    private lateinit var prefs: SharedPreferences

    // Cached screen rotation, updated by a DisplayListener instead of re-read every tick.
    @Volatile private var cachedRotation: Int = Surface.ROTATION_0

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

    // Dedicated scope for service-scoped background work (DB writes, etc.).
    // Cancelled in onDestroy so we don't leak coroutines like GlobalScope would.
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ──────────────────────────────────────────────────────────────────
    // Notification Watchdog.
    // Runs on a dedicated background HandlerThread (NOT the main thread) so it
    // cannot contend with UI or sensor callbacks. Interval bumped from 8s → 60s
    // since a transiently-missing silent notification is not catastrophic.
    // ──────────────────────────────────────────────────────────────────
    private var watchdogThread: HandlerThread? = null
    private var watchdogHandler: Handler? = null
    private val watchdogRunnable = object : Runnable {
        override fun run() {
            if (isIntentionallyStopped) return
            try {
                val manager = getSystemService(NotificationManager::class.java)
                val hasOurNotif = manager.activeNotifications.any { it.id == NOTIFICATION_ID }
                if (!hasOurNotif) {
                    Log.d(TAG, "Watchdog: notification was swiped — re-posting")
                    manager.notify(NOTIFICATION_ID, createPersistentNotification())
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Watchdog tick failed: ${t.message}")
            }
            watchdogHandler?.postDelayed(this, WATCHDOG_INTERVAL_MS)
        }
    }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) {}
        override fun onDisplayRemoved(displayId: Int) {}
        override fun onDisplayChanged(displayId: Int) {
            cachedRotation = displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation
                ?: Surface.ROTATION_0
        }
    }

    companion object {
        private const val TAG = "NeckGuardService"
        // Changed Channel ID to force channel recreation with new Importance
        private const val CHANNEL_ID = "neckguard_bg_silent_1"
        private const val ALERT_CHANNEL_ID = "neckguard_alert_channel"
        private const val NOTIFICATION_ID = 101
        private const val ALERT_NOTIFICATION_ID = 202
        // Batch sensor deliveries at most 2 seconds apart. Previously 30s, which
        // meant `PostureEngine.currentPitch` could be up to 30 seconds stale when
        // CheckPostureActivity read it — directly causing "late / wrong" alerts.
        private const val BATCH_LATENCY_US = 2_000_000
        private const val WATCHDOG_INTERVAL_MS = 60_000L

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
        // CrashReporter is now initialized once in the Application class. No need to re-init here.

        // SecurePrefs is now a cached singleton; fallback handling lives inside it.
        prefs = com.example.neckguard.SecurePrefs.get(this)
        cumulativeUsageTimerMs = prefs.getLong("CumulativeUsageMs", 0L)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

        // Cache DisplayManager + initial rotation once. Previously we re-resolved the
        // display on every accelerometer event (~every 20 ms), which was an expensive
        // binder call and constant allocation.
        displayManager = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        cachedRotation = displayManager.getDisplay(Display.DEFAULT_DISPLAY)?.rotation
            ?: Surface.ROTATION_0
        displayManager.registerDisplayListener(displayListener, null)

        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Pause from the persistent-notification action — short-circuit before
        // any auth-check / foreground-promotion work.
        if (intent?.action == "STOP_SERVICE") {
            Log.d(TAG, "User explicitly paused the shield.")
            isIntentionallyStopped = true
            stopWatchdog()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // Auth check runs on EVERY entry point — explicit user start, OS
        // redelivery (intent == null branch of START_STICKY), and alarm-relay
        // restart. Previously the null-intent branch bypassed this, so a
        // user who logged out could still have a "ghost service" running
        // under their persistent notification after the OS redelivered the
        // start. (B-13)
        if (intent == null) {
            Log.w(TAG, "Service restarted by OS (START_STICKY) — re-checking session.")
        }
        val userRepo = com.example.neckguard.data.UserRepository(prefs)
        if (!userRepo.hydrateSession() || !userRepo.hasCompletedOnboarding()) {
            Log.w(TAG, "Auth invalid or onboarding incomplete. Killing ghost service.")
            stopSelf()
            return START_NOT_STICKY
        }

        isIntentionallyStopped = false
        val notification = createPersistentNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startWatchdog()
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
            // Android 14 (API 34) requires every dynamic receiver to specify whether it is
            // exported. SCREEN_ON/OFF are protected system broadcasts so RECEIVER_NOT_EXPORTED
            // is correct — only the system can deliver them.
            ContextCompat.registerReceiver(
                this,
                screenReceiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
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
                        val appCtx = applicationContext
                        val healthy = sessionHealthyMs
                        val slouched = sessionSlouchedMs
                        val sessionStart = screenOnSessionStart
                        // Run DB work on our service-scoped CoroutineScope instead of
                        // GlobalScope so it's cancelled if the service dies.
                        serviceScope.launch {
                            try {
                                val dao = com.example.neckguard.data.local.NeckGuardDatabase
                                    .getDatabase(appCtx).postureLogDao()
                                val log = com.example.neckguard.data.local.PostureLog(
                                    sessionStart,
                                    duration,
                                    healthy,
                                    slouched,
                                    false
                                )
                                dao.insertLog(log)
                            } catch (t: Throwable) {
                                Log.w(TAG, "Failed to persist posture log: ${t.message}")
                            }
                        }
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
                // SENSOR_DELAY_NORMAL (~200 ms) is plenty for a posture filter. UI rate (~60 Hz)
                // was wasteful given a 2s batch window still produces dozens of samples to filter.
                accelSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, BATCH_LATENCY_US) }
                gyroSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL, BATCH_LATENCY_US) }
            }
            AppState.ALERT_PENDING -> {
                sendPostureAlert()
            }
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
            val state = PostureEngine.processSensorTick(
                ax = event.values[0], ay = event.values[1], az = event.values[2],
                gx = lastGx, gy = lastGy, gz = lastGz,
                timestampNS = event.timestamp, surfaceRotation = cachedRotation
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

            // Periodically flush partial session data to Room so the
            // dashboard score updates while the screen is on, not just
            // when it turns off.
            if (lastFlushMs == 0L) lastFlushMs = now
            if (now - lastFlushMs >= FLUSH_INTERVAL_MS) {
                flushCurrentSessionToDb()
            }

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

        // Flush the current session's posture data to Room BEFORE resetting
        // the counters. This is what makes the dashboard score update in
        // real-time — previously data only landed in the DB when the screen
        // turned OFF, so the score was frozen while the user was looking at it.
        flushCurrentSessionToDb()

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
            // Auto-dismiss if the user doesn't tap within 2 minutes.
            // If they DO tap, CheckPostureActivity replaces this with
            // the result notification (same ID 202), which has its own
            // 90-second timeout.
            .setTimeoutAfter(120_000L)

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(ALERT_NOTIFICATION_ID, builder.build())
    }

    // ─────────── Periodic score flush ───────────

    private var lastFlushMs = 0L
    private val FLUSH_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes

    /**
     * Writes the current screen-on session's accumulated healthy/slouched
     * time to Room as a partial log entry, WITHOUT resetting the session
     * counters. This makes the dashboard score update in near-real-time
     * while the screen is on.
     *
     * Uses REPLACE conflict strategy (PostureLogDao.insertLog) keyed on
     * `timestampStartMs`, so repeated flushes for the same session just
     * overwrite the previous partial row with the latest cumulative values.
     * When the screen eventually turns OFF and DORMANT fires, it writes the
     * final complete row with the same `timestampStartMs` key — naturally
     * replacing the partial one.
     */
    private fun flushCurrentSessionToDb() {
        if (screenOnSessionStart == 0L) return
        val now = System.currentTimeMillis()
        val duration = now - screenOnSessionStart
        if (duration < 5_000L) return

        val appCtx = applicationContext
        val healthy = sessionHealthyMs
        val slouched = sessionSlouchedMs
        val sessionStart = screenOnSessionStart

        serviceScope.launch {
            try {
                val dao = com.example.neckguard.data.local.NeckGuardDatabase
                    .getDatabase(appCtx).postureLogDao()
                val log = com.example.neckguard.data.local.PostureLog(
                    sessionStart, duration, healthy, slouched, false
                )
                dao.insertLog(log)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to flush partial posture log: ${t.message}")
            }
        }
        lastFlushMs = now
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    override fun onBind(intent: Intent?): IBinder? = null

    // ─────────── Cleanup & restart ───────────

    override fun onDestroy() {
        Log.d(TAG, "Service Destroyed")
        stopWatchdog()
        screenReceiver?.let {
            try { unregisterReceiver(it) } catch (_: IllegalArgumentException) {}
        }
        screenReceiver = null
        try { displayManager.unregisterDisplayListener(displayListener) } catch (_: Throwable) {}
        sensorManager.unregisterListener(this)
        serviceScope.cancel()

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

    // ─────────── Watchdog lifecycle ───────────

    private fun startWatchdog() {
        stopWatchdog()
        val thread = HandlerThread("NeckGuardWatchdog").apply { start() }
        val handler = Handler(thread.looper)
        watchdogThread = thread
        watchdogHandler = handler
        handler.postDelayed(watchdogRunnable, WATCHDOG_INTERVAL_MS)
    }

    private fun stopWatchdog() {
        watchdogHandler?.removeCallbacksAndMessages(null)
        watchdogHandler = null
        watchdogThread?.quitSafely()
        watchdogThread = null
    }

    // ─────────── Notification helpers ───────────

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            val bgChannel = NotificationChannel(CHANNEL_ID, "NudgeUp Active Status", NotificationManager.IMPORTANCE_MIN).apply {
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
        val pauseIntent = Intent(this, NeckGuardService::class.java).apply { action = "STOP_SERVICE" }
        val pausePendingIntent = android.app.PendingIntent.getService(
            this, 2, pauseIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, com.example.neckguard.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = android.app.PendingIntent.getActivity(
            this, 3, openAppIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NudgeUp Active")
            .setContentText("Monitoring posture silently")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .addAction(android.R.drawable.ic_media_pause, "Pause Shield", pausePendingIntent)
            .build()
    }
}
