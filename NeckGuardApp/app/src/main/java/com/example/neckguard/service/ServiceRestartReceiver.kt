package com.example.neckguard.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Dedicated BroadcastReceiver that restarts the NeckGuardService after it has been
 * killed by the OS (e.g. user swipes notification on Android 14+).
 *
 * WHY A RECEIVER INSTEAD OF STARTING DIRECTLY FROM ALARM?
 * On Android 12+, apps cannot start a ForegroundService directly from an AlarmManager
 * callback when the app is in the background (throws IllegalStateException silently).
 * However, a BroadcastReceiver's onReceive() runs with a brief foreground-execution
 * window, which DOES allow calling startForegroundService().
 */
class ServiceRestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.example.neckguard.RESTART_SERVICE") return

        Log.d("ServiceRestartReceiver", "Received restart signal — starting NeckGuardService")

        val serviceIntent = Intent(context, NeckGuardService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}
