package com.example.neckguard

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Process-wide cache for EncryptedSharedPreferences with self-healing fallback.
 *
 * KeyStore failures are real on certain Xiaomi/MTK firmwares, in direct-boot,
 * and after a master-key rotation that left a corrupted file. We can't refuse
 * to start the app for those users, but we can:
 *
 *   1. **Be loud about it** — surface [isUsingFallback] so the UI shows the
 *      user a banner explaining their data isn't currently encrypted at rest,
 *      and so [TelemetryConsent] can choose to skip persistence of sensitive
 *      values (auth tokens, OAuth state) if it wants stricter handling.
 *
 *   2. **Not be permanent** — every cold start retries the secure path. If
 *      the KeyStore comes back online, [createStoreWithMigration] copies any
 *      data we wrote to the plaintext fallback over to the encrypted store
 *      and wipes the fallback file. From the next launch onwards the user
 *      is back on the secure path, with their data intact.
 *
 * The retry happens at process start (and only at process start) because
 * [cached] is held for the process lifetime — we never thrash between secure
 * and fallback within a single run.
 */
object SecurePrefs {
    private const val TAG = "SecurePrefs"
    private const val PREFS_NAME = "neckguard_secure_prefs"
    private const val FALLBACK_PREFS_NAME = "neckguard_prefs_fallback"

    @Volatile private var cached: SharedPreferences? = null

    /**
     * True if [get] returned an unencrypted fallback because the KeyStore was
     * unavailable on this run. Stays true for the rest of the process; flips
     * back to false on the next process restart if the KeyStore recovers.
     *
     * Read from Compose with `remember { mutableStateOf(SecurePrefs.isUsingFallback) }`
     * — the value is set during [get] before any composable runs, so a
     * snapshot read is safe.
     */
    @Volatile var isUsingFallback: Boolean = false
        private set

    fun get(context: Context): SharedPreferences {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: createStoreWithMigration(context.applicationContext).also { cached = it }
        }
    }

    /**
     * Builds the SharedPreferences instance for this process. Tries the secure
     * path first; on success, also migrates any leftover data from a previous
     * fallback session into the encrypted store. On failure, returns the
     * plaintext fallback and flags [isUsingFallback].
     */
    private fun createStoreWithMigration(appContext: Context): SharedPreferences {
        val secure = tryCreateSecure(appContext)
        if (secure != null) {
            // KeyStore is healthy this run. Sweep up any data left in a
            // previous run's fallback file and merge it back in.
            try {
                migrateFallbackIfPresent(appContext, secure)
            } catch (t: Throwable) {
                Log.w(TAG, "Fallback migration failed: ${t.message}")
                reportNonFatal(t, "secureprefs_migration_failed")
            }
            return secure
        }

        // Secure path is broken on this run. Fall back to plaintext, surface
        // the event, and let callers / UI react.
        isUsingFallback = true
        Log.w(TAG, "EncryptedSharedPreferences unavailable; using plain fallback.")
        reportFallback()
        return appContext.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun tryCreateSecure(appContext: Context): SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                appContext,
                PREFS_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(TAG, "Secure path unavailable: ${e.message}")
            null
        }
    }

    /**
     * If a previous run wrote data to the plaintext fallback, copy it into
     * the encrypted store and wipe the fallback file. Migration is one-shot
     * per recovery; once the fallback file is empty, future calls are no-ops.
     *
     * Conflict policy: encrypted-store wins. If the same key exists in both
     * (because a user wrote to both stores across multiple runs), the
     * encrypted value is the source of truth and the fallback value is
     * silently dropped. This biases toward "data the user wrote on a healthy
     * device" rather than "data the user wrote on a broken device".
     */
    private fun migrateFallbackIfPresent(
        appContext: Context,
        secure: SharedPreferences
    ) {
        val fallback = appContext.getSharedPreferences(FALLBACK_PREFS_NAME, Context.MODE_PRIVATE)
        val fallbackEntries = fallback.all
        if (fallbackEntries.isEmpty()) return

        Log.i(TAG, "Migrating ${fallbackEntries.size} key(s) from fallback into secure store.")
        val editor = secure.edit()
        var migrated = 0
        for ((key, value) in fallbackEntries) {
            // Encrypted-store wins on conflict. `secure.contains(key)` is the
            // cheap check — `secure.all` would force a full decrypt sweep.
            if (secure.contains(key)) continue
            when (value) {
                is String  -> editor.putString(key, value)
                is Int     -> editor.putInt(key, value)
                is Long    -> editor.putLong(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Float   -> editor.putFloat(key, value)
                is Set<*>  -> {
                    @Suppress("UNCHECKED_CAST")
                    editor.putStringSet(key, value as Set<String>)
                }
                else -> {
                    // Defensive: skip unknown types rather than crash.
                    Log.w(TAG, "Skipping unsupported fallback type for key=$key")
                }
            }
            migrated++
        }
        editor.apply()

        // Atomically wipe the fallback so we don't repeat the migration on
        // the next launch.
        fallback.edit().clear().apply()

        try {
            FirebaseCrashlytics.getInstance().apply {
                log("[SecurePrefs] migrated $migrated key(s) from fallback")
                setCustomKey("secureprefs_recovered", true)
            }
        } catch (_: Throwable) {}
    }

    private fun reportFallback() {
        try {
            FirebaseCrashlytics.getInstance().apply {
                setCustomKey("secureprefs_fallback", true)
                recordException(SecurePrefsFallbackException())
            }
        } catch (_: Throwable) {
            // Crashlytics not initialised yet (very early startup) — losing
            // this signal is acceptable; the next session will report it.
        }
    }

    private fun reportNonFatal(t: Throwable, signature: String) {
        try {
            FirebaseCrashlytics.getInstance().apply {
                log("[SecurePrefs] $signature")
                recordException(t)
            }
        } catch (_: Throwable) {}
    }

    /**
     * Wrapper exception so Crashlytics groups KeyStore-fallback events under
     * a single signature, distinct from other [Exception] we might record.
     */
    private class SecurePrefsFallbackException :
        RuntimeException("KeyStore-backed prefs unavailable; using plaintext fallback")
}
