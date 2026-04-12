package com.example.neckguard.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            val prefs = com.example.neckguard.SecurePrefs.get(context)
            val repo = com.example.neckguard.data.UserRepository(prefs)
            
            // Only restart the background privacy tracker if the user is ACTUALLY logged in and set up
            if (repo.hydrateSession() && repo.hasCompletedOnboarding()) {
                Log.d("BootReceiver", "Device rebooted. Restarting NeckGuard Service silently in background!")
                
                val serviceIntent = Intent(context, NeckGuardService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } else {
                Log.d("BootReceiver", "Device rebooted, but user is logged out or hasn't onboarded. Staying dormant.")
            }
        }
    }
}
