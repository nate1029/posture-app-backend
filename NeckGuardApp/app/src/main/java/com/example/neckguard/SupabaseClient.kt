package com.example.neckguard

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object SupabaseClient {
    private const val TAG = "SupabaseClient"
    private val BASE_URL = BuildConfig.SUPABASE_URL
    private val API_KEY = BuildConfig.SUPABASE_ANON_KEY

    // Session state. @Volatile so writes from one thread are visible to readers
    // on other cores (the auth flow runs on Dispatchers.IO, the UI reads on Main).
    @Volatile var accessToken: String? = null
    @Volatile var refreshToken: String? = null
    @Volatile var userId: String? = null

    // Single-flight guard so concurrent 401s don't stampede the refresh endpoint.
    private val refreshMutex = Mutex()

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
            val signupUrl = URL("$BASE_URL/auth/v1/signup")
            val signupConn = openConnection(signupUrl, "POST")
            writeBody(signupConn, jsonBody.toString())

            val signupCode = signupConn.responseCode
            if (signupCode in 200..299) {
                val success = parseAuthResponse(signupConn)
                if (success) return@withContext null
                // Signup HTTP 2xx but no access_token in body = email confirmation
                // is required server-side. Tell the user what to do without leaking
                // implementation details about Supabase's config.
                return@withContext "Check your email to confirm your address, then come back and sign in."
            }

            val signupErrorStr = signupConn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            val signupJsonStr = try { JSONObject(signupErrorStr).optString("msg", "") } catch(e:Exception) {""}

            if (!signupJsonStr.contains("already registered", true) && !signupJsonStr.contains("already exists", true)) {
                return@withContext signupJsonStr.ifEmpty { "Signup Failed: $signupCode" }
            }

            val loginUrl = URL("$BASE_URL/auth/v1/token?grant_type=password")
            val loginConn = openConnection(loginUrl, "POST")
            writeBody(loginConn, jsonBody.toString())

            val loginCode = loginConn.responseCode
            if (loginCode in 200..299) {
                val success = parseAuthResponse(loginConn)
                if (success) return@withContext null
                return@withContext "Login successful but failed to read token."
            }

            val loginErrorStr = loginConn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            val loginErrorMsg = try { JSONObject(loginErrorStr).optString("error_description", "") } catch(e:Exception) {""}

            return@withContext loginErrorMsg.ifEmpty { "Incorrect email or password." }

        } catch (e: Exception) {
            LogX.e(TAG, "Auth exception", e)
            return@withContext "Network error: ${e.message}"
        }
    }

    /**
     * Calls Supabase's `/auth/v1/user` endpoint with the supplied bearer token.
     * Returns the verified user JSON (containing `id`, `email`, etc.) when the
     * token is genuinely issued by *this* Supabase project, or null when the
     * token is invalid / expired / spoofed.
     *
     * Critically, this does NOT mutate any session state. Callers (e.g. the
     * OAuth deep-link handler) must explicitly commit the validated tokens
     * after this returns successfully.
     */
    suspend fun fetchVerifiedUser(bearerToken: String): JSONObject? = withContext(Dispatchers.IO) {
        try {
            val url = URL("$BASE_URL/auth/v1/user")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("apikey", API_KEY)
            conn.setRequestProperty("Authorization", "Bearer $bearerToken")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000

            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                return@withContext JSONObject(body)
            }
            LogX.w(TAG, "fetchVerifiedUser rejected with code ${conn.responseCode}")
            return@withContext null
        } catch (e: Exception) {
            LogX.e(TAG, "Token verification failed", e)
            return@withContext null
        }
    }

    /**
     * Pushes the completed Onboarding profile to the 'user_profiles' table securely using their Auth Token.
     */
    suspend fun saveProfile(
        name: String, age: String, vibe: String, context: String, health: String, interval: Long
    ): Boolean = withContext(Dispatchers.IO) {
        if (userId == null || accessToken == null) {
            LogX.e(TAG, "Cannot save profile without being authenticated!")
            return@withContext false
        }

        val jsonBody = JSONObject().apply {
            put("user_id", userId)
            put("name", name)
            put("age_group", age)
            put("notification_vibe", vibe)
            put("usage_context", context)
            put("neck_health", health)
            put("check_interval_ms", interval)
        }.toString()

        val code = executeAuthed("POST", "/rest/v1/user_profiles", jsonBody) { conn ->
            conn.setRequestProperty("Prefer", "resolution=merge-duplicates")
        } ?: return@withContext false

        val success = code in 200..299
        LogX.d(TAG, "Profile Save result: $code - $success")
        return@withContext success
    }

    /**
     * Uploads unhandled JVM crashes to a Postgres table. Requires authentication.
     * If the user is not authenticated, the upload is dropped silently — Firebase
     * Crashlytics already captures pre-login crashes, so we don't double-report,
     * and we never want to allow anonymous writes against the table.
     *
     * Server-side, the `crash_reports` table MUST have RLS enabled with a policy
     * that restricts inserts to `authenticated` and checks `auth.uid() = user_id`.
     */
    suspend fun uploadCrashLog(stackTrace: String): Boolean = withContext(Dispatchers.IO) {
        val uid = userId
        if (uid.isNullOrEmpty() || accessToken.isNullOrEmpty()) {
            LogX.d(TAG, "Skipping crash upload — not authenticated.")
            return@withContext false
        }

        val jsonBody = JSONObject().apply {
            put("user_id", uid)
            put("stack_trace", stackTrace)
            put("device_info", android.os.Build.MODEL + " (API " + android.os.Build.VERSION.SDK_INT + ")")
        }.toString()

        val code = executeAuthed("POST", "/rest/v1/crash_reports", jsonBody) ?: return@withContext false
        val success = code in 200..299
        LogX.d(TAG, "Crash Upload result: $code - $success")
        return@withContext success
    }

    /**
     * Tri-state outcome from [fetchProfile]. Distinguishing [NotFound] from
     * [Error] is important: the previous boolean-style API conflated the two,
     * which caused the upstream side to route users with transient network
     * errors back through onboarding and silently overwrite their real
     * Supabase profile via UPSERT (see bug B-05 in the audit).
     */
    sealed class ProfileResult {
        data class Found(val profile: JSONObject) : ProfileResult()
        /** Authenticated request succeeded but no row exists for this user yet. */
        object NotFound : ProfileResult()
        /** Network failure, 5xx, parse error, expired/refresh-failed token. */
        object Error : ProfileResult()
    }

    /**
     * Fetches the user's profile from Supabase.
     *
     * Returns:
     *   - [ProfileResult.Found]    when a row exists, with the JSON payload.
     *   - [ProfileResult.NotFound] when the request succeeded with an empty
     *     array — i.e. this user genuinely has no profile yet and should be
     *     routed through onboarding.
     *   - [ProfileResult.Error]    for any other outcome (network down,
     *     non-2xx response after refresh, JSON parse failure, missing
     *     session). Callers must NOT treat this as "no profile" — the
     *     server may still have a valid row that we simply can't read
     *     right now.
     */
    suspend fun fetchProfile(): ProfileResult = withContext(Dispatchers.IO) {
        val uid = userId ?: return@withContext ProfileResult.Error
        if (accessToken == null) return@withContext ProfileResult.Error

        var attempts = 0
        while (attempts < 2) {
            val token = accessToken ?: return@withContext ProfileResult.Error
            try {
                val url = URL("$BASE_URL/rest/v1/user_profiles?user_id=eq.$uid&select=*")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("apikey", API_KEY)
                conn.setRequestProperty("Authorization", "Bearer $token")
                conn.setRequestProperty("Content-Type", "application/json")
                conn.connectTimeout = 15_000
                conn.readTimeout = 15_000

                val code = conn.responseCode
                if (code == 401 && attempts == 0) {
                    attempts++
                    if (refreshAccessToken()) continue
                    return@withContext ProfileResult.Error
                }
                if (code in 200..299) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val arr = org.json.JSONArray(body)
                    return@withContext if (arr.length() > 0) {
                        ProfileResult.Found(arr.getJSONObject(0))
                    } else {
                        ProfileResult.NotFound
                    }
                }
                // Non-2xx, non-401 → server problem; treat as transient.
                return@withContext ProfileResult.Error
            } catch (e: Exception) {
                LogX.e(TAG, "Failed to fetch profile", e)
                return@withContext ProfileResult.Error
            }
        }
        ProfileResult.Error
    }

    // ─────────── Refresh token plumbing ───────────

    /**
     * Exchanges the persisted refresh_token for a fresh access_token via Supabase's
     * `/auth/v1/token?grant_type=refresh_token` endpoint. Updates [accessToken],
     * [refreshToken], and [userId] in-place on success and persists them via the
     * hook installed in [installPersistenceHook].
     *
     * Returns true if the refresh succeeded, false otherwise (caller should treat
     * the session as terminated and force re-login).
     *
     * Guarded by [refreshMutex] so concurrent 401s don't stampede the endpoint.
     */
    suspend fun refreshAccessToken(): Boolean = refreshMutex.withLock {
        withContext(Dispatchers.IO) {
            val rt = refreshToken
            if (rt.isNullOrEmpty()) {
                LogX.w(TAG, "No refresh token available — cannot refresh session.")
                return@withContext false
            }
            try {
                val url = URL("$BASE_URL/auth/v1/token?grant_type=refresh_token")
                val conn = openConnection(url, "POST")
                writeBody(conn, JSONObject().apply { put("refresh_token", rt) }.toString())

                if (conn.responseCode in 200..299) {
                    val parsed = parseAuthResponse(conn)
                    if (parsed) {
                        LogX.d(TAG, "Access token refreshed successfully.")
                        return@withContext true
                    }
                }

                LogX.w(TAG, "Refresh failed with code ${conn.responseCode} — clearing session.")
                clearSession()
                return@withContext false
            } catch (e: Exception) {
                LogX.e(TAG, "Refresh exception", e)
                return@withContext false
            }
        }
    }

    fun clearSession() {
        accessToken = null
        refreshToken = null
        userId = null
        sessionPersister?.invoke(null, null, null)
    }

    /**
     * UserRepository registers a hook here so the encrypted-prefs copy is
     * always in lockstep with the in-memory tokens, including after a
     * background refresh. Triple of (access, refresh, userId); null = clear.
     */
    @Volatile private var sessionPersister: ((String?, String?, String?) -> Unit)? = null
    fun installPersistenceHook(hook: (access: String?, refresh: String?, uid: String?) -> Unit) {
        sessionPersister = hook
    }

    // ─────────── Internal HTTP helpers ───────────

    private suspend fun executeAuthed(
        method: String,
        path: String,
        body: String?,
        configure: (HttpURLConnection) -> Unit = {}
    ): Int? {
        var attempts = 0
        while (attempts < 2) {
            val token = accessToken ?: return null
            try {
                val url = URL("$BASE_URL$path")
                val conn = openConnection(url, method, token)
                configure(conn)
                if (body != null) writeBody(conn, body)

                val code = conn.responseCode
                if (code == 401 && attempts == 0) {
                    attempts++
                    if (refreshAccessToken()) continue
                    return code
                }
                return code
            } catch (e: Exception) {
                LogX.e(TAG, "$method $path failed", e)
                return null
            }
        }
        return null
    }

    private fun openConnection(url: URL, method: String, bearer: String? = null): HttpURLConnection {
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.setRequestProperty("apikey", API_KEY)
        conn.setRequestProperty("Content-Type", "application/json")
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        if (bearer != null) conn.setRequestProperty("Authorization", "Bearer $bearer")
        if (method == "POST" || method == "PATCH" || method == "PUT") conn.doOutput = true
        return conn
    }

    private fun writeBody(conn: HttpURLConnection, body: String) {
        OutputStreamWriter(conn.outputStream).use { it.write(body) }
    }

    private fun parseAuthResponse(conn: HttpURLConnection): Boolean {
        val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
        val responseJson = JSONObject(responseStr)

        val newAccess = responseJson.optString("access_token").takeIf { it.isNotEmpty() }
        val newRefresh = responseJson.optString("refresh_token").takeIf { it.isNotEmpty() }

        val userObj = responseJson.optJSONObject("user")
        val newUid = userObj?.optString("id")?.takeIf { it.isNotEmpty() }
            ?: responseJson.optString("user_id").takeIf { it.isNotEmpty() }

        if (newAccess == null || newUid == null) return false

        accessToken = newAccess
        // Supabase rotates refresh_token on every refresh. If the response did
        // not include one (rare; some grants reuse it), keep the old one.
        if (newRefresh != null) refreshToken = newRefresh
        userId = newUid

        sessionPersister?.invoke(accessToken, refreshToken, userId)
        return true
    }
}
