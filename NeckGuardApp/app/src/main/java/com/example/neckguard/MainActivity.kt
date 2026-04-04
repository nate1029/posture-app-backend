package com.example.neckguard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.neckguard.service.NeckGuardService
import com.example.neckguard.ui.theme.NeckGuardTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs = getSharedPreferences("NeckGuardPrefs", Context.MODE_PRIVATE)
            var isOnboardingComplete by remember { mutableStateOf(prefs.getBoolean("OnboardingComplete", false)) }
            
            var isUserAuthenticated by remember { 
                val savedToken = prefs.getString("SupabaseToken", null)
                val savedId = prefs.getString("SupabaseUserId", null)
                if (savedToken != null) {
                    com.example.neckguard.SupabaseClient.accessToken = savedToken
                    com.example.neckguard.SupabaseClient.userId = savedId
                    mutableStateOf(true)
                } else mutableStateOf(false)
            }

            NeckGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!isUserAuthenticated) {
                        com.example.neckguard.ui.AuthScreen {
                            // On successful login, save credentials locally
                            prefs.edit()
                                .putString("SupabaseToken", com.example.neckguard.SupabaseClient.accessToken)
                                .putString("SupabaseUserId", com.example.neckguard.SupabaseClient.userId)
                                .apply()
                            isUserAuthenticated = true
                        }
                    } else if (!isOnboardingComplete) {
                        com.example.neckguard.ui.OnboardingScreen(prefs = prefs) {
                            isOnboardingComplete = true
                        }
                    } else {
                        AppScreen()
                    }
                }
            }
        }
    }
}

@SuppressLint("BatteryLife")
@Composable
fun AppScreen() {
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("NeckGuardPrefs", Context.MODE_PRIVATE)

    var hasCameraPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var hasNotifPerm by remember {
        mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true)
    }
    
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var isIgnoringBatteryOptimizations by remember {
        mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName))
    }

    var selectedInterval by remember { 
        mutableStateOf(prefs.getLong("IntervalPreferenceMs", 30 * 60 * 1000L)) 
    }

    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasCameraPerm = it }

    val notifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasNotifPerm = it }
    
    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text("NeckGuard Setup", style = MaterialTheme.typography.headlineMedium)
        Text("Background monitor is ready.", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(24.dp))

        // Permissions Card
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Required Permissions", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Notifications", modifier = Modifier.padding(top = 12.dp))
                    if (hasNotifPerm) Text("✅", modifier = Modifier.padding(top = 12.dp)) else Button(onClick = { 
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }) { Text("Grant") }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Camera (Invisible Check)", modifier = Modifier.padding(top = 12.dp))
                    if (hasCameraPerm) Text("✅", modifier = Modifier.padding(top = 12.dp)) else Button(onClick = { 
                        cameraLauncher.launch(Manifest.permission.CAMERA)
                    }) { Text("Grant") }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Unrestricted Battery", modifier = Modifier.padding(top = 12.dp))
                    if (isIgnoringBatteryOptimizations) Text("✅", modifier = Modifier.padding(top = 12.dp)) else Button(onClick = { 
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }) { Text("Grant") }
                }
            }
        }

        // Settings Card
        Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Check Interval", style = MaterialTheme.typography.titleMedium)
                Text("Sets how long you must be reading before posture is evaluated.", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(8.dp))
                
                val options = listOf(
                    15_000L to "15 Seconds (Testing Mode)", 
                    15 * 60 * 1000L to "15 Minutes", 
                    30 * 60 * 1000L to "30 Minutes", 
                    60 * 60 * 1000L to "1 Hour"
                )
                
                options.forEach { (ms, label) ->
                    Row {
                        RadioButton(
                            selected = (selectedInterval == ms),
                            onClick = { 
                                selectedInterval = ms
                                prefs.edit().putLong("IntervalPreferenceMs", ms).apply()
                            }
                        )
                        Text(label, modifier = Modifier.padding(start = 8.dp, top = 12.dp))
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        Button(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = hasCameraPerm && hasNotifPerm,
            onClick = {
                val serviceIntent = Intent(context, NeckGuardService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        ) {
            Text("Start Background Protection")
        }
    }
}