package com.example.neckguard.data

import android.content.SharedPreferences
import com.example.neckguard.SupabaseClient

class UserRepository(private val prefs: SharedPreferences) {

    /**
     * Checks if the user is authenticated. Firebase Auth is the source of
     * truth for "is the user logged in?". Supabase tokens are hydrated
     * best-effort from prefs (needed for REST calls, but the background
     * service / boot receiver can run without them).
     */
    fun hydrateSession(): Boolean {
        // Firebase Auth is the primary gate
        val firebaseUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            ?: return false

        // Best-effort Supabase hydration
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        val uid = prefs.getString(KEY_USER_ID, null)
        val refresh = prefs.getString(KEY_REFRESH_TOKEN, null)
        if (!token.isNullOrEmpty() && !uid.isNullOrEmpty()) {
            SupabaseClient.accessToken = token
            SupabaseClient.userId = uid
            SupabaseClient.refreshToken = refresh
        }

        return true
    }

    fun hasCompletedOnboarding(): Boolean {
        return prefs.getBoolean("OnboardingComplete", false)
    }

    fun completeOnboarding() {
        prefs.edit().putBoolean("OnboardingComplete", true).apply()
    }

    fun logout(context: android.content.Context) {
        com.example.neckguard.FirebaseAuthManager.signOut(context)
        // clearSession also wipes prefs via the persistence hook
    }

    fun saveTokens(accessToken: String, refreshToken: String?, userId: String) {
        SupabaseClient.accessToken = accessToken
        SupabaseClient.refreshToken = refreshToken
        SupabaseClient.userId = userId
        persistSession(accessToken, refreshToken, userId)
    }

    private fun persistSession(access: String?, refresh: String?, uid: String?) {
        persistSessionTo(prefs, access, refresh, uid)
    }

    // --- Gamification & Day Rollover Logic ---

    /**
     * Atomic increments for the gamification counters. Previously each was
     * a non-atomic read-modify-write `prefs.edit().putInt(K, get(K) + amt)`,
     * which silently dropped concurrent updates if e.g. two exercises were
     * marked complete in rapid succession from different scopes (UI + the
     * service-scope coroutine). The synchronized block here is process-wide
     * cheap (no IPC) and serialises only the read+write window. (B-28)
     */
    val lifetimePoints: Int get() = prefs.getInt("LifetimePoints", 0)
    fun addPoints(amount: Int) = synchronized(COUNTER_LOCK) {
        prefs.edit().putInt("LifetimePoints", prefs.getInt("LifetimePoints", 0) + amount).apply()
    }

    val totalExercisesDone: Int get() = prefs.getInt("TotalExercisesDone", 0)
    fun addExerciseDone() = synchronized(COUNTER_LOCK) {
        prefs.edit().putInt("TotalExercisesDone", prefs.getInt("TotalExercisesDone", 0) + 1).apply()
    }

    /**
     * Yesterday's posture score, 0..100. Returns [NO_YESTERDAY_DATA] (-1)
     * when there is no logged data for yesterday — fresh install, app
     * paused all day, etc. Callers must check for the sentinel before
     * computing deltas or showing "vs yesterday" copy.
     */
    val yesterdayScore: Int get() = prefs.getInt("YesterdayScore", NO_YESTERDAY_DATA)
    fun setYesterdayScore(score: Int) = prefs.edit().putInt("YesterdayScore", score).apply()

    val currentDayOfYear: Int get() = prefs.getInt("CurrentDayOfYear", -1)
    fun setCurrentDayOfYear(day: Int) = prefs.edit().putInt("CurrentDayOfYear", day).apply()

    /**
     * Today's assigned & completed exercises now use [SharedPreferences.putStringSet]
     * instead of joining titles with commas. The CSV format would have
     * silently broken the moment a remote-config-driven exercise title
     * contained a comma (B-20). Read paths transparently migrate any
     * legacy CSV value the first time they're touched.
     */
    val assignedExercisesList: List<String> get() = readListPref(KEY_ASSIGNED_EX)
    fun setAssignedExercisesList(list: List<String>) = writeListPref(KEY_ASSIGNED_EX, list.toSet())

    val completedExercisesTodayList: Set<String> get() = readListPref(KEY_COMPLETED_EX_TODAY).toSet()
    fun setCompletedExercisesTodayList(set: Set<String>) = writeListPref(KEY_COMPLETED_EX_TODAY, set)
    fun addCompletedExerciseToday(id: String) = synchronized(COUNTER_LOCK) {
        val current = completedExercisesTodayList.toMutableSet()
        if (current.add(id)) writeListPref(KEY_COMPLETED_EX_TODAY, current)
    }

    /**
     * Reads a string-set pref, transparently migrating any legacy CSV value
     * left over from an earlier build. Returns the set as a List for
     * positional use (assigned-exercises display order).
     */
    private fun readListPref(key: String): List<String> {
        val asSet = prefs.getStringSet(key, null)
        if (asSet != null) return asSet.toList()

        val legacy = prefs.getString(key, null)
        if (legacy.isNullOrBlank()) return emptyList()

        // Migrate the legacy CSV value over to a string-set, in place.
        val items = legacy.split(",").filter { it.isNotBlank() }
        prefs.edit()
            .remove(key)
            .putStringSet(key, items.toSet())
            .apply()
        return items
    }

    private fun writeListPref(key: String, set: Set<String>) {
        prefs.edit().putStringSet(key, set).apply()
    }

    /**
     * Tri-state result from [restoreProfileFromSupabase]. See [SupabaseClient.ProfileResult].
     */
    sealed class RestoreResult {
        /** Profile existed and has been written to local prefs. */
        object Restored : RestoreResult()
        /** Authenticated, but no profile row — user should be onboarded. */
        object NoProfile : RestoreResult()
        /** Network / server / parse failure — caller must NOT treat as NoProfile. */
        object TransientError : RestoreResult()
    }

    // ── Pending Profile Sync (B-26) ──────────────────────────────────────
    // Set when onboarding completed locally but the upload to Supabase
    // failed (server 5xx, network drop). Cleared when the deferred upload
    // eventually succeeds. Surfaced via [hasPendingProfileSync] so
    // [com.example.neckguard.ui.MainViewModel.checkStatus] can drive the
    // retry on the next launch.

    fun hasPendingProfileSync(): Boolean = prefs.getBoolean(KEY_PENDING_PROFILE_SYNC, false)

    private fun setPendingProfileSync(pending: Boolean) {
        prefs.edit().putBoolean(KEY_PENDING_PROFILE_SYNC, pending).apply()
    }

    /**
     * Reads the locally-stored profile (written during onboarding) and
     * attempts to upload it to Supabase. Returns true if the upload
     * succeeded; the pending flag is cleared on success.
     */
    suspend fun retryPendingProfileSync(): Boolean {
        if (!hasPendingProfileSync()) return false
        val name = prefs.getString("UserName", "") ?: ""
        val age = prefs.getString("UserAgeGrp", "") ?: ""
        val vibe = prefs.getString("UserVibe", "") ?: ""
        val ctx = prefs.getString("UserContext", "") ?: ""
        val health = prefs.getString("UserHealth", "") ?: ""
        val interval = prefs.getLong("IntervalPreferenceMs", 30 * 60 * 1000L)

        val ok = SupabaseClient.saveProfile(name, age, vibe, ctx, health, interval)
        if (ok) setPendingProfileSync(false)
        return ok
    }

    /**
     * Marks onboarding as complete locally (so the user can use the app
     * offline) and queues a Supabase profile sync for the next launch.
     * Called by [com.example.neckguard.ui.OnboardingScreen] when its own
     * direct upload returns false.
     */
    fun markOnboardingPendingSync(
        name: String, age: String, vibe: String, context: String, health: String, interval: Long
    ) {
        prefs.edit().apply {
            putString("UserName", name)
            putString("UserAgeGrp", age)
            putString("UserVibe", vibe)
            putString("UserContext", context)
            putString("UserHealth", health)
            putLong("IntervalPreferenceMs", interval)
            putBoolean("OnboardingComplete", true)
            putBoolean(KEY_PENDING_PROFILE_SYNC, true)
        }.apply()
    }

    /**
     * Attempts to restore the user's profile from Supabase after login.
     * If a profile exists server-side, we write it to local prefs and mark
     * onboarding as done.
     */
    suspend fun restoreProfileFromSupabase(): RestoreResult {
        return when (val result = SupabaseClient.fetchProfile()) {
            is SupabaseClient.ProfileResult.Found -> {
                val profile = result.profile
                prefs.edit().apply {
                    putString("UserName", profile.optString("name", ""))
                    putString("UserAgeGrp", profile.optString("age_group", ""))
                    putString("UserVibe", profile.optString("notification_vibe", ""))
                    putString("UserContext", profile.optString("usage_context", ""))
                    putString("UserHealth", profile.optString("neck_health", ""))
                    putLong("IntervalPreferenceMs", profile.optLong("check_interval_ms", 30 * 60 * 1000L))
                    putBoolean("OnboardingComplete", true)
                }.apply()
                RestoreResult.Restored
            }
            SupabaseClient.ProfileResult.NotFound -> RestoreResult.NoProfile
            SupabaseClient.ProfileResult.Error -> RestoreResult.TransientError
        }
    }

    companion object {
        private const val KEY_ACCESS_TOKEN = "SupabaseToken"
        private const val KEY_REFRESH_TOKEN = "SupabaseRefreshToken"
        private const val KEY_USER_ID = "SupabaseUserId"
        private const val KEY_ASSIGNED_EX = "AssignedExercises"
        private const val KEY_COMPLETED_EX_TODAY = "CompletedExercisesToday"
        private const val KEY_PENDING_PROFILE_SYNC = "PendingProfileSync"

        /**
         * Sentinel stored under "YesterdayScore" when there is no logged
         * data for the previous day. Negative so it can never collide
         * with a real 0..100 score. Consumed by [yesterdayScore] /
         * [setYesterdayScore].
         */
        const val NO_YESTERDAY_DATA = -1

        /** Process-wide lock for atomic counter updates (B-28). */
        private val COUNTER_LOCK = Any()

        /**
         * Installed once from [com.example.neckguard.NeckGuardApplication.onCreate]
         * so any caller — login, OAuth callback, refresh, or logout — automatically
         * mirrors session state to encrypted prefs without needing to know about it.
         */
        fun installSessionPersistence(prefs: SharedPreferences) {
            SupabaseClient.installPersistenceHook { access, refresh, uid ->
                persistSessionTo(prefs, access, refresh, uid)
            }
        }

        private fun persistSessionTo(
            prefs: SharedPreferences,
            access: String?,
            refresh: String?,
            uid: String?
        ) {
            val editor = prefs.edit()
            if (access.isNullOrEmpty()) editor.remove(KEY_ACCESS_TOKEN) else editor.putString(KEY_ACCESS_TOKEN, access)
            if (refresh.isNullOrEmpty()) editor.remove(KEY_REFRESH_TOKEN) else editor.putString(KEY_REFRESH_TOKEN, refresh)
            if (uid.isNullOrEmpty()) editor.remove(KEY_USER_ID) else editor.putString(KEY_USER_ID, uid)
            editor.apply()
        }
    }
}
