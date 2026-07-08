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

import com.example.neckguard.R

import android.view.Display

import android.view.Surface

import androidx.core.app.NotificationCompat

import androidx.core.content.ContextCompat

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

 * Kept lean ΓÇö the previously-listed CHECKING and RESULT_SHOWN entries were

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

    private var sessionHealthyMs = 0L

    private var sessionSlouchedMs = 0L



    // Posture tracking

    private var lastAlertTimeMs = 0L

    private var isIntentionallyStopped = false



    private var lastGx = 0f

    private var lastGy = 0f

    private var lastGz = 0f

    // Tracks last posture reading for diagnosis-aware notifications.
    private var lastPostureState = com.example.neckguard.engine.PostureEngine.PostureState.GOOD

    // ── Duty-Cycle Sampling ─────────────────────────────────────────────
    // Sensors run for SAMPLE_WINDOW_MS every SAMPLE_INTERVAL_MS.
    // This reduces CPU usage by ~96% vs continuous monitoring.
    private val dutyCycleHandler = Handler(android.os.Looper.getMainLooper())

    // Per-window vote tallies
    private var windowPoorVotes = 0
    private var windowGoodVotes = 0

    // Accumulated bad-posture time (persisted). The camera posture-check fires
    // once this reaches the user-chosen interval (getUsageThresholdMs). Unlike
    // the old "consecutive windows" model, GOOD windows no longer reset it —
    // bad-posture time simply adds up across the session until the threshold.
    private var accumulatedBadPostureMs = 0L

    private val sampleWindowRunnable = Runnable {
        // Sample window ends → stop sensors, evaluate window
        sensorManager.unregisterListener(this)
        evaluateSampleWindow()
    }

    private val sampleCycleRunnable = object : Runnable {
        override fun run() {
            if (currentState != AppState.MONITORING || isIntentionallyStopped) return
            // Start a new sample window: register sensors
            windowPoorVotes = 0
            windowGoodVotes = 0
            PostureEngine.resetFilter()
            accelSensor?.let { sensorManager.registerListener(
                this@NeckGuardService, it, SensorManager.SENSOR_DELAY_NORMAL, BATCH_LATENCY_US) }
            gyroSensor?.let { sensorManager.registerListener(
                this@NeckGuardService, it, SensorManager.SENSOR_DELAY_NORMAL, BATCH_LATENCY_US) }
            Log.d(TAG, "Duty cycle: sensors ON for ${SAMPLE_WINDOW_MS / 1000}s")
            // Stop sensors after the window duration
            dutyCycleHandler.postDelayed(sampleWindowRunnable, SAMPLE_WINDOW_MS)
            // Schedule next cycle
            dutyCycleHandler.postDelayed(this, SAMPLE_INTERVAL_MS)
        }
    }



    // Dedicated scope for service-scoped background work (DB writes, etc.).

    // Cancelled in onDestroy so we don't leak coroutines like GlobalScope would.

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // -- Time-based interval nudge --
    // Guarantees a check fires every X minutes while screen is ON regardless of posture state.
    // Fixes the Netflix case: user with good posture for 2+ hours never gets a reminder.
    private val intervalTimerHandler = Handler(android.os.Looper.getMainLooper())
    private val intervalTimerRunnable = object : Runnable {
        override fun run() {
            if (currentState == AppState.MONITORING && !isIntentionallyStopped) {
                Log.d(TAG, "Time-based interval nudge firing")
                sendReminderNotification()
            }
        }
    }



    // ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ

    // Notification Watchdog.

    // Runs on a dedicated background HandlerThread (NOT the main thread) so it

    // cannot contend with UI or sensor callbacks. Interval bumped from 8s ΓåÆ 60s

    // since a transiently-missing silent notification is not catastrophic.

    // ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ

    private var watchdogThread: HandlerThread? = null

    private var watchdogHandler: Handler? = null

    private val watchdogRunnable = object : Runnable {

        override fun run() {

            if (isIntentionallyStopped) return

            try {

                val manager = getSystemService(NotificationManager::class.java)

                val hasOurNotif = manager.activeNotifications.any { it.id == NOTIFICATION_ID }

                if (!hasOurNotif) {

                    Log.d(TAG, "Watchdog: notification was swiped ΓÇö re-posting")

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

        // Sensor batch latency for duty-cycle windows.
        private const val BATCH_LATENCY_US = 2_000_000

        // Duty-cycle constants:
        //   SAMPLE_WINDOW_MS  = how long sensors stay ON per cycle (~10 seconds)
        //   SAMPLE_INTERVAL_MS = gap between cycle starts (every 4 minutes)
        // Net sensor active time per hour: (10/240) * 60 min ≈ 2.5 min (~4% duty cycle)
        private const val SAMPLE_WINDOW_MS   = 10_000L  // 10 seconds ON
        private const val SAMPLE_INTERVAL_MS = 4 * 60 * 1000L  // 4-minute cycle

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



    // ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ Lifecycle ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ



    override fun onCreate() {

        super.onCreate()

        Log.d(TAG, "Service Created")

        // CrashReporter is now initialized once in the Application class. No need to re-init here.



        // SecurePrefs is now a cached singleton; fallback handling lives inside it.

        prefs = com.example.neckguard.SecurePrefs.get(this)

        cumulativeUsageTimerMs = prefs.getLong("CumulativeUsageMs", 0L)

        accumulatedBadPostureMs = prefs.getLong("AccumulatedBadPostureMs", 0L)



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

        // Pause from the persistent-notification action ΓÇö short-circuit before

        // any auth-check / foreground-promotion work.

        if (intent?.action == "STOP_SERVICE") {

            Log.d(TAG, "User explicitly paused the shield.")

            isIntentionallyStopped = true

            stopWatchdog()

            stopForeground(STOP_FOREGROUND_REMOVE)

            stopSelf()

            return START_NOT_STICKY

        }



        // Auth check runs on EVERY entry point ΓÇö explicit user start, OS

        // redelivery (intent == null branch of START_STICKY), and alarm-relay

        // restart. Previously the null-intent branch bypassed this, so a

        // user who logged out could still have a "ghost service" running

        // under their persistent notification after the OS redelivered the

        // start. (B-13)

        if (intent == null) {

            Log.w(TAG, "Service restarted by OS (START_STICKY) ΓÇö re-checking session.")

        }

        val userRepo = com.example.neckguard.data.UserRepository(prefs)

        if (!userRepo.hydrateSession() || !userRepo.hasCompletedOnboarding()) {

            Log.w(TAG, "Auth invalid or onboarding incomplete. Killing ghost service.")

            isIntentionallyStopped = true

            stopSelf()

            return START_NOT_STICKY

        }



        isIntentionallyStopped = false

        val notification = createPersistentNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)

        } else {

            startForeground(NOTIFICATION_ID, notification)

        }



        startWatchdog()

        registerScreenReceiver()

        transitionTo(AppState.MONITORING)



        return START_STICKY

    }



    /** Called when user swipes the app from recents ΓÇö schedule a restart. */

    override fun onTaskRemoved(rootIntent: Intent?) {

        super.onTaskRemoved(rootIntent)

        if (!isIntentionallyStopped) {

            Log.d(TAG, "App swiped from recents ΓÇö scheduling restart")

            scheduleRestart()

        }

    }



    // ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ Screen ON/OFF receiver ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ



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

            // is correct ΓÇö only the system can deliver them.

            ContextCompat.registerReceiver(

                this,

                screenReceiver,

                filter,

                ContextCompat.RECEIVER_NOT_EXPORTED

            )

        }

    }



    // ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ State machine ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ



    private fun transitionTo(newState: AppState) {

        if (currentState == newState) return

        Log.d(TAG, "Transitioning State: $currentState -> $newState")



        when (newState) {

            AppState.DORMANT -> {

                Log.d(TAG, "Screen OFF. Stopping duty-cycle sampling.")

                // Stop the duty cycle entirely
                dutyCycleHandler.removeCallbacksAndMessages(null)
                sensorManager.unregisterListener(this)
                PostureEngine.resetFilter()
                // Note: accumulatedBadPostureMs is intentionally NOT reset here.
                // Bad-posture time carries across screen-off so it keeps adding
                // up toward the user's threshold across separate usage sessions.



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

                    sessionHealthyMs = 0L

                    sessionSlouchedMs = 0L

                }

            }

            AppState.MONITORING -> {

                Log.d(TAG, "Screen ON. Starting duty-cycle sampling.")
                screenOnSessionStart = System.currentTimeMillis()
                scheduleIntervalNudge()

                // Duty-cycle: run the first sample immediately, then every SAMPLE_INTERVAL_MS.
                dutyCycleHandler.removeCallbacksAndMessages(null)
                dutyCycleHandler.post(sampleCycleRunnable)

            }

            AppState.ALERT_PENDING -> {

                sendBadPostureNotification()

            }

        }

        currentState = newState

    }



    // ── Sensor processing (duty-cycle sample window) ──────────────────────

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

            // Tally votes for this sample window.
            // MODERATE counts as a poor-posture vote — it represents real
            // forward neck load (15–25° flexion) that accumulates damage over
            // time. Previously it was grouped with GOOD, causing typical
            // phone-in-lap use (~35° tilt) to be silently ignored.
            when (state) {
                PostureEngine.PostureState.POOR,
                PostureEngine.PostureState.MODERATE -> windowPoorVotes++
                PostureEngine.PostureState.GOOD -> windowGoodVotes++
                else -> { /* IDLE/UNKNOWN — ignore */ }
            }

            lastPostureState = state
        }
    }

    /**
     * Called at the end of each 10-second sample window.
     * Majority vote determines whether the window counts as POOR or GOOD.
     * Accumulates bad-posture time and triggers the camera posture-check once
     * the total reaches the user-chosen interval (getUsageThresholdMs).
     */
    private fun evaluateSampleWindow() {
        val isMajorityPoor = windowPoorVotes > windowGoodVotes
        Log.d(TAG, "Sample window: poor=$windowPoorVotes good=$windowGoodVotes → ${if (isMajorityPoor) "POOR" else "GOOD"}")

        // Attribute this window's time to the session (dashboard accounting)
        if (isMajorityPoor) {
            sessionSlouchedMs += SAMPLE_WINDOW_MS
            // Accumulate real bad-posture time toward the posture-check trigger.
            // Each poor sample represents its ~4-minute duty cycle, so we add
            // SAMPLE_INTERVAL_MS (not the 10s sample window). GOOD windows do
            // NOT reset this — bad time simply adds up until the user's threshold.
            accumulatedBadPostureMs += SAMPLE_INTERVAL_MS
            prefs.edit().putLong("AccumulatedBadPostureMs", accumulatedBadPostureMs).apply()
        } else {
            sessionHealthyMs += SAMPLE_WINDOW_MS
        }

        // Flush to DB after every window so dashboard updates ~every 4 min
        flushCurrentSessionToDb()

        // Fire the camera posture-check once accumulated bad-posture time reaches
        // the interval the user chose in settings (e.g. 30 min of bad posture,
        // added up over the session rather than needing to be continuous).
        if (accumulatedBadPostureMs >= getUsageThresholdMs(prefs)) {
            Log.d(TAG, "Accumulated bad posture ${accumulatedBadPostureMs / 60_000}min reached threshold — firing posture check")
            accumulatedBadPostureMs = 0L
            prefs.edit().putLong("AccumulatedBadPostureMs", 0L).apply()
            sendBadPostureNotification()
        }
    }



    // ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ Posture alert ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ



    // ── Type 1: Reminder Notification ──────────────────────────────────
    // Fires from the periodic timer. Posture is fine — we're just nudging
    // the user to try an exercise and keep their streak alive.
    private fun sendReminderNotification() {
        Log.d(TAG, "FIRING REMINDER NOTIFICATION")

        flushCurrentSessionToDb()

        lastAlertTimeMs = System.currentTimeMillis()
        cumulativeUsageTimerMs = 0L
        screenOnSessionStart = System.currentTimeMillis()
        prefs.edit().putLong("CumulativeUsageMs", 0L).apply()
        intervalTimerHandler.removeCallbacksAndMessages(null)
        scheduleIntervalNudge()

        // Pick next uncompleted exercise for the CTA
        val repository = com.example.neckguard.data.UserRepository(prefs)
        val assignedList = repository.assignedExercisesList
        val completedList = repository.completedExercisesTodayList
        val targetExercise = assignedList.firstOrNull { !completedList.contains(it) }
            ?: assignedList.firstOrNull() ?: "Chin Tuck"

        // Dashboard intent (tap body -> opens app)
        val mainIntent = Intent(this, com.example.neckguard.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = android.app.PendingIntent.getActivity(
            this, 1010, mainIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Exercise intent (CTA button -> opens exercise)
        val exerciseIntent = Intent(this, com.example.neckguard.MainActivity::class.java).apply {
            action = "OPEN_EXERCISE"
            putExtra("exercise_name", targetExercise)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val exercisePendingIntent = android.app.PendingIntent.getActivity(
            this, 1011, exerciseIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Pick a random message from the reminder pool
        val messages = com.example.neckguard.NotificationPool.reminderMessages
        val lastIdx = prefs.getInt("lastReminderMsgIdx", -1)
        var idx = messages.indices.random()
        while (idx == lastIdx && messages.size > 1) idx = messages.indices.random()
        prefs.edit().putInt("lastReminderMsgIdx", idx).apply()

        val body = messages[idx].replace("{exercise}", targetExercise)

        val reminderTitles = listOf(
            "Time for a stretch \uD83E\uDDD8",
            "Exercise check-in \u2728",
            "Quick stretch break \uD83D\uDCAA",
            "Your body will thank you \uD83D\uDE4F",
            "Stretch o'clock! \u23F0",
            "Wellness moment \uD83C\uDF3F",
            "Keep the streak alive \uD83D\uDD25",
            "Hey champion \uD83C\uDFC6"
        )
        val lastTitleIdx = prefs.getInt("lastReminderTitleIdx", -1)
        var titleIdx = reminderTitles.indices.random()
        while (titleIdx == lastTitleIdx && reminderTitles.size > 1) titleIdx = reminderTitles.indices.random()
        prefs.edit().putInt("lastReminderTitleIdx", titleIdx).apply()

        val builder = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminderTitles[titleIdx])
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(mainPendingIntent)
            .addAction(0, "Try $targetExercise \uD83D\uDCAA", exercisePendingIntent)
            .setAutoCancel(true)
            .setTimeoutAfter(120_000L)

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(ALERT_NOTIFICATION_ID, builder.build())

        com.example.neckguard.data.UserRepository(prefs).addNudgeFiredToday()
    }

    // ── Type 2: Bad Posture Notification ─────────────────────────────────
    // Fires when sensors detect sustained bad posture. Tap -> camera check.
    private fun sendBadPostureNotification() {

        Log.d(TAG, "FIRING BAD POSTURE NOTIFICATION!")




        // Flush the current session's posture data to Room BEFORE resetting

        // the counters. This is what makes the dashboard score update in

        // real-time ΓÇö previously data only landed in the DB when the screen

        // turned OFF, so the score was frozen while the user was looking at it.

        flushCurrentSessionToDb()



        lastAlertTimeMs = System.currentTimeMillis()

        cumulativeUsageTimerMs = 0L

        screenOnSessionStart = System.currentTimeMillis()

        prefs.edit().putLong("CumulativeUsageMs", 0L).apply()
        // Reset interval timer so next nudge is a full interval away.
        intervalTimerHandler.removeCallbacksAndMessages(null)
        scheduleIntervalNudge()

        val intent = Intent(this, com.example.neckguard.CheckPostureActivity::class.java).apply {

            flags = Intent.FLAG_ACTIVITY_NEW_TASK

            putExtra("phone_pitch", com.example.neckguard.engine.PostureEngine.currentPitch)

        }

        val pendingIntent = android.app.PendingIntent.getActivity(

            this, 0, intent,

            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT

        )



        // -- Real-time posture diagnosis --
        val flexion = com.example.neckguard.engine.PostureEngine.lastNeckFlexion
        val postureState = lastPostureState
        val intervalMins = getUsageThresholdMs(prefs) / 60_000

        val spinalLoadKg = when {
            flexion < 15f -> 12
            flexion < 25f -> 18
            flexion < 35f -> 22
            flexion < 45f -> 27
            else          -> 32
        }

        // Gen-Z cheesy notification (NO scientific data directly here!)
        val diagnosisLine = "Hey, maybe you're in a bad posture 👀 let's check"
        
        val nudgeTitles = listOf(
            "Your posture is calling 📞",
            "Goblin mode detected? 👀",
            "Hey, how's your neck? 🤔",
            "Slouch alert? Let's find out 🔎",
            "Neck check bestie ✨"
        )

        val expandedBodies = listOf(
            "Your posture is calling... all you need to do is click this!! 🔥",
            "Are we slouching? The sensors say maybe. Let's do a 2-second scan to be sure 📸",
            "Bestie, it's posture check time. Tap to see if you're giving shrimp posture 🦐"
        )

        val lastNudgeTitleIdx = prefs.getInt("lastNudgeTitleIdx", -1)
        val lastExpandedIdx   = prefs.getInt("lastExpandedIdx_${postureState.name}", -1)
        var titleIdx = nudgeTitles.indices.random()
        while (titleIdx == lastNudgeTitleIdx && nudgeTitles.size > 1) titleIdx = nudgeTitles.indices.random()
        var expandedIdx = expandedBodies.indices.random()
        while (expandedIdx == lastExpandedIdx && expandedBodies.size > 1) expandedIdx = expandedBodies.indices.random()
        prefs.edit()
            .putInt("lastNudgeTitleIdx", titleIdx)
            .putInt("lastExpandedIdx_${postureState.name}", expandedIdx)
            .apply()

        val builder = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(nudgeTitles[titleIdx])
            .setContentText(diagnosisLine)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(expandedBodies[expandedIdx])
                    .setSummaryText(diagnosisLine)
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setTimeoutAfter(120_000L)



        val manager = getSystemService(NotificationManager::class.java)

        manager.notify(ALERT_NOTIFICATION_ID, builder.build())

    }



    // ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ Periodic score flush ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ



    private var lastFlushMs = 0L



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

     * final complete row with the same `timestampStartMs` key ΓÇö naturally

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



    // ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ Cleanup & restart ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ



    override fun onDestroy() {

        Log.d(TAG, "Service Destroyed")

        stopWatchdog()
        intervalTimerHandler.removeCallbacksAndMessages(null)
        dutyCycleHandler.removeCallbacksAndMessages(null)

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



    private fun scheduleIntervalNudge() {
        intervalTimerHandler.removeCallbacksAndMessages(null)
        val intervalMs = getUsageThresholdMs(prefs)
        Log.d(TAG, "Next interval nudge in ${intervalMs / 60_000}min")
        intervalTimerHandler.postDelayed(intervalTimerRunnable, intervalMs)
    }

    /** Schedules a restart via AlarmManager ΓåÆ BroadcastReceiver relay. */

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



    // ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ Watchdog lifecycle ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ



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



    // ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ Notification helpers ΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇΓöÇ



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

            .setSmallIcon(R.drawable.ic_notification)

            .setPriority(NotificationCompat.PRIORITY_MIN)

            .setOngoing(true)

            .setContentIntent(openAppPendingIntent)

            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

            .addAction(android.R.drawable.ic_media_pause, "Pause Shield", pausePendingIntent)

            .build()

    }

}

