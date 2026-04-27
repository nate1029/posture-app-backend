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
import androidx.compose.runtime.collectAsState
import com.example.neckguard.ui.MainViewModel
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.clip
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.with

import androidx.core.content.ContextCompat
import com.example.neckguard.service.NeckGuardService
import com.example.neckguard.ui.theme.NeckGuardTheme

class MainActivity : ComponentActivity() {
    private lateinit var repository: com.example.neckguard.data.UserRepository
    private lateinit var viewModel: com.example.neckguard.ui.MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Crash telemetry is initialised in NeckGuardApplication.onCreate;
        // this call is idempotent (guarded by an `initialized` flag) and
        // exists as a safety net in case the Application class is ever
        // bypassed (e.g. instrumentation tests).
        com.example.neckguard.CrashReporter.initialize(this)

        repository = com.example.neckguard.data.UserRepository(SecurePrefs.get(this))
        val dao = com.example.neckguard.data.local.NeckGuardDatabase.getDatabase(this).postureLogDao()
        viewModel = androidx.lifecycle.ViewModelProvider(this, com.example.neckguard.ui.MainViewModelFactory(repository, dao))[com.example.neckguard.ui.MainViewModel::class.java]
        
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
                                // SupabaseClient.parseAuthResponse already invoked the
                                // persistence hook installed by UserRepository, so the
                                // tokens are already on disk. Just nudge the ViewModel.
                                viewModel.checkStatus()
                            }
                        }
                        is com.example.neckguard.ui.AppState.NeedsOnboarding -> {
                            com.example.neckguard.ui.OnboardingScreen(
                                prefs = SecurePrefs.get(this),
                                repository = repository
                            ) {
                                viewModel.finishOnboarding()
                            }
                        }
                        is com.example.neckguard.ui.AppState.Ready -> {
                            AppScreen(viewModel)
                        }
                        is com.example.neckguard.ui.AppState.ProfileFetchFailed -> {
                            ProfileFetchFailedScreen(
                                onRetry = { viewModel.checkStatus() },
                                onLogout = { viewModel.logout(this@MainActivity) }
                            )
                        }
                    }
                }
            }
        }
    }

}


/**
 * Shown when the user is authenticated but `fetchProfile` failed transiently
 * (network down, 5xx). Lets them retry or log out — anything except silently
 * routing them through onboarding, which would UPSERT-overwrite their real
 * Supabase profile.
 */
@Composable
fun ProfileFetchFailedScreen(onRetry: () -> Unit, onLogout: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FinalCream)
            .padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("📡", fontSize = 56.sp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "We couldn't reach the server",
            style = MaterialTheme.typography.headlineSmall,
            color = FinalBark
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Check your connection and try again. We won't change anything until we hear back.",
            style = MaterialTheme.typography.bodyMedium,
            color = FinalMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FinalMoss)
        ) {
            Text("Retry", color = FinalWhite, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onLogout) {
            Text("Log out instead", color = FinalMuted)
        }
    }
}

@SuppressLint("BatteryLife")
@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun AppScreen(viewModel: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = com.example.neckguard.SecurePrefs.get(context)

    var hasCameraPerm by remember { mutableStateOf(androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    var hasNotifPerm by remember {
        mutableStateOf(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true)
    }
    var hasActivityPerm by remember {
        mutableStateOf(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACTIVITY_RECOGNITION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true)
    }

    val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
    var isIgnoringBatteryOptimizations by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName)) }

    val dashState by viewModel.dashboardState.collectAsState()
    var currentTab by remember { mutableStateOf("Home") }
    var previousTab by remember { mutableStateOf("Home") }
    var selectedInterval by remember { mutableStateOf(prefs.getLong("IntervalPreferenceMs", 30 * 60 * 1000L)) }
    var telemetryEnabled by remember {
        mutableStateOf(com.example.neckguard.TelemetryConsent.isEnabled(prefs))
    }

    val multiplePermissionsLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPerm = permissions[android.Manifest.permission.CAMERA] ?: hasCameraPerm
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            hasNotifPerm = permissions[android.Manifest.permission.POST_NOTIFICATIONS] ?: hasNotifPerm
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            hasActivityPerm = permissions[android.Manifest.permission.ACTIVITY_RECOGNITION] ?: hasActivityPerm
        }
    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf(android.Manifest.permission.CAMERA)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            permissionsToRequest.add(android.Manifest.permission.ACTIVITY_RECOGNITION)
        }
        multiplePermissionsLauncher.launch(permissionsToRequest.toTypedArray())
    }
    
    val userName = prefs.getString("UserName", "Max") ?: "Max"

    // Intercept hardware back button when not on Home tab
    androidx.activity.compose.BackHandler(enabled = currentTab != "Home") {
        if (currentTab == "Settings" && previousTab != "Settings") {
            currentTab = previousTab
        } else {
            currentTab = "Home"
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(FinalCream)) {
        androidx.compose.animation.AnimatedContent(
            targetState = currentTab,
            transitionSpec = { fadeIn() with fadeOut() },
            modifier = Modifier.fillMaxSize(),
            label = "tab_transition"
        ) { tab ->
            when (tab) {
                "Exercises" -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        com.example.neckguard.ui.ExercisesScreen(viewModel, onSettingsClick = { previousTab = currentTab; currentTab = "Settings" })
                    }
                }
                "Settings" -> {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Spacer(modifier = Modifier.height(48.dp))
                        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { currentTab = previousTab }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = FinalMuted)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Back to Dashboard", style = MaterialTheme.typography.bodyLarge, color = FinalMuted)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                                                        SettingsComponent(
                                selectedInterval = selectedInterval,
                                onIntervalChange = { ms -> selectedInterval = ms; prefs.edit().putLong("IntervalPreferenceMs", ms).apply() },
                                hasCameraPerm = hasCameraPerm,
                                hasNotifPerm = hasNotifPerm,
                                hasActivityPerm = hasActivityPerm,
                                isIgnoringBattery = isIgnoringBatteryOptimizations,
                                onFixPerm = { perm -> multiplePermissionsLauncher.launch(arrayOf(perm)) },
                                onFixBattery = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = android.net.Uri.parse("package:${context.packageName}") }) },
                                onLogout = {
                                    // Mark as manually paused BEFORE stopping. Otherwise the
                                    // service's onDestroy → scheduleRestart relay (or any
                                    // other respawner) could resurrect the ghost service
                                    // before BootReceiver / ServiceRestartReceiver get to
                                    // re-check the auth flag. (B-19)
                                    prefs.edit().putBoolean("isManuallyPaused", true).apply()
                                    val serviceIntent = android.content.Intent(context, com.example.neckguard.service.NeckGuardService::class.java)
                                    context.stopService(serviceIntent)
                                    viewModel.setAppActive(false)
                                    viewModel.logout(context)
                                },
                                telemetryEnabled = telemetryEnabled,
                                onTelemetryChange = { enabled ->
                                    telemetryEnabled = enabled
                                    com.example.neckguard.TelemetryConsent.setEnabled(context, prefs, enabled)
                                },
                                onOpenNotificationSettings = {
                                    val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    try {
                                        context.startActivity(intent)
                                    } catch (_: android.content.ActivityNotFoundException) {
                                        // Fallback for OEMs that don't expose the standard action.
                                        context.startActivity(
                                            android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                                .setData(android.net.Uri.parse("package:${context.packageName}"))
                                        )
                                    }
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
                "Rewards" -> {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        RewardsTab(viewModel, onSettingsClick = { previousTab = currentTab; currentTab = "Settings" })
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
                else -> { // "Home"
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        HomeTab(userName, viewModel, onToggleService = { active ->
                            if (active) {
                                prefs.edit().putBoolean("isManuallyPaused", false).apply()
                                val serviceIntent = android.content.Intent(context, com.example.neckguard.service.NeckGuardService::class.java)
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) context.startForegroundService(serviceIntent) else context.startService(serviceIntent)
                            } else {
                                prefs.edit().putBoolean("isManuallyPaused", true).apply()
                                val stopIntent = android.content.Intent(context, com.example.neckguard.service.NeckGuardService::class.java).apply { action = "STOP_SERVICE" }
                                context.startService(stopIntent)
                            }
                            viewModel.setAppActive(active)
                        }, onSettingsClick = { previousTab = currentTab; currentTab = "Settings" },
                           onNavigateToExercises = { exerciseId ->
                               currentTab = "Exercises"
                               if (exerciseId != null) {
                                   viewModel.focusAndExpandExercise(exerciseId)
                               }
                           }
                        )
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = currentTab != "Settings",
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = androidx.compose.animation.slideInVertically(initialOffsetY = { it }) + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) + androidx.compose.animation.fadeOut()
        ) {
            BottomNavBar(
                currentTab = currentTab,
                onTabChange = { currentTab = it }
            )
        }
    }
}

@Composable
fun BottomNavBar(currentTab: String, onTabChange: (String) -> Unit, modifier: Modifier = Modifier) {
    NavigationBar(
        modifier = modifier.border(1.dp, FinalMist),
        containerColor = FinalWhite
    ) {
        val tabs = listOf("Home", "Progress", "Exercises")
        val icons = listOf(Icons.Default.Home, Icons.Default.DateRange, Icons.Default.FavoriteBorder)
        val mapping = listOf("Home", "Rewards", "Exercises")

        // Using for loop to preserve RowScope properly
        for (index in tabs.indices) {
            val title = tabs[index]
            val mappedTab = mapping[index]
            NavigationBarItem(
                selected = (currentTab == mappedTab),
                onClick = { onTabChange(mappedTab) },
                icon = { Icon(icons[index], contentDescription = title) },
                label = { Text(title, style = MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = FinalMoss,
                    selectedTextColor = FinalMoss,
                    unselectedIconColor = FinalMuted,
                    unselectedTextColor = FinalMuted,
                    indicatorColor = FinalSagePale
                )
            )
        }
    }
}

val FinalMoss = Color(0xFF4A6741)
val FinalSage = Color(0xFF7A9E7E)
val FinalSageLight = Color(0xFFB5CEB8)
val FinalSagePale = Color(0xFFE8F0E9)
val FinalEarth = Color(0xFFC4A882)
val FinalEarthLight = Color(0xFFEDD9BE)
val FinalCream = Color(0xFFFAF8F3)
val FinalBark = Color(0xFF3D2E1E)
val FinalBarkSoft = Color(0xFF5C4A35)
val FinalMist = Color(0xFFEEF2EE)
val FinalCoral = Color(0xFFD4736A)
val FinalWhite = Color(0xFFFFFFFF)
val FinalMuted = Color(0xFF8A8A7A)
val FinalEarthPale = Color(0xFFF7F0E6)

@Composable
fun HomeTab(
    userName: String,
    viewModel: MainViewModel,
    onToggleService: (Boolean) -> Unit,
    onSettingsClick: () -> Unit,
    onNavigateToExercises: (String?) -> Unit
) {
    val dashState by viewModel.dashboardState.collectAsState()
    val exercisesState by viewModel.exercisesState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(FinalCream)
    ) {
        HomeHeaderCmp(userName, dashState, onToggleService, onSettingsClick)
        
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HomeStreakCmp(dashState)
            HomeNudgeCmp(dashState)
            HomeSectionDivider("TODAY'S EXERCISES")
            HomeExercisesCmp(dashState, exercisesState, onNavigateToExercises)
            HomeSectionDivider("RECOMMENDED FOR YOU")
            HomeDetectBannerCmp(dashState)
            HomeRecCardsCmp(onNavigateToExercises)
        }
    }
}

@Composable
fun HomeHeaderCmp(userName: String, dashState: com.example.neckguard.ui.DashboardState, onToggle: (Boolean)->Unit, onSettings: ()->Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(androidx.compose.ui.graphics.Brush.linearGradient(colors = listOf(FinalMoss, FinalSage, FinalSageLight)))
            .padding(top = 52.dp, start = 22.dp, end = 22.dp, bottom = 20.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.background(FinalMoss, shape = RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 6.dp)) {
                     Text("NudgeUp ↑", color = FinalCream, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                Box(modifier = Modifier.size(36.dp).background(FinalWhite, shape = CircleShape).border(2.dp, FinalMist, CircleShape).clip(CircleShape).clickable { onSettings() }, contentAlignment = Alignment.Center) {
                    Text("⚙", fontSize = 20.sp)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            // Recomputed once per composition so the header label always
            // reflects the current calendar day. Cheap (~µs) — formatting
            // a Date is not worth caching.
            val headerDate = remember {
                java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault())
                    .format(java.util.Date())
                    .uppercase(java.util.Locale.getDefault())
            }
            Text(headerDate, fontSize = 12.sp, color = FinalWhite.copy(alpha=0.65f), letterSpacing = 0.06.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row {
               Text("Hey, ", fontSize = 26.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite)
               Text(userName, fontSize = 26.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = FinalWhite)
               Text(" 👋", fontSize = 26.sp)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text("Your posture scan is complete for today.", fontSize = 12.sp, color = FinalWhite.copy(alpha=0.6f))
            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth().background(FinalWhite.copy(alpha = 0.15f), shape = RoundedCornerShape(16.dp)).padding(14.dp, 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${dashState.postureScore}", fontSize = 48.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("POSTURE SCORE", fontSize = 10.sp, letterSpacing = 0.07.sp, color = FinalWhite.copy(alpha=0.6f))
                    val title = if(dashState.postureScore > 80) "Superb 🌟" else if(dashState.postureScore > 60) "Good 👍" else "Needs Work ⚠️"
                    Text(title, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalWhite)
                    Text(if(dashState.scoreDelta >= 0) "↑ +${dashState.scoreDelta} vs yesterday" else "↓ ${dashState.scoreDelta} vs yesterday", fontSize = 11.sp, color = FinalWhite.copy(alpha=0.6f))
                }
                Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawArc(color = FinalWhite.copy(alpha=0.25f), startAngle = 0f, sweepAngle = 360f, useCenter = false, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx()))
                        drawArc(color = FinalWhite, startAngle = -90f, sweepAngle = 360f * (dashState.postureScore/100f), useCenter = false, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.background(FinalWhite.copy(alpha=0.2f), shape = RoundedCornerShape(20.dp)).clickable { onToggle(!dashState.isAppActive) }.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                if (dashState.isAppActive) {
                    Box(modifier = Modifier.size(7.dp).background(FinalWhite, CircleShape))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("MONITORING ACTIVE", fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite, letterSpacing = 0.06.sp)
                } else {
                    Box(modifier = Modifier.size(7.dp).background(FinalWhite.copy(alpha=0.5f), CircleShape))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("PAUSED (TAP TO RESUME)", fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite.copy(alpha=0.8f), letterSpacing = 0.06.sp)
                }
            }
        }
    }
}

@Composable
fun HomeStreakCmp(dashState: com.example.neckguard.ui.DashboardState) {
    Row(modifier = Modifier.fillMaxWidth().background(FinalBark, shape = RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 11.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) { Text("⚡", fontSize = 16.sp); Spacer(modifier = Modifier.width(7.dp)); Text("${dashState.streakDays}-day streak", fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalEarthLight) }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Next nudge in ", fontSize = 11.sp, color = FinalWhite.copy(alpha=0.4f)); Text("${dashState.nextNudgeMins} min", fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite.copy(alpha=0.75f)) }
    }
}

@Composable
fun HomeNudgeCmp(dashState: com.example.neckguard.ui.DashboardState) {
    Column(modifier = Modifier.fillMaxWidth().background(FinalCoral, shape = RoundedCornerShape(14.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.Top) { Text("📱", fontSize = 22.sp); Spacer(modifier = Modifier.width(10.dp)); Column { Text("TODAY'S NUDGE", fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite.copy(alpha=0.55f), letterSpacing=0.1.sp); Spacer(modifier = Modifier.height(3.dp)); Text(dashState.todayNudge.instruction, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite, lineHeight = 18.sp) } }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { dashState.todayNudge.tags.forEach { tag -> Box(modifier = Modifier.background(FinalWhite.copy(alpha=0.18f), shape = RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) { Text(tag, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalWhite) } } }
        Spacer(modifier = Modifier.height(8.dp))
        Column(modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha=0.1f), shape = RoundedCornerShape(10.dp)).padding(10.dp)) { Text("WHY THIS WORKS", fontSize = 8.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite.copy(alpha=0.5f), letterSpacing = 0.1.sp); Spacer(modifier = Modifier.height(2.dp)); Text(dashState.todayNudge.reasoning, fontSize = 10.sp, color = FinalWhite.copy(alpha=0.8f), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, lineHeight = 14.sp) }
    }
}

@Composable
fun HomeSectionDivider(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) { Box(modifier = Modifier.weight(1f).height(1.dp).background(FinalMist)); Spacer(modifier = Modifier.width(10.dp)); Text(title, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 0.1.sp, color = FinalMuted); Spacer(modifier = Modifier.width(10.dp)); Box(modifier = Modifier.weight(1f).height(1.dp).background(FinalMist)) }
}

@Composable
fun HomeExercisesCmp(dashState: com.example.neckguard.ui.DashboardState, exercisesState: com.example.neckguard.ui.ExercisesState, onNavigateToExercises: (String?) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(FinalWhite, shape = RoundedCornerShape(14.dp)).border(1.dp, FinalMist, RoundedCornerShape(14.dp)).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().clickable(
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            indication = null
        ) { onNavigateToExercises(null) }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column { Text("Your assigned routine", fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalBark); Spacer(modifier = Modifier.height(2.dp)); Text("Based on today's scan • ${dashState.completedExercisesCount}/${dashState.activeExercisesCount} completed", fontSize = 10.sp, color = FinalMuted) }; Text("See all →", fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalSage) }
        Spacer(modifier = Modifier.height(10.dp))
        dashState.assignedExercises.forEach { exerciseName ->
            val isDone = exercisesState.doneIds.contains(exerciseName)
            val cat = com.example.neckguard.ui.ExerciseData.exercises.firstOrNull { it.title == exerciseName }?.category ?: ""
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp).clip(RoundedCornerShape(10.dp)).background(FinalMist).clickable { onNavigateToExercises(exerciseName) }.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(9.dp).background(if (isDone) FinalSage else FinalSageLight, CircleShape)); Spacer(modifier = Modifier.width(10.dp)); Text(exerciseName, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = if (isDone) FinalMuted else FinalBark, textDecoration = if (isDone) androidx.compose.ui.text.style.TextDecoration.LineThrough else null, modifier = Modifier.weight(1f)); Spacer(modifier = Modifier.width(10.dp)); Text(cat, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, color = FinalMuted); Spacer(modifier = Modifier.width(4.dp)); Text(if (isDone) "✓" else "→", fontSize = 11.sp, color = if (isDone) FinalSage else FinalMuted)
            }
        }
    }
}

@Composable
fun HomeDetectBannerCmp(dashState: com.example.neckguard.ui.DashboardState) {
    Row(modifier = Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.linearGradient(colors = listOf(FinalBark, FinalBarkSoft)), shape = RoundedCornerShape(14.dp)).padding(16.dp)) {
        Text("👁️", fontSize = 26.sp); Spacer(modifier = Modifier.width(12.dp)); Column { Text("DETECTED TODAY", fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite.copy(alpha=0.45f), letterSpacing = 0.1.sp); Spacer(modifier = Modifier.height(4.dp)); Text(dashState.detectedToday.title, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite, lineHeight = 19.sp); Spacer(modifier = Modifier.height(3.dp)); Text(dashState.detectedToday.subtitle, fontSize = 11.sp, color = FinalWhite.copy(alpha=0.5f), lineHeight = 15.sp); Spacer(modifier = Modifier.height(8.dp)); Box(modifier = Modifier.border(1.dp, FinalWhite.copy(alpha=0.2f), RoundedCornerShape(20.dp)).background(FinalWhite.copy(alpha=0.12f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) { Text(dashState.detectedToday.severityTag, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalWhite.copy(alpha=0.7f)) } }
    }
}

@Composable
fun HomeRecCardsCmp(onNavigateToExercises: (String?) -> Unit) {
    listOf(Triple("20-20-20 Rule", "Eye Relief", "Every 20 min"), Triple("Blinking Exercise", "Eye Relief", "10 reps/break")).forEachIndexed { i, rec ->
        Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(FinalWhite).border(1.dp, FinalMist, RoundedCornerShape(14.dp)).clickable { onNavigateToExercises(rec.first) }.padding(bottom = 10.dp)) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(38.dp).background(FinalSagePale, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) { Text("${i+1}", fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalMoss) }; Spacer(modifier = Modifier.width(12.dp)); Column(modifier = Modifier.weight(1f)) { Text(rec.first, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalBark); Spacer(modifier = Modifier.height(3.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.background(FinalSagePale, RoundedCornerShape(20.dp)).padding(horizontal=8.dp, vertical=2.dp)) { Text(rec.second, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalMoss) }; Spacer(modifier = Modifier.width(8.dp)); Text(rec.third, fontSize = 10.sp, color = FinalMuted) } }; Text("→", fontSize = 13.sp, color = FinalMuted) }
            Row(modifier = Modifier.fillMaxWidth().background(FinalSagePale, RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Text("💡", fontSize = 12.sp); Spacer(modifier = Modifier.width(6.dp)); Text(if (i==0) "Closest match for sustained near-vision screen use detected today." else "Reduces dry eye caused by reduced blink rate.", fontSize = 10.sp, color = FinalMoss, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, lineHeight = 14.sp) }
        }
    }
}

@Composable
fun RewardsTab(viewModel: MainViewModel, onSettingsClick: () -> Unit) {
    val state by viewModel.rewardsState.collectAsState()
    val dashState by viewModel.dashboardState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(modifier = Modifier.fillMaxSize().background(FinalCream)) {
        Box(modifier = Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.linearGradient(colors = listOf(FinalSage, FinalSageLight))).padding(top = 52.dp, start = 22.dp, end = 22.dp, bottom = 24.dp)) {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.background(FinalMoss, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 6.dp)) { Text("NudgeUp ↑", color = FinalCream, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }; Box(modifier = Modifier.size(36.dp).background(FinalEarthPale, CircleShape).border(2.dp, FinalEarthPale, CircleShape).clip(CircleShape).clickable { onSettingsClick() }, contentAlignment = Alignment.Center) { Text("⚙", fontSize = 15.sp) } }
                Spacer(modifier = Modifier.height(10.dp)); Text("PROGRESS & REWARDS", fontSize = 12.sp, color = FinalWhite.copy(alpha=0.55f), letterSpacing = 0.07.sp); Spacer(modifier = Modifier.height(14.dp))
                val scoreTitle = when {
                    dashState.postureScore > 80 -> "Superb posture 🌟"
                    dashState.postureScore > 60 -> "Good posture 👍"
                    dashState.postureScore > 0  -> "Needs work ⚠️"
                    else -> "No data yet"
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("${dashState.postureScore}", fontSize = 56.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite); Spacer(modifier = Modifier.width(16.dp)); Column { Text("TODAY'S SCORE", fontSize = 10.sp, color = FinalWhite.copy(alpha=0.6f), letterSpacing = 0.07.sp); Text(scoreTitle, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalWhite); Text(if(dashState.scoreDelta >= 0) "↑ +${dashState.scoreDelta} vs yesterday" else "↓ ${dashState.scoreDelta} vs yesterday", fontSize = 11.sp, color = FinalWhite.copy(alpha=0.6f)) } }
            }
        }
        
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val sections = listOf(
                Triple("week", "📅", Pair("This week", "")),
                Triple("lb", "🏆", Pair("Leaderboard", "")),
                Triple("friends", "👥", Pair("Friends' streaks", "")),
                Triple("rewards", "🎁", Pair("Rewards", ""))
            )
            
            sections.forEach { item ->
                val key = item.first; val icon = item.second; val title = item.third.first; val sub = item.third.second
                val isExpanded = state.expandedSection == key
                val iconBg = when(key) { "week" -> FinalSagePale; "lb" -> FinalEarthLight; "friends" -> Color(0xFFDCF0F7); else -> FinalEarthPale }
                
                Column(modifier = Modifier.fillMaxWidth().background(FinalWhite, RoundedCornerShape(16.dp)).border(1.dp, FinalMist, RoundedCornerShape(16.dp))) {
                    Row(modifier = Modifier.fillMaxWidth().clickable { viewModel.setRewardsSection(key) }.padding(14.dp, 16.dp), verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(36.dp).background(iconBg, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Text(icon, fontSize = 17.sp) }; Spacer(modifier = Modifier.width(10.dp)); Column(modifier = Modifier.weight(1f)) { Text(title, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalBark); if (sub.isNotEmpty()) Text(sub, fontSize = 10.sp, color = FinalMuted) }; Text(if(isExpanded) "▲" else "▼", fontSize = 11.sp, color = FinalMuted) }
                    if (isExpanded) {
                        Box(modifier = Modifier.fillMaxWidth().padding(horizontal=16.dp).height(1.dp).background(FinalMist))
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (key == "week") {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    state.weekLog.forEach { item2 -> val day = item2.first; val stat = item2.second; Column(horizontalAlignment = Alignment.CenterHorizontally) { Box(modifier = Modifier.size(32.dp).border(2.5.dp, if(stat=="✓" || stat=="F") FinalSage else if (stat=="!") FinalEarth else Color(0xFFE0E0D8), CircleShape).background(if(stat=="✓") FinalSagePale else if(stat=="F") FinalSage else if(stat=="!") FinalEarthPale else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) { Text(if(stat=="✓") "✓" else if(stat=="!") "!" else if(stat=="F") day else day, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = if(stat=="✓"||stat=="F") FinalMoss else if(stat=="!") FinalEarth else Color.LightGray) }; Spacer(modifier = Modifier.height(3.dp)); Text(day, fontSize=9.sp, color=FinalMuted) } }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Column(modifier = Modifier.weight(1f).background(FinalMist, RoundedCornerShape(12.dp)).padding(12.dp)) { Text(String.format("%.1f", state.timeTrackedHours) + "h", fontSize=22.sp, fontWeight=androidx.compose.ui.text.font.FontWeight.Bold, color=FinalBark); Text("Time tracked", fontSize=10.sp, color=FinalMuted) }; Column(modifier = Modifier.weight(1f).background(FinalMist, RoundedCornerShape(12.dp)).padding(12.dp)) { Text("${state.exercisesDoneTotal}/${state.totalRequiredExercises}", fontSize=22.sp, fontWeight=androidx.compose.ui.text.font.FontWeight.Bold, color=FinalBark); Text("Exercises done", fontSize=10.sp, color=FinalMuted) } }
                            } else if (key == "rewards") {
                                Row(modifier = Modifier.fillMaxWidth().background(FinalBark, RoundedCornerShape(14.dp)).padding(14.dp, 16.dp), verticalAlignment = Alignment.CenterVertically) { Column(modifier = Modifier.weight(1f)) { Text("${state.points} pts", fontSize=30.sp, fontWeight=androidx.compose.ui.text.font.FontWeight.Bold, color=FinalEarthLight); Text("EARNED THIS WEEK", fontSize=10.sp, color=FinalWhite.copy(alpha=0.4f), letterSpacing=0.06.sp) }; androidx.compose.material3.Button(onClick={ android.widget.Toast.makeText(context, "Giftcard store coming soon!", android.widget.Toast.LENGTH_SHORT).show() }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = FinalSage), shape = RoundedCornerShape(10.dp)) { Text("Redeem →", fontSize=12.sp, fontWeight=androidx.compose.ui.text.font.FontWeight.Bold) } }
                                Spacer(modifier = Modifier.height(14.dp)); Text("YOUR TITLES", fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalMuted, letterSpacing = 0.08.sp); Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { Box(modifier = Modifier.background(FinalSage, RoundedCornerShape(20.dp)).padding(horizontal=13.dp, vertical=6.dp)) { Text("Neck Newbie", fontSize=11.sp, fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold, color=FinalWhite) }; Box(modifier = Modifier.background(FinalSage, RoundedCornerShape(20.dp)).padding(horizontal=13.dp, vertical=6.dp)) { Text("Posture Pro", fontSize=11.sp, fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold, color=FinalWhite) }; Box(modifier = Modifier.background(FinalMist, RoundedCornerShape(20.dp)).padding(horizontal=13.dp, vertical=6.dp)) { Text("Spine Savant", fontSize=11.sp, fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold, color=Color(0xFFBBBBBB)) } }
                            } else {
                                Text("Feature coming soon...", fontSize = 12.sp, color = FinalMuted, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsComponent(
    selectedInterval: Long, onIntervalChange: (Long) -> Unit,
    hasCameraPerm: Boolean, hasNotifPerm: Boolean, hasActivityPerm: Boolean, isIgnoringBattery: Boolean,
    onFixPerm: (String) -> Unit, onFixBattery: () -> Unit, onLogout: () -> Unit,
    telemetryEnabled: Boolean, onTelemetryChange: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit
) {
    Column {
        androidx.compose.material3.Text("App Settings", fontSize = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalBark)
        Spacer(modifier = Modifier.height(16.dp))

        // ─── Insecure-storage banner (S-07 visibility) ────────────────────
        // Only renders when the KeyStore was unavailable on this run. Tells
        // the user their data isn't currently encrypted at rest and that we
        // will retry on next launch (the fallback is non-permanent).
        if (com.example.neckguard.SecurePrefs.isUsingFallback) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FinalCoral.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                    .border(1.dp, FinalCoral.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                androidx.compose.material3.Text("⚠️", fontSize = 18.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    androidx.compose.material3.Text(
                        "Secure storage unavailable",
                        fontSize = 13.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = FinalCoral
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    androidx.compose.material3.Text(
                        "Your data is being stored without device encryption right now. We'll retry secure storage on the next app launch. If this keeps happening, try restarting your phone.",
                        fontSize = 11.sp,
                        color = FinalBark,
                        lineHeight = 15.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
        Spacer(modifier = Modifier.height(8.dp))
        
        Column(modifier = Modifier.fillMaxWidth().background(FinalWhite, RoundedCornerShape(14.dp)).border(1.dp, FinalMist, RoundedCornerShape(14.dp)).padding(16.dp)) {
            androidx.compose.material3.Text("Check Interval", fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalBark)
            androidx.compose.material3.Text("Sets how long you must be reading before posture is evaluated.", fontSize = 12.sp, color = FinalMuted)
            Spacer(modifier = Modifier.height(12.dp))
            val options = listOf(15_000L to "15 Seconds (Testing)", 15 * 60 * 1000L to "15 Minutes", 30 * 60 * 1000L to "30 Minutes")
            options.forEach { (ms, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(selected = (selectedInterval == ms), onClick = { onIntervalChange(ms) }, colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = FinalMoss))
                    androidx.compose.material3.Text(label, fontSize = 14.sp, color = FinalBark)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.fillMaxWidth().background(FinalSagePale, RoundedCornerShape(14.dp)).border(1.dp, FinalSageLight, RoundedCornerShape(14.dp)).padding(16.dp)) {
            androidx.compose.material3.Text("System Requirements", fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalMoss)
            Spacer(modifier = Modifier.height(12.dp))
            if (!hasNotifPerm) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.Text("Notifications", color = FinalBark)
                    androidx.compose.material3.Button(
                        // The runtime POST_NOTIFICATIONS dialog only appears
                        // until the user denies twice (Android 13+) — after
                        // that the launcher returns instantly with no UI.
                        // Deep-linking to App Notification Settings is the
                        // only reliable recovery path, and it works fine even
                        // for first-time users (just adds one extra tap).
                        // (B-27)
                        onClick = { onOpenNotificationSettings() },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = FinalMoss)
                    ) { androidx.compose.material3.Text("Fix") }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (!hasCameraPerm) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { androidx.compose.material3.Text("Camera Sensor", color = FinalBark); androidx.compose.material3.Button(onClick = { onFixPerm(android.Manifest.permission.CAMERA) }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = FinalMoss)) { androidx.compose.material3.Text("Fix") } }; Spacer(modifier = Modifier.height(8.dp)) }
            if (!hasActivityPerm) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { androidx.compose.material3.Text("Activity Sensor", color = FinalBark); androidx.compose.material3.Button(onClick = { if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) onFixPerm(android.Manifest.permission.ACTIVITY_RECOGNITION) }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = FinalMoss)) { androidx.compose.material3.Text("Fix") } }; Spacer(modifier = Modifier.height(8.dp)) }
            if (!isIgnoringBattery) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { androidx.compose.material3.Text("Background Power", color = FinalBark); androidx.compose.material3.Button(onClick = { onFixBattery() }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = FinalMoss)) { androidx.compose.material3.Text("Fix") } } }
            if (hasCameraPerm && hasNotifPerm && hasActivityPerm && isIgnoringBattery) { androidx.compose.material3.Text("All systems nominal. Ready to protect your neck!", fontSize = 12.sp, color = FinalMoss) }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.fillMaxWidth().background(FinalWhite, RoundedCornerShape(14.dp)).border(1.dp, FinalMist, RoundedCornerShape(14.dp)).padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                    androidx.compose.material3.Text(
                        "Anonymous diagnostics",
                        fontSize = 16.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = FinalBark
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    androidx.compose.material3.Text(
                        "Lets us see crashes and aggregated usage so we can fix bugs. We never log your name, email, or photos.",
                        fontSize = 12.sp,
                        color = FinalMuted,
                        lineHeight = 16.sp
                    )
                }
                androidx.compose.material3.Switch(
                    checked = telemetryEnabled,
                    onCheckedChange = onTelemetryChange,
                    colors = androidx.compose.material3.SwitchDefaults.colors(
                        checkedTrackColor = FinalMoss,
                        checkedThumbColor = FinalWhite
                    )
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.fillMaxWidth().background(FinalCoral.copy(alpha=0.1f), RoundedCornerShape(14.dp)).border(1.dp, FinalCoral.copy(alpha=0.3f), RoundedCornerShape(14.dp)).padding(16.dp)) {
            androidx.compose.material3.Text("Account", fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalCoral)
            androidx.compose.material3.Text("Sign out of the application and reset local preferences.", fontSize = 12.sp, color = FinalBark)
            Spacer(modifier = Modifier.height(12.dp))
            androidx.compose.material3.Button(onClick = onLogout, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = FinalCoral)) {
                androidx.compose.material3.Text("Log Out", color = FinalWhite)
            }
        }
    }
}
