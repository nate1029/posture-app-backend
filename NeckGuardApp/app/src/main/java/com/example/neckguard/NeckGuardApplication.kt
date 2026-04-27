package com.example.neckguard

import android.app.Application
import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings

/**
 * Application-level startup. Runs exactly once per process, BEFORE any Activity,
 * Service, or BroadcastReceiver is created.
 *
 * Centralises all SDK initialisations so they run once and benefit every entry point
 * (MainActivity, NeckGuardService, BootReceiver, etc.).
 */
class NeckGuardApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // ── 1. Firebase Crashlytics ──
        // Apply user telemetry consent (default-on; user can opt out in Settings).
        // We always set the custom keys, even if collection is disabled, because
        // Firebase Crashlytics still sends crashes that occurred before opt-out
        // until they're flushed.
        try {
            val crashlytics = FirebaseCrashlytics.getInstance()
            crashlytics.setCustomKey("app_version", BuildConfig.VERSION_NAME)
            crashlytics.setCustomKey("build_type", BuildConfig.BUILD_TYPE)
            Log.d(TAG, "Firebase Crashlytics initialized")
        } catch (t: Throwable) {
            Log.w(TAG, "Crashlytics init failed: ${t.message}")
        }

        // ── 2. Firebase Analytics (auto-initialized by the SDK, but we grab the instance) ──
        try {
            FirebaseAnalytics.getInstance(this)
            NudgeAnalytics.init(this)
            Log.d(TAG, "Firebase Analytics initialized")
        } catch (t: Throwable) {
            Log.w(TAG, "Analytics init failed: ${t.message}")
        }

        // ── 2b. Honour the persisted telemetry-consent flag for both SDKs ──
        // This must run AFTER both SDKs are initialised so the disable call has
        // an effect. SecurePrefs may not be ready yet on direct-boot, in which
        // case we fall through to the SDK defaults (enabled).
        try {
            val prefs = SecurePrefs.get(this)
            TelemetryConsent.apply(this, TelemetryConsent.isEnabled(prefs))
        } catch (t: Throwable) {
            Log.w(TAG, "Telemetry consent apply failed: ${t.message}")
        }

        // ── 3. Firebase Remote Config ──
        // Lets us change nudge messages, scoring thresholds, and feature flags
        // without pushing an app update.
        //
        // The B-24 race ("RemoteConfig returns 0 before defaults activate")
        // is handled in [RemoteConfigManager] itself via hardcoded
        // fallback constants — the synchronous `setDefaults(int)` overload
        // we used initially was removed in firebase-config 22.x. Async
        // defaults still work; the engine just never sees the in-flight
        // empty-state because it goes through RemoteConfigManager's
        // defensive accessors.
        try {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            val configSettings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(3600) // 1 hour in production
                .build()
            remoteConfig.setConfigSettingsAsync(configSettings)
            remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)

            // Fetch latest values from Firebase on every cold start
            remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Remote Config fetched and activated")
                } else {
                    Log.w(TAG, "Remote Config fetch failed, using defaults")
                }
            }
            Log.d(TAG, "Firebase Remote Config initialized")
        } catch (t: Throwable) {
            Log.w(TAG, "Remote Config init failed: ${t.message}")
        }

        // ── 4. Legacy CrashReporter (Supabase) ──
        // Keep as secondary backup — uploads crash traces to our own DB
        CrashReporter.initialize(this)

        // ── 5. Rive Animation Engine ──
        try {
            app.rive.runtime.kotlin.core.Rive.init(this)
        } catch (t: Throwable) {
            Log.w(TAG, "Rive init failed: ${t.message}")
        }

        // ── 6. Warm EncryptedSharedPreferences singleton ──
        try { SecurePrefs.get(this) } catch (_: Throwable) { /* fallback already handled */ }

        // ── 7. Wire SupabaseClient → encrypted prefs persistence ──
        // Any session change (login, silent refresh, logout) now mirrors to disk
        // automatically. Installed exactly once per process so background entry
        // points (BootReceiver, ServiceRestartReceiver) don't reinstall it.
        try {
            com.example.neckguard.data.UserRepository.installSessionPersistence(SecurePrefs.get(this))
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to install session persistence hook: ${t.message}")
        }
    }

    companion object {
        private const val TAG = "NudgeUpApp"
    }
}
