package com.example.neckguard

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object SupabaseClient {
    private const val TAG = "SupabaseClient"
    private val BASE_URL = BuildConfig.SUPABASE_URL
    private val API_KEY = BuildConfig.SUPABASE_ANON_KEY

    // Holds the session dynamically after login/signup
    var accessToken: String? = null
    var userId: String? = null

    /**
     * Attempts to Sign Up the user. If they already exist, it attempts to Log them in instead.
     * Returns null if successful. Returns error String if failed.
     */
    suspend fun authenticate(email: String, pass: String): String? = withContext(Dispatchers.IO) {
        val jsonBody = JSONObject().apply {
            put("email", email)
            put("password", pass)
        }

        try {
            // First attempt: Sign Up
            val signupUrl = URL("$BASE_URL/auth/v1/signup")
            val signupConn = setupConnection(signupUrl)
            writeBody(signupConn, jsonBody.toString())

            val signupCode = signupConn.responseCode
            if (signupCode in 200..299) {
                val success = parseAuthResponse(signupConn)
                if (success) return@withContext null // Success!
                // If it succeeded but no access_token was returned, Email Confirmation is blocking it!
                return@withContext "Please completely disable 'Confirm Email' in your Supabase Auth Providers setting!"
            } 
            
            // Read signup error to see if it's just "User exists"
            val signupErrorStr = signupConn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            val signupJsonStr = try { JSONObject(signupErrorStr).optString("msg", "") } catch(e:Exception) {""}
            
            if (!signupJsonStr.contains("already registered", true) && !signupJsonStr.contains("already exists", true)) {
                return@withContext signupJsonStr.ifEmpty { "Signup Failed: $signupCode" }
            }

            // If user exists, attempt Login
            val loginUrl = URL("$BASE_URL/auth/v1/token?grant_type=password")
            val loginConn = setupConnection(loginUrl)
            writeBody(loginConn, jsonBody.toString())

            val loginCode = loginConn.responseCode
            if (loginCode in 200..299) {
                val success = parseAuthResponse(loginConn)
                if (success) return@withContext null // Success!
                return@withContext "Login successful but failed to read token."
            }

            val loginErrorStr = loginConn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            val loginErrorMsg = try { JSONObject(loginErrorStr).optString("error_description", "") } catch(e:Exception) {""}
            
            return@withContext loginErrorMsg.ifEmpty { "Incorrect email or password." }

        } catch (e: Exception) {
            Log.e(TAG, "Auth exception", e)
            return@withContext "Network error: ${e.message}"
        }
    }

    /**
     * Pushes the completed Onboarding profile to the 'user_profiles' table securely using their Auth Token.
     */
    suspend fun saveProfile(
        name: String, age: String, vibe: String, context: String, health: String, interval: Long
    ): Boolean = withContext(Dispatchers.IO) {
        if (userId == null || accessToken == null) {
            Log.e(TAG, "Cannot save profile without being authenticated!")
            return@withContext false
        }

        try {
            val url = URL("$BASE_URL/rest/v1/user_profiles") // Automatically mapping to your DB Table
            val conn = setupConnection(url)
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("Prefer", "resolution=merge-duplicates") // Perform UPSERT
            
            val jsonBody = JSONObject().apply {
                put("user_id", userId) // Maps exact row to Auth logic automatically inside Supabase
                put("name", name)
                put("age_group", age)
                put("notification_vibe", vibe)
                put("usage_context", context)
                put("neck_health", health)
                put("check_interval_ms", interval)
            }

            writeBody(conn, jsonBody.toString())

            // 201 Created is typical for Postgres Inserts via REST
            val success = conn.responseCode in 200..299
            Log.d(TAG, "Profile Save result: ${conn.responseCode} - $success")
            return@withContext success

        } catch (e: Exception) {
            Log.e(TAG, "Database Insertion exception", e)
            return@withContext false
        }
    }

    /**
     * Uploads unhandled JVM crashes directly to a Postgres table.
     * Prevents reliance on Google Services/Firebase Crashlytics for raw stability indexing.
     */
    suspend fun uploadCrashLog(stackTrace: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/rest/v1/crash_reports")
            val conn = setupConnection(url)
            
            // Note: Does not require "Authorization: Bearer <token>" if the table
            // is configured to allow anon inserts, OR we can append it if available.
            if (accessToken != null) {
                conn.setRequestProperty("Authorization", "Bearer $accessToken")
            }
            
            val jsonBody = JSONObject().apply {
                put("user_id", userId ?: "unauthenticated")
                put("stack_trace", stackTrace)
                put("device_info", android.os.Build.MODEL + " (API " + android.os.Build.VERSION.SDK_INT + ")")
            }

            writeBody(conn, jsonBody.toString())

            val success = conn.responseCode in 200..299
            Log.d(TAG, "Crash Upload result: ${conn.responseCode} - $success")
            return@withContext success

        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload crash log", e)
            return@withContext false
        }
    }

    private fun setupConnection(url: URL): HttpURLConnection {
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("apikey", API_KEY)
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        return conn
    }

    private fun writeBody(conn: HttpURLConnection, body: String) {
        OutputStreamWriter(conn.outputStream).use { it.write(body) }
    }

    private fun parseAuthResponse(conn: HttpURLConnection): Boolean {
        val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
        val responseJson = JSONObject(responseStr)
        accessToken = responseJson.optString("access_token")
        
        // Handle variations between Login vs Signup payload shapes securely
        val userObj = responseJson.optJSONObject("user")
        userId = userObj?.optString("id") ?: responseJson.optString("user_id", null)
        
        Log.d(TAG, "Authenticated! User ID: $userId")
        return accessToken.isNullOrEmpty().not() && userId.isNullOrEmpty().not()
    }
}
