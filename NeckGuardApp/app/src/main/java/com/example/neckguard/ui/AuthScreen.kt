package com.example.neckguard.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.neckguard.FirebaseAuthManager
import kotlinx.coroutines.launch

private const val MIN_PASSWORD_LEN = 8

private fun validateCredentials(email: String, password: String): String? {
    if (email.isBlank()) return "Please enter your email."
    if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
        return "That doesn't look like a valid email address."
    }
    if (password.length < MIN_PASSWORD_LEN) {
        return "Use a password of at least $MIN_PASSWORD_LEN characters."
    }
    return null
}

@Composable
fun AuthScreen(onLoginSuccess: () -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }

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
        Text("Log in or create a brand new account.", style = MaterialTheme.typography.bodyMedium)

        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            supportingText = { Text("$MIN_PASSWORD_LEN+ characters") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation()
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (loginError != null) {
            Text(loginError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (isLoading) {
            CircularProgressIndicator()
        } else {
            Button(
                onClick = {
                    val trimmedEmail = email.trim()
                    val validationError = validateCredentials(trimmedEmail, password)
                    if (validationError != null) {
                        loginError = validationError
                        return@Button
                    }

                    isLoading = true
                    loginError = null

                    coroutineScope.launch {
                        val error = FirebaseAuthManager.signInWithEmail(trimmedEmail, password)
                        isLoading = false
                        if (error == null) onLoginSuccess() else loginError = error
                    }
                },
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Continue", style = MaterialTheme.typography.titleMedium)
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
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
            ) {
                Text("Sign in with Google", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondary)
            }
        }
    }
}
