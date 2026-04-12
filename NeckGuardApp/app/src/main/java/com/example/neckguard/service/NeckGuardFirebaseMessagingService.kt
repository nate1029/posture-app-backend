package com.example.neckguard.service

import android.app.NotificationManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class NeckGuardFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
    }

    /**
     * Called when a new FCM registration token is generated.
     * We should ideally save this to Supabase so the dashboard knows who to target.
     */
    override fun onNewToken(token: String) {
        Log.d(TAG, "Refreshed token: $token")
        // Normally, you would inject UserRepository and call updateFcmToken() here 
        // to sync the token back up to Supabase.
    }

    /**
     * Called when a notification arrives while the app is actively running in the foreground.
     * Background notifications are handled directly by the Android System UI.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Check if message contains a data payload (Custom logic triggers)
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
            // e.g. Triggering an immediate posture check remotely via JSON payload
        }

        // Check if message contains a notification payload (Standard Text Popup)
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            showNotification(it.title ?: "NeckGuard Announcement", it.body ?: "")
        }
    }

    private fun showNotification(title: String, body: String) {
        // Re-using the Alert channel so the user feels it
        val builder = NotificationCompat.Builder(this, "neckguard_alert_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(System.currentTimeMillis().toInt(), builder.build())
    }
}
