package com.example.neckguard

import android.content.Context
import android.os.Bundle
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * Centralized analytics tracker for NudgeUp.
 * Logs custom events for key user actions so we can measure engagement
 * and iterate on features from the Firebase Dashboard.
 */
object NudgeAnalytics {
    private var analytics: FirebaseAnalytics? = null

    fun init(context: Context) {
        analytics = FirebaseAnalytics.getInstance(context)
    }

    /**
     * User completed the onboarding questionnaire.
     *
     * The user's name is intentionally NOT sent. Per Firebase Analytics
     * policy and Google Play Data Safety guidelines, personally
     * identifiable information (names, emails, exact ages, location)
     * must never be logged as event parameters. We track only the
     * coarse, non-identifying signals we actually need for product
     * decisions (age bucket and notification preference).
     */
    fun logOnboardingComplete(ageGroup: String, vibe: String) {
        analytics?.logEvent("onboarding_complete", Bundle().apply {
            putString("age_group", ageGroup)
            putString("notification_vibe", vibe)
        })
    }

    /** User logged in (email or Google) */
    fun logLogin(method: String) {
        analytics?.logEvent(FirebaseAnalytics.Event.LOGIN, Bundle().apply {
            putString(FirebaseAnalytics.Param.METHOD, method)
        })
    }

    /** User completed an exercise */
    fun logExerciseComplete(exerciseId: String, category: String) {
        analytics?.logEvent("exercise_complete", Bundle().apply {
            putString("exercise_id", exerciseId)
            putString("category", category)
            putLong("timestamp", System.currentTimeMillis())
        })
    }

    /** Posture check triggered (background sensor detected slouch) */
    fun logPostureCheckTriggered(phonePitch: Float, isSlouchDetected: Boolean) {
        analytics?.logEvent("posture_check", Bundle().apply {
            putFloat("phone_pitch", phonePitch)
            putBoolean("slouch_detected", isSlouchDetected)
        })
    }

    /** User toggled the monitoring service ON/OFF */
    fun logServiceToggle(isActive: Boolean) {
        analytics?.logEvent("service_toggle", Bundle().apply {
            putBoolean("is_active", isActive)
        })
    }

    /** User viewed a specific tab */
    fun logScreenView(screenName: String) {
        analytics?.logEvent(FirebaseAnalytics.Event.SCREEN_VIEW, Bundle().apply {
            putString(FirebaseAnalytics.Param.SCREEN_NAME, screenName)
        })
    }

    /** Daily posture score recorded */
    fun logDailyScore(score: Int, delta: Int, streakDays: Int) {
        analytics?.logEvent("daily_score", Bundle().apply {
            putInt("posture_score", score)
            putInt("score_delta", delta)
            putInt("streak_days", streakDays)
        })
    }

    /** User changed a setting */
    fun logSettingChanged(settingName: String, value: String) {
        analytics?.logEvent("setting_changed", Bundle().apply {
            putString("setting_name", settingName)
            putString("setting_value", value)
        })
    }

    /** Set the user ID for cross-referencing with Crashlytics */
    fun setUserId(userId: String) {
        analytics?.setUserId(userId)
    }
}
