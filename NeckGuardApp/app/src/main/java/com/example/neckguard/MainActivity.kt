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
    private lateinit var repository: com.example.neckguard.data.UserRepository
    private lateinit var viewModel: com.example.neckguard.ui.MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Crash Telemetry safely since we bypassed the Application class
        com.example.neckguard.CrashReporter.initialize(this)
        
        repository = com.example.neckguard.data.UserRepository(SecurePrefs.get(this))
        viewModel = androidx.lifecycle.ViewModelProvider(this, com.example.neckguard.ui.MainViewModelFactory(repository))[com.example.neckguard.ui.MainViewModel::class.java]
        
        handleAuthIntent(intent) // Handle case where app was dead
        
        setContent {
            val appState by viewModel.appState.collectAsState()

            NeckGuardTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    when(appState) {
                        is com.example.neckguard.ui.AppState.Loading -> { 
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
                        }
                        is com.example.neckguard.ui.AppState.Unauthenticated -> {
                            com.example.neckguard.ui.AuthScreen {
                                repository.saveTokens(
                                    com.example.neckguard.SupabaseClient.accessToken ?: "", 
                                    com.example.neckguard.SupabaseClient.userId ?: ""
                                )
                                viewModel.checkStatus()
                            }
                        }
                        is com.example.neckguard.ui.AppState.NeedsOnboarding -> {
                            com.example.neckguard.ui.OnboardingScreen(prefs = SecurePrefs.get(this)) {
                                viewModel.finishOnboarding()
                            }
                        }
                        is com.example.neckguard.ui.AppState.Ready -> {
                            AppScreen(viewModel)
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "neckguard" && uri.host == "callback") {
            val fragment = uri.fragment ?: ""
            val params = fragment.split("&").associate { 
                val split = it.split("=")
                split[0] to (if (split.size > 1) split[1] else "")
            }
            if (params.containsKey("access_token")) {
                val token = params["access_token"]!!
                var userId = ""
                try {
                    val payloadStr = token.split(".")[1]
                    val decodedBytes = android.util.Base64.decode(payloadStr, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
                    val payload = String(decodedBytes, Charsets.UTF_8)
                    userId = org.json.JSONObject(payload).optString("sub")
                } catch (e: Exception) {}
                
                // Securely save without reloading the entire application loop
                repository.saveTokens(token, userId)
                
                intent.data = null // Clear intent to not recycle
                viewModel.checkStatus() // Triggers UI StateFlow redraw instantly
            }
        }
    }
}


@SuppressLint("BatteryLife")
@Composable
fun AppScreen(viewModel: com.example.neckguard.ui.MainViewModel) {
    val context = LocalContext.current
    val prefs = SecurePrefs.get(context)

    var hasCameraPerm by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var hasNotifPerm by remember {
        mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        } else true)
    }
    var hasActivityPerm by remember {
        mutableStateOf(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
        } else true)
    }

    val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    var isIgnoringBatteryOptimizations by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName)) }
    var isAppActive by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf("Home") }
    var selectedInterval by remember { mutableStateOf(prefs.getLong("IntervalPreferenceMs", 30 * 60 * 1000L)) }

    val multiplePermissionsLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPerm = permissions[Manifest.permission.CAMERA] ?: hasCameraPerm
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            hasNotifPerm = permissions[Manifest.permission.POST_NOTIFICATIONS] ?: hasNotifPerm
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            hasActivityPerm = permissions[Manifest.permission.ACTIVITY_RECOGNITION] ?: hasActivityPerm
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }
        multiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
    }
    
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
                    enabled = hasCameraPerm && hasNotifPerm && hasActivityPerm,
                    onClick = {
                        if (!isAppActive) {
                            val serviceIntent = Intent(context, NeckGuardService::class.java)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent) else context.startService(serviceIntent)
                            isAppActive = true
                        } else {
                            // Send STOP_SERVICE action so the service shuts down gracefully
                            // without triggering the AlarmManager auto-revive
                            val stopIntent = Intent(context, NeckGuardService::class.java).apply {
                                action = "STOP_SERVICE"
                            }
                            context.startService(stopIntent)
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
                        if (!hasNotifPerm) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Notifications", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp)); Button(onClick = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) multiplePermissionsLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) }) { Text("Fix") } } }
                        if (!hasCameraPerm) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Camera Sensor", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp)); Button(onClick = { multiplePermissionsLauncher.launch(arrayOf(Manifest.permission.CAMERA)) }) { Text("Fix") } } }
                        if (!hasActivityPerm) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Activity Sensor", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp)); Button(onClick = { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) multiplePermissionsLauncher.launch(arrayOf(Manifest.permission.ACTIVITY_RECOGNITION)) }) { Text("Fix") } } }
                        if (!isIgnoringBatteryOptimizations) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("Background Power", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp)); Button(onClick = { context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = Uri.parse("package:${context.packageName}") }) }) { Text("Fix") } } }
                        if (hasCameraPerm && hasNotifPerm && hasActivityPerm && isIgnoringBatteryOptimizations) { Text("All systems nominal.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Account / Logout Card
                Card(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Account", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text("Sign out of the application and reset local preferences.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f))
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Button(
                            onClick = {
                                val serviceIntent = Intent(context, NeckGuardService::class.java)
                                context.stopService(serviceIntent)
                                isAppActive = false
                                viewModel.logout()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Log Out", color = MaterialTheme.colorScheme.onError)
                        }
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