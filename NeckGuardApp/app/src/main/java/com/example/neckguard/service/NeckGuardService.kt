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
import com.example.neckguard.engine.PostureEngine

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
    
    // Posture tracking
    private var poorPostureSessionStart = 0L
    private var lastAlertTimeMs = 0L

    private var lastGx = 0f
    private var lastGy = 0f
    private var lastGz = 0f

    companion object {
        private const val TAG = "NeckGuardService"
        private const val CHANNEL_ID = "neckguard_service_channel"
        private const val ALERT_CHANNEL_ID = "neckguard_alert_channel"
        private const val NOTIFICATION_ID = 101
        private const val ALERT_NOTIFICATION_ID = 202
        private const val BATCH_LATENCY_US = 30_000_000 // 30s batching
        
        // Dynamic Threshold Getters
        fun getUsageThresholdMs(prefs: SharedPreferences): Long {
            return prefs.getLong("IntervalPreferenceMs", 30 * 60 * 1000L) 
        }

        fun getSustainedPoorMs(prefs: SharedPreferences): Long {
            // For production, this is 2 mins. But if interval is tiny (like 15s for testing), scale it down.
            val interval = getUsageThresholdMs(prefs)
            if (interval <= 30_000L) return 5000L // 5 sec poor posture for testing
            return 2 * 60 * 1000L // 2 mins 
        }

        fun getAlertCooldownMs(prefs: SharedPreferences): Long {
            val interval = getUsageThresholdMs(prefs)
            if (interval <= 30_000L) return 10000L // 10 sec cooldown for testing
            return 5 * 60 * 1000L // 5 mins
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service Created")
        prefs = getSharedPreferences("NeckGuardPrefs", Context.MODE_PRIVATE)
        cumulativeUsageTimerMs = prefs.getLong("CumulativeUsageMs", 0L)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroSensor = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) {
            Log.w(TAG, "Service restarted by OS (START_STICKY) with null intent")
        }
        
        val notification = createPersistentNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        
        registerScreenReceiver()
        transitionTo(AppState.MONITORING) // Default to monitoring when started (screen is presumably on)
        
        return START_STICKY
    }

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

    private fun transitionTo(newState: AppState) {
        if (currentState == newState) return
        Log.d(TAG, "Transitioning State: $currentState -> $newState")
        
        when (newState) {
            AppState.DORMANT -> {
                Log.d(TAG, "Screen OFF. Unregistering sensors, pausing timer.")
                sensorManager.unregisterListener(this)
                PostureEngine.resetFilter()
                
                // Commit cumulative usage time
                if (screenOnSessionStart != 0L) {
                    cumulativeUsageTimerMs += (System.currentTimeMillis() - screenOnSessionStart)
                    prefs.edit().putLong("CumulativeUsageMs", cumulativeUsageTimerMs).apply()
                    screenOnSessionStart = 0L
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
                timestampNS = event.timestamp, isLandscape = false
            )

            // Core Detection Logic
            val now = System.currentTimeMillis()
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
                // If posture recovers, break the sustained poor chain
                poorPostureSessionStart = 0L 
            }
        }
    }

    private fun sendPostureAlert() {
        Log.d(TAG, "FIRING POSTURE ALERT!")
        
        // 1. Reset all tracking variables immediately so the user gets a fresh 15-second (or 30-min) gap!
        lastAlertTimeMs = System.currentTimeMillis()
        poorPostureSessionStart = 0L
        cumulativeUsageTimerMs = 0L
        screenOnSessionStart = System.currentTimeMillis()
        prefs.edit().putLong("CumulativeUsageMs", 0L).apply()
        
        // 2. Attach PendingIntent to open Transparent Activity
        val intent = Intent(this, com.example.neckguard.CheckPostureActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 
            0, 
            intent, 
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

    override fun onDestroy() {
        Log.d(TAG, "Service Destroyed")
        screenReceiver?.let { unregisterReceiver(it) }
        sensorManager.unregisterListener(this)
        super.onDestroy()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            
            val bgChannel = NotificationChannel(CHANNEL_ID, "NeckGuard Active Status", NotificationManager.IMPORTANCE_LOW).apply {
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
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("NeckGuard is active")
            .setContentText("Monitoring posture in the background...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }
}
