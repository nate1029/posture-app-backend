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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
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

    var hasCameraPerm by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var hasNotifPerm by remember {
        mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true)
    }
    
    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var isIgnoringBatteryOptimizations by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName)) }
    var isAppActive by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf("Home") }
    var selectedInterval by remember { mutableStateOf(prefs.getLong("IntervalPreferenceMs", 30 * 60 * 1000L)) }

    val cameraLauncher = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCameraPerm = it }
    val notifLauncher = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasNotifPerm = it }
    
    val userName = prefs.getString("UserName", "there") ?: "there"

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 8.dp
            ) {
                val tabs = listOf("Home", "Progress", "Exercises", "Rewards", "Settings")
                val icons = listOf(Icons.Default.Home, Icons.Default.DateRange, Icons.Default.FavoriteBorder, Icons.Default.Star, Icons.Default.Settings)
                
                tabs.forEachIndexed { index, title ->
                    NavigationBarItem(
                        selected = (currentTab == title),
                        onClick = { currentTab = title },
                        icon = { Icon(icons[index], contentDescription = title) },
                        label = { Text(title, style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.surfaceVariant,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            
            if (currentTab == "Home") {
                // 1. Dashboard Hook
                Text("Feeling good, $userName.", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
                Text("Your neck is fully supported today.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(48.dp))

                // 2. Primary Engine Action
                Button(
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isAppActive) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary,
                        contentColor = if (isAppActive) MaterialTheme.colorScheme.onSurfaceVariant else Color.Black
                    ),
                    enabled = hasCameraPerm && hasNotifPerm,
                    onClick = {
                        if (!isAppActive) {
                            val serviceIntent = Intent(context, NeckGuardService::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent) else context.startService(serviceIntent)
                            isAppActive = true
                        } else {
                            context.stopService(Intent(context, NeckGuardService::class.java))
                            isAppActive = false
                        }
                    }
                ) {
                    Text(if (isAppActive) "Pause Shield" else "Activate Shield", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(modifier = Modifier.height(24.dp))

                // 3. The Gamification / Streak Card
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("\uD83D\uDD25 3 Day Streak", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("You've stayed mindful of your posture for 3 days in a row.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (currentTab == "Settings") {
                Text("Settings", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(24.dp))
                
                // Interval Radio Buttons
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Check Interval", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text("Sets how long you must be reading before posture is evaluated.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val options = listOf(15_000L to "15 Seconds (Testing)", 15 * 60 * 1000L to "15 Minutes", 30 * 60 * 1000L to "30 Minutes")
                        options.forEach { (ms, label) ->
                            Row {
                                RadioButton(selected = (selectedInterval == ms), onClick = { 
                                    selectedInterval = ms
                                    prefs.edit().putLong("IntervalPreferenceMs", ms).apply()
                                })
                                Text(label, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 8.dp, top = 12.dp))
                            }
                        }
                    }
                }

                // System Status
                Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("System Requirements", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(8.dp))
                        if (!hasNotifPerm) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Notifications", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp)); Button(onClick = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) { Text("Fix") } } }
                        if (!hasCameraPerm) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Camera Sensor", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp)); Button(onClick = { cameraLauncher.launch(Manifest.permission.CAMERA) }) { Text("Fix") } } }
                        if (!isIgnoringBatteryOptimizations) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Background Power", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp)); Button(onClick = { context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${context.packageName}") }) }) { Text("Fix") } } }
                        if (hasCameraPerm && hasNotifPerm && isIgnoringBatteryOptimizations) { Text("All systems nominal.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            } else {
                Text(currentTab, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
                Spacer(modifier = Modifier.height(16.dp))
                Text("This premium feature is currently locked in the MVP.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}