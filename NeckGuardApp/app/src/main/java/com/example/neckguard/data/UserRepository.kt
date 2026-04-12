package com.example.neckguard.data

import android.content.SharedPreferences
import com.example.neckguard.SupabaseClient

class UserRepository(private val prefs: SharedPreferences) {
    
    fun hydrateSession(): Boolean {
        val token = prefs.getString("SupabaseToken", null)
        val uid = prefs.getString("SupabaseUserId", null)
        if (token != null && uid != null) {
            SupabaseClient.accessToken = token
            SupabaseClient.userId = uid
            return true
        }
        return false
    }

    fun hasCompletedOnboarding(): Boolean {
        return prefs.getBoolean("OnboardingComplete", false)
    }

    fun completeOnboarding() {
        prefs.edit().putBoolean("OnboardingComplete", true).apply()
    }

    fun logout() {
        prefs.edit().clear().apply()
        SupabaseClient.accessToken = null
        SupabaseClient.userId = null
    }

    fun saveTokens(token: String, userId: String) {
        prefs.edit()
            .putString("SupabaseToken", token)
            .putString("SupabaseUserId", userId)
            .apply()
    }
}
