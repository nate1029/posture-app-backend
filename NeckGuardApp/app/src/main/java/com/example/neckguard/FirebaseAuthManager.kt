package com.example.neckguard

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

/**
 * Wraps Firebase Auth (email/password + Google Sign-In + Magic Link)
 * and silently bridges each login to a corresponding Supabase account
 * so our REST layer + RLS policies continue working unchanged.
 *
 * The bridge: after every Firebase login/signup, we call
 * [SupabaseClient.authenticate] with the user's email and a deterministic
 * password derived from the Firebase uid. This creates (or logs into) a
 * mirror Supabase Auth account that owns the `user_profiles` and
 * `crash_reports` rows via RLS.
 */
object FirebaseAuthManager {
    private val auth: FirebaseAuth get() = FirebaseAuth.getInstance()

    /** Key used to persist the email between sending the link and clicking it. */
    private const val PREF_MAGIC_LINK_EMAIL = "MagicLinkPendingEmail"

    /**
     * Email/password sign-in. Tries login first (most common), falls back to
     * signup if the user doesn't exist yet.
     * Returns null on success, error string on failure.
     */
    suspend fun signInWithEmail(email: String, password: String): String? {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            bridgeToSupabase()
            null
        } catch (e: Exception) {
            if (isUserNotFound(e)) {
                trySignUp(email, password)
            } else {
                mapError(e)
            }
        }
    }

    private suspend fun trySignUp(email: String, password: String): String? {
        return try {
            auth.createUserWithEmailAndPassword(email, password).await()
            bridgeToSupabase()
            null
        } catch (e: Exception) {
            mapError(e)
        }
    }


    // ─── Google Sign-In ──────────────────────────────────────────────────

    /**
     * Single source of truth for the Google Sign-In options.
     * Sign-in and sign-out MUST use the same GSO, otherwise they
     * operate on different GoogleSignInClient instances and
     * sign-out silently does nothing to the sign-in client's cache.
     */
    private fun buildGso(context: Context): GoogleSignInOptions {
        return GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(context.getString(R.string.default_web_client_id))
            .requestEmail()
            .build()
    }

    /** Builds the Google Sign-In intent for [ActivityResultLauncher]. */
    fun getGoogleSignInIntent(context: Context): Intent {
        return GoogleSignIn.getClient(context, buildGso(context)).signInIntent
    }

    /**
     * Processes the result from the Google Sign-In activity.
     * Returns null on success, error string on failure.
     */
    suspend fun handleGoogleSignInResult(data: Intent?): String? {
        if (data == null) return "Google Sign-In failed: no data received."
        return try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
                ?: return "Google Sign-In failed: no ID token received."

            val credential = GoogleAuthProvider.getCredential(idToken, null)
            auth.signInWithCredential(credential).await()
            bridgeToSupabase()
            null
        } catch (e: ApiException) {
            if (e.statusCode == 12501) {
                "cancelled"
            } else {
                "Google Sign-In failed (code ${e.statusCode}). Please try again."
            }
        } catch (e: FirebaseAuthInvalidUserException) {
            auth.signOut()
            "Your account was removed. Please sign in again to create a new one."
        } catch (e: Exception) {
            "Google Sign-In failed: ${e.message}"
        }
    }

    fun signOut(context: Context) {
        try {
            GoogleSignIn.getClient(context, buildGso(context)).signOut()
        } catch (_: Throwable) {}
        auth.signOut()
        SupabaseClient.clearSession()
    }

    fun currentUser() = auth.currentUser

    /**
     * Deletes the currently signed-in Firebase user.
     * Must be called before sign out.
     */
    suspend fun deleteCurrentUser(): Boolean {
        return try {
            auth.currentUser?.delete()?.await()
            true
        } catch (e: Exception) {
            android.util.Log.e("FirebaseAuthManager", "Failed to delete Firebase user", e)
            false
        }
    }

    // ─── Supabase bridge ────────────────────────────────────────────────

    /**
     * Silently creates/logs into a Supabase Auth account that mirrors the
     * current Firebase user. Deterministic password ensures the same
     * Firebase user always maps to the same Supabase account.
     */
    suspend fun bridgeToSupabase(): String? {
        val user = auth.currentUser ?: return "No Firebase user"
        
        // Supabase requires a password for email sign-ins.
        // We deterministically derive one from the Firebase UID so
        // the user never has to know it exists.
        val email = user.email ?: "${user.uid}@nudgeup.local"
        val password = deriveSupabasePassword(user.uid)
        
        android.util.Log.d("FirebaseAuthManager", "Bridging to Supabase with email=$email")
        
        val error = com.example.neckguard.SupabaseClient.authenticate(email, password)
        if (error != null) {
            android.util.Log.e("FirebaseAuthManager", "Supabase bridge FAILED: $error")
        } else {
            android.util.Log.d("FirebaseAuthManager", "Supabase bridge OK, userId=${com.example.neckguard.SupabaseClient.userId}")
        }
        return error
    }

    /**
     * SHA-256 hash of the Firebase uid with a salt. Deterministic so the
     * same Firebase user always gets the same Supabase password. Not
     * reversible to the uid.
     */
    private fun deriveSupabasePassword(uid: String): String {
        val input = "nudgeup_bridge_$uid"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }

    // ─── Error mapping ──────────────────────────────────────────────────

    private fun isUserNotFound(e: Exception): Boolean {
        return e is FirebaseAuthInvalidUserException
    }

    private fun mapError(e: Exception): String {
        return when (e) {
            is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password."
            is FirebaseAuthInvalidUserException -> "No account found with this email."
            is FirebaseAuthUserCollisionException -> "An account with this email already exists. Try signing in."
            is FirebaseAuthWeakPasswordException -> "Password is too weak. Use at least 8 characters."
            else -> "Authentication failed: ${e.localizedMessage ?: "unknown error"}"
        }
    }
}
