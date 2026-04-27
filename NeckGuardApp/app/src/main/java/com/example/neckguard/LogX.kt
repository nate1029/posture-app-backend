package com.example.neckguard

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Thin logging façade. Two responsibilities:
 *   1. Suppress verbose debug logs in release builds (logcat is reachable
 *      from a USB-connected dev machine, system bug-reports, and any process
 *      with READ_LOGS — so anything containing tokens, user IDs, URLs, or
 *      biometric measurements stays out).
 *   2. Forward production errors to Crashlytics instead of just logcat so
 *      we actually find out about them.
 *
 * Use [d] for chatty debug output (lifecycle, state transitions, sensor
 * readings). Use [w] for recoverable issues. Use [e] for unexpected
 * exceptions; [e] always reports to Crashlytics regardless of build type.
 */
internal object LogX {
    fun d(tag: String, msg: String) {
        if (BuildConfig.DEBUG) Log.d(tag, msg)
    }

    fun w(tag: String, msg: String, t: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (t != null) Log.w(tag, msg, t) else Log.w(tag, msg)
        }
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        if (BuildConfig.DEBUG) {
            if (t != null) Log.e(tag, msg, t) else Log.e(tag, msg)
        }
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.log("[$tag] $msg")
            if (t != null) crashlytics.recordException(t)
        } catch (_: Throwable) {
            // Crashlytics may not be initialised yet during the very early
            // startup window — losing this signal is acceptable.
        }
    }
}
