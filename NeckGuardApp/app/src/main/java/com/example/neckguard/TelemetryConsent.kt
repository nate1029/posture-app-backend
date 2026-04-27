package com.example.neckguard

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * User-controlled gate for crash + product telemetry.
 *
 * Persisted in [SecurePrefs] under [KEY]; default-on so we get crash visibility
 * out-of-the-box, opt-out via Settings.
 *
 * When set to `false` we:
 *   • Disable Firebase Crashlytics collection (already-buffered crashes still
 *     send on next launch, but no new ones are captured).
 *   • Disable Firebase Analytics collection (no events, no install attribution).
 *
 * We do NOT disable our own Supabase crash uploader from here — it's already
 * gated by user authentication (S-01) so it can't fire for users who haven't
 * explicitly logged in. If you want to extend this gate to cover Supabase
 * crash uploads too, check [isEnabled] inside `CrashReporter.flushPendingCrash`.
 */
object TelemetryConsent {
    private const val KEY = "analytics_consent"
    private const val DEFAULT_ENABLED = true

    fun isEnabled(prefs: SharedPreferences): Boolean = prefs.getBoolean(KEY, DEFAULT_ENABLED)

    fun setEnabled(context: Context, prefs: SharedPreferences, enabled: Boolean) {
        prefs.edit().putBoolean(KEY, enabled).apply()
        apply(context, enabled)
    }

    /**
     * Applies the supplied state to the Firebase SDKs. Safe to call multiple
     * times; idempotent. Each SDK call is wrapped in try/catch because
     * Firebase can be uninitialised in obscure startup paths (instrumentation
     * tests, very early process death) and we don't want telemetry plumbing
     * to crash the app.
     */
    fun apply(context: Context, enabled: Boolean) {
        try {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(enabled)
        } catch (_: Throwable) {}
        try {
            FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(enabled)
        } catch (_: Throwable) {}
    }
}
