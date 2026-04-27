package com.example.neckguard

import android.content.Context
import android.util.Log
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Captures uncaught JVM exceptions, persists them to encrypted storage so the
 * trace survives the OS killing us, and uploads the trace on the next launch.
 *
 * Why encrypted storage?
 *   Stack traces routinely contain user IDs, request URLs, query strings, and
 *   exception messages that quote attacker-controlled input. Storing them in
 *   plain `MODE_PRIVATE` SharedPreferences means anyone with `adb backup`
 *   access or root reads the lot. We use [SecurePrefs] (KeyStore-backed
 *   AES-GCM) instead.
 *
 * Why is `commit()` (sync) safe in a crash handler?
 *   The encryption is in-process (no IPC), and the master key is already
 *   cached because [SecurePrefs.get] is warmed during Application onCreate.
 *   Worst case the encrypted write costs ~10 ms; well under the OS's grace
 *   period before SIGKILL. If encryption itself throws (rare, e.g. KeyStore
 *   misbehaving) we silently drop the trace — Firebase Crashlytics has
 *   already captured the same crash via its own handler chain.
 */
object CrashReporter {
    private const val TAG = "CrashReporter"
    private const val KEY_PENDING_CRASH = "pending_crash_trace"

    @Volatile private var initialized = false
    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        // Guard against double-initialisation (Application.onCreate AND
        // MainActivity.onCreate both call us today; see bug B-07). Without
        // this guard we'd chain our handler twice and write the same trace
        // twice on every fatal crash.
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            initialized = true
        }

        appContext = context.applicationContext
        val prefs = SecurePrefs.get(context)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, exception ->
            Log.e(TAG, "FATAL CRASH DETECTED. Intercepting for telemetry...")

            try {
                val sw = StringWriter()
                exception.printStackTrace(PrintWriter(sw))
                val trace = sw.toString()

                // Synchronous write — needs to land on disk before the OS
                // kills the process. apply() is async and would lose the
                // trace if the JVM exits before the worker thread flushes.
                prefs.edit().putString(KEY_PENDING_CRASH, trace).commit()
            } catch (t: Throwable) {
                // Don't let our telemetry handler mask the real crash.
                // Crashlytics still has its own handler in the chain below
                // and will record this exception independently.
                Log.w(TAG, "Failed to persist crash trace: ${t.message}")
            }

            defaultHandler?.uncaughtException(thread, exception)
        }

        // Best-effort upload at startup. SupabaseClient refuses anonymous
        // inserts (S-01) and the user's session may not be hydrated yet,
        // so this often no-ops; in that case the trace stays on disk and
        // [flushPendingCrash] will retry post-authentication.
        flushPendingCrash()
    }

    /**
     * Attempts to upload a pending crash trace if one is on disk. Safe to
     * call from any context; called from [com.example.neckguard.ui.MainViewModel.checkStatus]
     * after a successful session hydration so the trace lands on Supabase
     * the moment we have a valid bearer token.
     *
     * No-op if there is no pending trace, the user is unauthenticated, or
     * the network is unavailable. Idempotent.
     */
    fun flushPendingCrash() {
        val ctx = appContext ?: return
        val prefs = SecurePrefs.get(ctx)
        val pendingCrash = prefs.getString(KEY_PENDING_CRASH, null) ?: return
        if (pendingCrash.isEmpty()) return

        @OptIn(DelicateCoroutinesApi::class)
        GlobalScope.launch {
            val success = SupabaseClient.uploadCrashLog(pendingCrash)
            if (success) {
                prefs.edit().remove(KEY_PENDING_CRASH).apply()
                Log.i(TAG, "Crash report flushed.")
            }
        }
    }
}
