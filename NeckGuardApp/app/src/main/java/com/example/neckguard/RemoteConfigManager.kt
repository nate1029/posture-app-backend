package com.example.neckguard

import com.google.firebase.remoteconfig.FirebaseRemoteConfig

/**
 * Single access point for all Firebase Remote Config values.
 *
 * Why fallback constants?
 *   `FirebaseRemoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)` is
 *   asynchronous; before the task completes (typically the first ~100 ms of
 *   the process) `getString` returns "" and `getDouble` / `getLong` return
 *   0. PostureEngine reading `slouch_angle_threshold == 0` would classify
 *   every reading as POOR, and the UI would render empty nudge text. We
 *   defend against that here by mirroring the values from
 *   res/xml/remote_config_defaults.xml as compile-time constants and
 *   falling back to them whenever the live read returns the zero / empty
 *   sentinel. See bug B-24.
 *
 *   Once the defaults activate (or the network fetch lands), live values
 *   take over automatically — the fallback only fires for the empty state.
 *
 * Keep [Defaults] in sync with [res/xml/remote_config_defaults.xml] —
 * neither file is a source of truth on its own; they have to match.
 */
object RemoteConfigManager {

    private val rc: FirebaseRemoteConfig
        get() = FirebaseRemoteConfig.getInstance()

    // ── Key constants ──────────────────────────────────────────────────────────
    object Key {
        const val NUDGE_CRITICAL_INSTRUCTION = "nudge_critical_instruction"
        const val NUDGE_CRITICAL_TAGS        = "nudge_critical_tags"
        const val NUDGE_CRITICAL_REASONING   = "nudge_critical_reasoning"

        const val NUDGE_MODERATE_INSTRUCTION = "nudge_moderate_instruction"
        const val NUDGE_MODERATE_TAGS        = "nudge_moderate_tags"
        const val NUDGE_MODERATE_REASONING   = "nudge_moderate_reasoning"

        const val DETECT_BAD_TITLE    = "detect_bad_title"
        const val DETECT_BAD_SEVERITY = "detect_bad_severity"

        const val DETECT_GOOD_TITLE    = "detect_good_title"
        const val DETECT_GOOD_SUBTITLE = "detect_good_subtitle"
        const val DETECT_GOOD_SEVERITY = "detect_good_severity"

        const val LEADERBOARD_ENABLED    = "leaderboard_enabled"
        const val FRIENDS_ENABLED        = "friends_enabled"

        const val SLOUCH_ANGLE_THRESHOLD = "slouch_angle_threshold"
        const val POINTS_PER_EXERCISE    = "points_per_exercise"

        const val EMERGENCY_MESSAGE = "emergency_message"
    }

    /**
     * Compile-time mirror of res/xml/remote_config_defaults.xml.
     * Used whenever the live RemoteConfig instance hasn't activated yet.
     */
    private object Defaults {
        const val NUDGE_CRITICAL_INSTRUCTION = "Raise your phone to eye level right now"
        const val NUDGE_CRITICAL_TAGS        = "🔴 Critical,⏱ 5 sec"
        const val NUDGE_CRITICAL_REASONING   = "Fixing your phone angle prevents text neck."

        const val NUDGE_MODERATE_INSTRUCTION = "Tuck your chin slightly inwards for 10s"
        const val NUDGE_MODERATE_TAGS        = "🔵 Moderate,⏱ 10 sec,Cervical"
        const val NUDGE_MODERATE_REASONING   = "Chin tucks strengthen deep neck flexors."

        const val DETECT_BAD_TITLE    = "Prolonged slouch detected yesterday"
        const val DETECT_BAD_SEVERITY = "Spinal imbalance • High severity"

        const val DETECT_GOOD_TITLE    = "Optimal alignment maintained"
        const val DETECT_GOOD_SUBTITLE = "Maintained perfect spine yesterday."
        const val DETECT_GOOD_SEVERITY = "Excellent • No action needed"

        const val SLOUCH_ANGLE_THRESHOLD = 25f
        const val POINTS_PER_EXERCISE    = 50
    }

    /** Reads a string from Remote Config, falling back to [fallback] if empty. */
    private fun stringOr(key: String, fallback: String): String {
        val v = rc.getString(key)
        return if (v.isNullOrEmpty()) fallback else v
    }

    // ── Nudge messages ─────────────────────────────────────────────────────────
    val nudgeCriticalInstruction: String get() = stringOr(Key.NUDGE_CRITICAL_INSTRUCTION, Defaults.NUDGE_CRITICAL_INSTRUCTION)
    val nudgeCriticalTags: List<String>  get() = stringOr(Key.NUDGE_CRITICAL_TAGS, Defaults.NUDGE_CRITICAL_TAGS).split(",")
    val nudgeCriticalReasoning: String   get() = stringOr(Key.NUDGE_CRITICAL_REASONING, Defaults.NUDGE_CRITICAL_REASONING)

    val nudgeModerateInstruction: String get() = stringOr(Key.NUDGE_MODERATE_INSTRUCTION, Defaults.NUDGE_MODERATE_INSTRUCTION)
    val nudgeModerateTags: List<String>  get() = stringOr(Key.NUDGE_MODERATE_TAGS, Defaults.NUDGE_MODERATE_TAGS).split(",")
    val nudgeModerateReasoning: String   get() = stringOr(Key.NUDGE_MODERATE_REASONING, Defaults.NUDGE_MODERATE_REASONING)

    // ── Detection messages ─────────────────────────────────────────────────────
    val detectBadTitle: String    get() = stringOr(Key.DETECT_BAD_TITLE, Defaults.DETECT_BAD_TITLE)
    val detectBadSeverity: String get() = stringOr(Key.DETECT_BAD_SEVERITY, Defaults.DETECT_BAD_SEVERITY)

    val detectGoodTitle: String    get() = stringOr(Key.DETECT_GOOD_TITLE, Defaults.DETECT_GOOD_TITLE)
    val detectGoodSubtitle: String get() = stringOr(Key.DETECT_GOOD_SUBTITLE, Defaults.DETECT_GOOD_SUBTITLE)
    val detectGoodSeverity: String get() = stringOr(Key.DETECT_GOOD_SEVERITY, Defaults.DETECT_GOOD_SEVERITY)

    // ── Feature flags ──────────────────────────────────────────────────────────
    // Booleans default to false in the SDK, which matches both XML defaults,
    // so no fallback wrapper needed.
    val isLeaderboardEnabled: Boolean get() = rc.getBoolean(Key.LEADERBOARD_ENABLED)
    val isFriendsEnabled: Boolean     get() = rc.getBoolean(Key.FRIENDS_ENABLED)

    // ── Scoring / detection thresholds ─────────────────────────────────────────
    /**
     * Neck flexion degrees at which posture transitions from MODERATE → POOR.
     * Default 25°. Falls back to the hardcoded constant if the live read
     * returns 0 (only happens in the brief window before defaults activate).
     */
    val slouchAngleThreshold: Float get() {
        val v = rc.getDouble(Key.SLOUCH_ANGLE_THRESHOLD).toFloat()
        return if (v <= 0f) Defaults.SLOUCH_ANGLE_THRESHOLD else v
    }

    /**
     * Points awarded per completed exercise. Default 50.
     *
     * Clamped to [1, 1000] for two reasons:
     *   1. A typo in the Firebase console (e.g. an extra zero) shouldn't
     *      let someone earn 5 million points for one Chin Tuck.
     *   2. `rc.getLong(...).toInt()` truncates silently on overflow, which
     *      could yield a negative reward. The lower bound prevents that.
     *
     * Falls back to [Defaults.POINTS_PER_EXERCISE] if the live read is the
     * 0-sentinel from the pre-activation window.
     */
    val pointsPerExercise: Int get() {
        val raw = rc.getLong(Key.POINTS_PER_EXERCISE).toInt()
        val resolved = if (raw <= 0) Defaults.POINTS_PER_EXERCISE else raw
        return resolved.coerceIn(1, 1000)
    }

    // ── Emergency broadcast ────────────────────────────────────────────────────
    /** Non-empty string = show an in-app emergency banner. Empty = no banner. */
    val emergencyMessage: String get() = rc.getString(Key.EMERGENCY_MESSAGE)
    val hasEmergencyMessage: Boolean get() = emergencyMessage.isNotBlank()
}
