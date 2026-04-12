package com.example.neckguard

import android.content.Context
import android.util.Log
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.io.StringWriter

object CrashReporter {
    private const val TAG = "CrashReporter"
    private const val PREFS_NAME = "neckguard_crashes"
    private const val KEY_PENDING_CRASH = "pending_crash_trace"

    fun initialize(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        // 1. Hook the JVM Global Error Handler
        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            Log.e(TAG, "FATAL CRASH DETECTED. Intercepting for telemetry...")
            
            // Extract the bloody details
            val sw = StringWriter()
            exception.printStackTrace(PrintWriter(sw))
            val trace = sw.toString()

            // Save to disk IMMEDIATELY synchronously before the OS kills us
            prefs.edit().putString(KEY_PENDING_CRASH, trace).commit() 

            // Hand back to Android so it can show the standard "App has stopped" dialog
            defaultHandler?.uncaughtException(thread, exception)
        }

        // 2. Upload pending crashes from previous sessions
        val pendingCrash = prefs.getString(KEY_PENDING_CRASH, null)
        if (!pendingCrash.isNullOrEmpty()) {
            Log.i(TAG, "Found pending crash report from previous run. Uploading to Supabase...")
            
            @OptIn(DelicateCoroutinesApi::class)
            GlobalScope.launch {
                val success = SupabaseClient.uploadCrashLog(pendingCrash)
                if (success) {
                    prefs.edit().remove(KEY_PENDING_CRASH).apply()
                    Log.i(TAG, "Crash report successfully flushed to servers.")
                }
            }
        }
    }
}
