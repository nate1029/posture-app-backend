package com.example.neckguard.service

import android.app.NotificationManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.neckguard.BuildConfig
import com.example.neckguard.SecurePrefs
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class NeckGuardFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "FCMService"
        private const val PENDING_FCM_TOKEN_KEY = "PendingFcmToken"
    }

    /**
     * Called when a new FCM registration token is generated. The token is
     * sensitive — anyone with it can target push notifications at this device,
     * and it's correlatable to the user. We:
     *   1. Persist it to encrypted prefs so we can sync it to Supabase later
     *      (currently a TODO — no `fcm_token` column in `user_profiles` yet).
     *   2. NEVER log the raw token. In debug builds we log a 6-char prefix
     *      to help engineering verify rotation; release builds log nothing.
     */
    override fun onNewToken(token: String) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "FCM token rotated (prefix=${token.take(6)}…)")
        }
        try {
            SecurePrefs.get(this).edit()
                .putString(PENDING_FCM_TOKEN_KEY, token)
                .apply()
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to persist FCM token: ${t.message}")
        }
        // TODO: when `user_profiles.fcm_token` column exists, push the token
        // to Supabase here. Suggested call site:
        //   GlobalScope.launch { SupabaseClient.uploadFcmToken(token) }
        // Gate by SupabaseClient.accessToken != null; otherwise leave the
        // token in encrypted prefs for the next post-login sync pass.
    }

    /**
     * Called when a notification arrives while the app is actively running in the foreground.
     * Background notifications are handled directly by the Android System UI.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "FCM message from: ${remoteMessage.from}")
        }

        if (remoteMessage.data.isNotEmpty() && BuildConfig.DEBUG) {
            // Data payload may include user IDs / experiment tags — debug only.
            Log.d(TAG, "Message data payload keys: ${remoteMessage.data.keys}")
        }

        remoteMessage.notification?.let {
            showNotification(it.title ?: "NudgeUp Announcement", it.body ?: "")
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
        // Use a fixed ID per push so duplicate dispatches replace each other
        // instead of stacking. (Was System.currentTimeMillis().toInt() — see
        // bug B-08 in the audit; truncation to Int could go negative and two
        // pushes within the same ms would collide.)
        manager.notify(NOTIFICATION_ID_PUSH, builder.build())
    }

    private val NOTIFICATION_ID_PUSH = 303
}
