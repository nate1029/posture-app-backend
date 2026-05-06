package com.example.neckguard.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.neckguard.FirebaseAuthManager
import kotlinx.coroutines.launch

/**
 * Two-step auth screen:
 *   Step 1 — Enter email → send Magic Link  (+ Google Sign-In)
 *   Step 2 — "Check your inbox" confirmation with a Resend button
 *
 * The actual sign-in is completed when the user clicks the email link,
 * which opens the app via deep link and is handled by
 * [com.example.neckguard.MainActivity.handleMagicLinkIntent].
 */
@Composable
fun AuthScreen(onLoginSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }
    var magicLinkSent by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Google Sign-In launcher — fires the native credential picker.
    val googleSignInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        isLoading = true
        loginError = null
        coroutineScope.launch {
            val error = FirebaseAuthManager.handleGoogleSignInResult(result.data)
            isLoading = false
            when {
                error == null -> onLoginSuccess()
                error == "cancelled" -> { /* user dismissed the picker — do nothing */ }
                else -> loginError = error
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Welcome to NudgeUp", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (!magicLinkSent) {
            // ── Step 1: Enter email ─────────────────────────────────────
            Text(
                "Sign in or create an account — no password needed.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(modifier = Modifier.height(32.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (loginError != null) {
                Text(
                    loginError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (isLoading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = {
                        val trimmedEmail = email.trim()
                        if (trimmedEmail.isBlank()) {
                            loginError = "Please enter your email."
                            return@Button
                        }
                        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(trimmedEmail).matches()) {
                            loginError = "That doesn't look like a valid email."
                            return@Button
                        }

                        isLoading = true
                        loginError = null
                        coroutineScope.launch {
                            val error = FirebaseAuthManager.sendMagicLink(context, trimmedEmail)
                            isLoading = false
                            if (error == null) {
                                magicLinkSent = true
                            } else {
                                loginError = error
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Send sign-in link", style = MaterialTheme.typography.titleMedium)
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text("OR", style = MaterialTheme.typography.labelSmall)
                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        val signInIntent = FirebaseAuthManager.getGoogleSignInIntent(context)
                        googleSignInLauncher.launch(signInIntent)
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondary
                    )
                ) {
                    Text(
                        "Sign in with Google",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondary
                    )
                }
            }
        } else {
            // ── Step 2: "Check your inbox" ──────────────────────────────
            Spacer(modifier = Modifier.height(16.dp))
            Text("📩", style = MaterialTheme.typography.displayLarge)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Check your inbox",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "We sent a sign-in link to $email.\nTap the link in the email to continue.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            if (loginError != null) {
                Text(
                    loginError!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (isLoading) {
                CircularProgressIndicator()
            } else {
                OutlinedButton(
                    onClick = {
                        isLoading = true
                        loginError = null
                        coroutineScope.launch {
                            val error = FirebaseAuthManager.sendMagicLink(context, email.trim())
                            isLoading = false
                            if (error != null) loginError = error
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Resend link", style = MaterialTheme.typography.titleMedium)
                }

                Spacer(modifier = Modifier.height(12.dp))

                TextButton(onClick = { magicLinkSent = false; loginError = null }) {
                    Text("Use a different email")
                }
            }
        }
    }
}
