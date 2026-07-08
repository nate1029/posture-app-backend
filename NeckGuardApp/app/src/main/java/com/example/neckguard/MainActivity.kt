package com.example.neckguard

import android.Manifest
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
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.compose.animation.core.animateFloat
import com.example.neckguard.ui.MainViewModel
import com.example.neckguard.ui.NudgeUpBrandPill
import com.example.neckguard.ui.SettingsIconButton
import com.example.neckguard.ui.IntervalOptions
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
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var repository: com.example.neckguard.data.UserRepository
    private lateinit var viewModel: com.example.neckguard.ui.MainViewModel
    val pendingExerciseNavigation = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        checkIntentForNavigation(intent)
    }

    private fun checkIntentForNavigation(intent: android.content.Intent?) {
        if (intent?.action == "OPEN_EXERCISE") {
            pendingExerciseNavigation.value = intent.getStringExtra("exercise_name")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkIntentForNavigation(intent)

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
                        is com.example.neckguard.ui.AppState.NeedsAppIntro -> {
                            com.example.neckguard.ui.AppIntroScreen {
                                viewModel.finishAppIntro()
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
                        is com.example.neckguard.ui.AppState.Error -> {
                            androidx.compose.foundation.layout.Box(
                                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) {
                                androidx.compose.foundation.layout.Column(
                                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                                ) {
                                    androidx.compose.material3.Text(
                                        text = (appState as com.example.neckguard.ui.AppState.Error).message,
                                        color = androidx.compose.ui.graphics.Color.Red,
                                        modifier = androidx.compose.ui.Modifier.padding(16.dp)
                                    )
                                    androidx.compose.material3.Button(onClick = { viewModel.logout(this@MainActivity) }) {
                                        androidx.compose.material3.Text("Log Out")
                                    }
                                }
                            }
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



    val dashState by viewModel.dashboardState.collectAsState()
    var currentTab by remember { mutableStateOf("Home") }
    var previousTab by remember { mutableStateOf("Home") }
    
    val activity = LocalContext.current as MainActivity
    var initialExercise by remember { mutableStateOf<String?>(null) }
    val pendingNav by activity.pendingExerciseNavigation.collectAsState()
    
    LaunchedEffect(pendingNav) {
        if (pendingNav != null) {
            initialExercise = pendingNav
            currentTab = "Exercises"
            activity.pendingExerciseNavigation.value = null // Consume
        }
    }
    var selectedInterval by remember { mutableStateOf(prefs.getLong("IntervalPreferenceMs", 30 * 60 * 1000L)) }
    var telemetryEnabled by remember {
        mutableStateOf(com.example.neckguard.TelemetryConsent.isEnabled(prefs))
    }
    var showPrivacyPolicy by remember { mutableStateOf(false) }

    val multiplePermissionsLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasCameraPerm = permissions[android.Manifest.permission.CAMERA] ?: hasCameraPerm
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            hasNotifPerm = permissions[android.Manifest.permission.POST_NOTIFICATIONS] ?: hasNotifPerm
        }

    }

    LaunchedEffect(Unit) {
        val permissionsToRequest = mutableListOf(android.Manifest.permission.CAMERA)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(android.Manifest.permission.POST_NOTIFICATIONS)
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
        androidx.compose.animation.Crossfade(
            targetState = currentTab,
            modifier = Modifier.fillMaxSize(),
            label = "tab_transition",
            animationSpec = androidx.compose.animation.core.tween(300)
        ) { tab ->
            when (tab) {
                "Exercises" -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        com.example.neckguard.ui.ExercisesScreen(viewModel, initialExercise = initialExercise, onSettingsClick = { previousTab = currentTab; currentTab = "Settings" })
                    }
                }
                "Settings" -> {
                    val isDeletingAccount by viewModel.isDeletingAccount.collectAsState()
                    val deleteError by viewModel.deleteAccountError.collectAsState()
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Spacer(modifier = Modifier.statusBarsPadding())
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
                                onIntervalChange = { ms -> selectedInterval = ms; prefs.edit().putLong("IntervalPreferenceMs", ms).apply(); viewModel.updateNextNudge() },
                                hasCameraPerm = hasCameraPerm,
                                hasNotifPerm = hasNotifPerm,

                                onFixPerm = { perm -> multiplePermissionsLauncher.launch(arrayOf(perm)) },
                                onLogout = {
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
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                        val intent = android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        intent.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        context.startActivity(intent)
                                    } else {
                                        val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                                        intent.data = android.net.Uri.parse("package:${context.packageName}")
                                        context.startActivity(intent)
                                    }
                                },
                                onShowPrivacyPolicy = {
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://nudgeupteam.github.io/Privacy/privacy_policy.html"))
                                    context.startActivity(intent)
                                },
                                onShowAbout = { previousTab = currentTab; currentTab = "About" },
                                onDeleteAccount = { viewModel.deleteAccount(context) },
                                isDeletingAccount = isDeletingAccount,
                                deleteAccountError = deleteError,
                                onClearDeleteError = { viewModel.clearDeleteError() }
                            )
                        }
                        Spacer(modifier = Modifier.height(140.dp))
                    }
                }
                "About" -> {
                    com.example.neckguard.ui.AboutScreen(onBack = { currentTab = previousTab })
                }
                "Rewards" -> {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        RewardsTab(viewModel, onSettingsClick = { previousTab = currentTab; currentTab = "Settings" })
                        Spacer(modifier = Modifier.height(140.dp))
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
                        Spacer(modifier = Modifier.height(140.dp))
                    }
                }
            }
        }

        val isBottomBarVisible = currentTab != "Settings" && currentTab != "About"
        val bottomBarOffsetY by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (isBottomBarVisible) 0f else 150f,
            animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing)
        )
        val bottomBarAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (isBottomBarVisible) 1f else 0f,
            animationSpec = androidx.compose.animation.core.tween(200)
        )

        val density = androidx.compose.ui.platform.LocalDensity.current.density
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    translationY = bottomBarOffsetY * density
                    alpha = bottomBarAlpha
                }
        ) {
            if (bottomBarAlpha > 0f) {
                BottomNavBar(
                    currentTab = currentTab,
                    onTabChange = { currentTab = it }
                )
            }
        }
    }
}


@Composable
fun BottomNavBar(currentTab: String, onTabChange: (String) -> Unit, modifier: Modifier = Modifier) {
    // No navigationBarsPadding() here — NavigationBar's built-in windowInsets draws its
    // white container down behind the system nav bar while insetting the items above it.
    // Adding the padding pushed the whole bar up and left a gap showing content underneath
    // (UI_POLISH_BACKLOG BUG-08).
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
            HomeStatsRowCmp(dashState)
            HomePostureSummaryCmp(dashState)
            HomeStreakCmp(dashState)
            HomeNudgeCmp(dashState)
            HomeExercisesCmp(dashState, exercisesState, viewModel, onNavigateToExercises)
        }
    }
}

@Composable
fun HomeHeaderCmp(userName: String, dashState: com.example.neckguard.ui.DashboardState, onToggle: (Boolean)->Unit, onSettings: ()->Unit) {
    val headerDate = remember {
        java.text.SimpleDateFormat("EEEE, d MMMM", java.util.Locale.getDefault())
            .format(java.util.Date())
            .uppercase(java.util.Locale.getDefault())
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(androidx.compose.ui.graphics.Brush.linearGradient(colors = listOf(FinalMoss, FinalSage, FinalSageLight)))
            .statusBarsPadding()
            .padding(top = 16.dp, start = 22.dp, end = 22.dp, bottom = 20.dp)
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                NudgeUpBrandPill()
                SettingsIconButton(onClick = onSettings)
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("$headerDate · POSTURE SCAN COMPLETE", fontSize = 13.sp, color = FinalWhite.copy(alpha=0.8f), letterSpacing = 0.8.sp)
            Spacer(modifier = Modifier.height(20.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(108.dp), contentAlignment = Alignment.Center) {
                    val animatedScore by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = dashState.postureScore / 100f,
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 1000, easing = androidx.compose.animation.core.EaseOutCubic)
                    )
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawArc(color = FinalWhite.copy(alpha=0.25f), startAngle = 0f, sweepAngle = 360f, useCenter = false, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 9.dp.toPx()))
                        drawArc(color = FinalWhite, startAngle = -90f, sweepAngle = 360f * animatedScore, useCenter = false, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 9.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${dashState.postureScore}", fontSize = 35.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite)
                        Text("SCORE", fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalWhite.copy(alpha=0.8f), letterSpacing = 0.5.sp)
                    }
                }

                Spacer(modifier = Modifier.width(18.dp))
                Column {
                    val statusTitle = if(dashState.postureScore > 80) "Superb 🌟" else if(dashState.postureScore > 60) "Good 👍" else "Needs Work ⚠️"
                    Row(modifier = Modifier.background(FinalWhite.copy(alpha=0.15f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(statusTitle, fontSize = 12.sp, color = FinalWhite, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(9.dp))
                    val statusSubtitle = when {
                        dashState.postureScore > 80 -> "You're crushing it. Keep those habits locked in."
                        dashState.postureScore > 60 -> "Small adjustments can move you to \"Superb\" today. Keep going."
                        else -> "Small adjustments can move you to \"Good\" today. Keep going."
                    }
                    Text(statusSubtitle, fontSize = 15.sp, color = FinalWhite.copy(alpha=0.9f), lineHeight = 20.sp)
                    Spacer(modifier = Modifier.height(5.dp))
                    val deltaText = if(dashState.scoreDelta > 0) "↑ +${dashState.scoreDelta} vs yesterday" else if(dashState.scoreDelta < 0) "↓ ${dashState.scoreDelta} vs yesterday" else "Same as yesterday"
                    Text(deltaText, fontSize = 14.sp, color = FinalWhite.copy(alpha=0.8f))
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Button(
                onClick = { onToggle(!dashState.isAppActive) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (dashState.isAppActive) FinalWhite else FinalEarthPale),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(if (dashState.isAppActive) Icons.Default.Check else Icons.Default.PlayArrow, contentDescription = null, tint = FinalMoss, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (dashState.isAppActive) "MONITORING ACTIVE" else "RESUME MONITORING", color = FinalMoss, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 0.5.sp)
            }
        }
    }
}

@Composable
fun HomeStatsRowCmp(dashState: com.example.neckguard.ui.DashboardState) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(modifier = Modifier.weight(1f).background(FinalWhite, RoundedCornerShape(14.dp)).border(1.dp, FinalMist, RoundedCornerShape(14.dp)).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⚡", fontSize = 19.sp)
            Spacer(modifier = Modifier.height(5.dp))
            Text("${dashState.streakDays}", fontSize = 21.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalMoss)
            Text("DAY STREAK", fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalMuted, letterSpacing = 0.3.sp)
        }
        Column(modifier = Modifier.weight(1f).background(FinalWhite, RoundedCornerShape(14.dp)).border(1.dp, FinalMist, RoundedCornerShape(14.dp)).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🏋️", fontSize = 19.sp)
            Spacer(modifier = Modifier.height(5.dp))
            Text("${dashState.completedExercisesCount}/${dashState.activeExercisesCount}", fontSize = 21.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalMoss)
            Text("EXERCISES", fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalMuted, letterSpacing = 0.3.sp)
        }
        Column(modifier = Modifier.weight(1f).background(FinalWhite, RoundedCornerShape(14.dp)).border(1.dp, FinalMist, RoundedCornerShape(14.dp)).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🔔", fontSize = 19.sp)
            Spacer(modifier = Modifier.height(5.dp))
            Text("${dashState.nudgesToday}", fontSize = 21.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalCoral)
            Text("NUDGES", fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalMuted, letterSpacing = 0.3.sp)
        }
    }
}

@Composable
fun HomePostureSummaryCmp(dashState: com.example.neckguard.ui.DashboardState) {
    val totalMs = dashState.totalMonitoredMs
    val healthyMs = dashState.healthyMs
    val slouchedMs = dashState.slouchedMs

    val hasData = totalMs > 0L
    val healthyPct = if (hasData) ((healthyMs.toFloat() / totalMs) * 100).toInt().coerceIn(0, 100) else 0
    val slouchedPct = if (hasData) 100 - healthyPct else 0

    fun formatTime(ms: Long): String {
        val totalSecs = ms / 1000
        val hours = totalSecs / 3600
        val mins = (totalSecs % 3600) / 60
        return if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(FinalWhite, RoundedCornerShape(16.dp))
            .border(1.dp, FinalMist, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("\uD83D\uDCCA", fontSize = 17.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "POSTURE SUMMARY",
                    fontSize = 11.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = FinalMuted,
                    letterSpacing = 0.8.sp
                )
            }
            Text(
                "Today",
                fontSize = 12.sp,
                color = FinalMuted,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        if (!hasData) {
            // Empty state
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("\uD83D\uDCCA", fontSize = 28.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Monitoring is active! Your posture breakdown will appear here shortly.",
                    fontSize = 13.sp,
                    color = FinalMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            return
        }

        // Progress bar
        val animatedHealthy by androidx.compose.animation.core.animateFloatAsState(
            targetValue = healthyPct / 100f,
            animationSpec = androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.EaseOutCubic)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(FinalCoral.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedHealthy)
                    .clip(RoundedCornerShape(6.dp))
                    .background(FinalMoss)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Stats row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Good posture
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(FinalMoss, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        formatTime(healthyMs),
                        fontSize = 18.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = FinalMoss
                    )
                    Text(
                        "Good posture ($healthyPct%)",
                        fontSize = 11.sp,
                        color = FinalMuted
                    )
                }
            }

            // Bad posture
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(FinalCoral, CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Text(
                        formatTime(slouchedMs),
                        fontSize = 18.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        color = FinalCoral
                    )
                    Text(
                        "Bad posture ($slouchedPct%)",
                        fontSize = 11.sp,
                        color = FinalMuted
                    )
                }
            }

            // Total
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatTime(totalMs),
                    fontSize = 18.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    color = FinalBark
                )
                Text(
                    "Total tracked",
                    fontSize = 11.sp,
                    color = FinalMuted
                )
            }
        }
    }
}

@Composable
fun HomeStreakCmp(dashState: com.example.neckguard.ui.DashboardState) {
    Row(modifier = Modifier.fillMaxWidth().background(FinalBark, RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("🔥", fontSize = 21.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text("${dashState.streakDays}-day streak", fontSize = 17.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalEarthLight)
                Text("Check in tomorrow to keep it alive", fontSize = 13.sp, color = FinalEarthPale.copy(alpha=0.7f))
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("NEXT NUDGE", fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalEarthLight.copy(alpha=0.7f), letterSpacing = 0.5.sp)
            Text("in ${dashState.nextNudgeMins} min", fontSize = 19.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalEarthLight)
        }
    }
}

@Composable
fun HomeNudgeCmp(dashState: com.example.neckguard.ui.DashboardState) {
    Column(modifier = Modifier.fillMaxWidth().background(FinalCoral, RoundedCornerShape(16.dp)).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(30.dp).background(FinalWhite.copy(alpha=0.2f), RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                Text("💡", fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text("TODAY'S NUDGE", fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite.copy(alpha=0.8f), letterSpacing = 0.5.sp)
                Text("Fact", fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalWhite)
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text("\"${dashState.todayNudge.instruction}\"", fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, color = FinalWhite, lineHeight = 24.sp)
        Spacer(modifier = Modifier.height(10.dp))
        Column(modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha=0.15f), RoundedCornerShape(10.dp)).padding(11.dp)) {
            Text("WHY THIS WORKS", fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite.copy(alpha=0.7f), letterSpacing = 0.5.sp)
            Spacer(modifier = Modifier.height(3.dp))
            Text(dashState.todayNudge.reasoning, fontSize = 14.sp, color = FinalWhite.copy(alpha=0.9f), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, lineHeight = 20.sp)
        }
    }
}

@Composable
fun HomeExercisesCmp(dashState: com.example.neckguard.ui.DashboardState, exercisesState: com.example.neckguard.ui.ExercisesState, viewModel: MainViewModel, onNavigateToExercises: (String?) -> Unit) {
    Column {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("TODAY'S EXERCISES", fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalMuted, letterSpacing = 0.8.sp)
            Text("See all →", fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalSage, modifier = Modifier.clickable { onNavigateToExercises(null) })
        }
        Column(modifier = Modifier.fillMaxWidth().background(FinalWhite, RoundedCornerShape(16.dp)).border(1.dp, FinalMist, RoundedCornerShape(16.dp))) {
            Row(modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 13.dp, bottom = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Suggested exercises", fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalBark)
                Text("${dashState.completedExercisesCount} / ${dashState.activeExercisesCount} done", fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, color = FinalMuted)
            }
            Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(bottom = 10.dp).height(3.dp).background(FinalMist, RoundedCornerShape(2.dp))) {
                val progress = if (dashState.activeExercisesCount > 0) dashState.completedExercisesCount.toFloat() / dashState.activeExercisesCount else 0f
                val animatedProgress by androidx.compose.animation.core.animateFloatAsState(targetValue = progress, animationSpec = androidx.compose.animation.core.tween(400))
                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(animatedProgress).background(FinalMoss, RoundedCornerShape(2.dp)))
            }

            dashState.assignedExercises.forEachIndexed { index, exerciseName ->
                val isDone = exercisesState.doneIds.contains(exerciseName)
                val cat = com.example.neckguard.ui.ExerciseData.exercises.firstOrNull { it.title == exerciseName }?.category ?: "Tag"
                Row(modifier = Modifier.fillMaxWidth().clickable { onNavigateToExercises(exerciseName) }.padding(horizontal = 14.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(24.dp).background(if (isDone) FinalMoss else FinalMist, CircleShape).border(1.5.dp, if (isDone) FinalMoss else FinalMuted.copy(alpha=0.3f), CircleShape).clickable { viewModel.toggleExerciseDone(exerciseName) }, contentAlignment = Alignment.Center) {
                        if (isDone) Icon(Icons.Default.Check, contentDescription = null, tint = FinalWhite, modifier = Modifier.size(13.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(exerciseName, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = if (isDone) FinalMuted else FinalBark, textDecoration = if (isDone) androidx.compose.ui.text.style.TextDecoration.LineThrough else null, modifier = Modifier.weight(1f))
                    Box(modifier = Modifier.background(FinalEarthPale, RoundedCornerShape(8.dp)).padding(horizontal = 9.dp, vertical = 3.dp)) {
                        Text(cat, fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, color = FinalBarkSoft)
                    }
                }
                if (index < dashState.assignedExercises.lastIndex) {
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(FinalMist))
                }
            }
        }
    }
}



@Composable
fun RewardsTab(viewModel: MainViewModel, onSettingsClick: () -> Unit) {
    val state by viewModel.rewardsState.collectAsState()
    val dashState by viewModel.dashboardState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(modifier = Modifier.fillMaxSize().background(FinalCream)) {
        Box(modifier = Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.linearGradient(colors = listOf(FinalSage, FinalSageLight))).statusBarsPadding().padding(top = 16.dp, start = 22.dp, end = 22.dp, bottom = 24.dp)) {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { NudgeUpBrandPill(); SettingsIconButton(onClick = onSettingsClick) }
                Spacer(modifier = Modifier.height(10.dp)); Text("PROGRESS & REWARDS", fontSize = 14.sp, color = FinalWhite.copy(alpha=0.55f), letterSpacing = 0.07.sp); Spacer(modifier = Modifier.height(14.dp))
                val scoreTitle = when {
                    dashState.postureScore > 80 -> "Superb posture 🌟"
                    dashState.postureScore > 60 -> "Good posture 👍"
                    dashState.postureScore > 0  -> "Needs work ⚠️"
                    else -> "No data yet"
                }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("${dashState.postureScore}", fontSize = 56.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite); Spacer(modifier = Modifier.width(16.dp)); Column { Text("TODAY'S SCORE", fontSize = 13.sp, color = FinalWhite.copy(alpha=0.6f), letterSpacing = 0.07.sp); Text(scoreTitle, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalWhite); Text(if(dashState.scoreDelta >= 0) "↑ +${dashState.scoreDelta} vs yesterday" else "↓ ${dashState.scoreDelta} vs yesterday", fontSize = 13.sp, color = FinalWhite.copy(alpha=0.6f)) } }
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
                    Row(modifier = Modifier.fillMaxWidth().clickable { viewModel.setRewardsSection(key) }.padding(14.dp, 16.dp), verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(36.dp).background(iconBg, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Text(icon, fontSize = 17.sp) }; Spacer(modifier = Modifier.width(10.dp)); Column(modifier = Modifier.weight(1f)) { Text(title, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalBark); if (sub.isNotEmpty()) Text(sub, fontSize = 13.sp, color = FinalMuted) }; Text(if(isExpanded) "▲" else "▼", fontSize = 13.sp, color = FinalMuted) }
                    if (isExpanded) {
                        Box(modifier = Modifier.fillMaxWidth().padding(horizontal=16.dp).height(1.dp).background(FinalMist))
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (key == "week") {
                                // Find max total time for scaling (min 1 hour to prevent huge bars for 5 mins of data)
                                val maxTimeMs = maxOf(state.weekLogStats.maxOfOrNull { it.totalMs } ?: 0L, 3600000L)
                                
                                Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth().height(140.dp).padding(horizontal = 4.dp)) {
                                    state.weekLogStats.forEach { stat ->
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom, modifier = Modifier.fillMaxHeight()) {
                                            if (stat.hasData && stat.totalMs > 0) {
                                                val totalPct = (stat.totalMs.toFloat() / maxTimeMs).coerceIn(0f, 1f)
                                                val healthyPct = ((stat.totalMs - stat.slouchedMs).toFloat() / stat.totalMs).coerceIn(0f, 1f)
                                                val slouchedPct = 1f - healthyPct
                                                
                                                val barHeight = 100.dp * totalPct
                                                
                                                Column(modifier = Modifier.width(16.dp).height(barHeight).clip(RoundedCornerShape(8.dp))) {
                                                    // Top part is red (slouched)
                                                    Box(modifier = Modifier.fillMaxWidth().weight(if (slouchedPct > 0) slouchedPct else 0.001f).background(FinalCoral))
                                                    // Bottom part is green (healthy)
                                                    Box(modifier = Modifier.fillMaxWidth().weight(if (healthyPct > 0) healthyPct else 0.001f).background(FinalMoss))
                                                }
                                            } else {
                                                // Empty state for day
                                                Box(modifier = Modifier.width(16.dp).height(100.dp), contentAlignment = Alignment.BottomCenter) {
                                                    Box(modifier = Modifier.size(6.dp).background(FinalMist, CircleShape))
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(stat.dayLabel, fontSize = 12.sp, color = if(stat.hasData) FinalBark else FinalMuted, fontWeight = if(stat.hasData) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(16.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Column(modifier = Modifier.weight(1f).background(FinalMist, RoundedCornerShape(12.dp)).padding(12.dp)) { Text(String.format("%.1f", state.timeTrackedHours) + "h", fontSize=22.sp, fontWeight=androidx.compose.ui.text.font.FontWeight.Bold, color=FinalBark); Text("Time tracked", fontSize = 13.sp, color=FinalMuted) }; Column(modifier = Modifier.weight(1f).background(FinalMist, RoundedCornerShape(12.dp)).padding(12.dp)) { Text("${state.exercisesDoneTotal}/${state.totalRequiredExercises}", fontSize=22.sp, fontWeight=androidx.compose.ui.text.font.FontWeight.Bold, color=FinalBark); Text("Exercises done", fontSize = 13.sp, color=FinalMuted) } }
                            } else if (key == "rewards") {
                                Row(modifier = Modifier.fillMaxWidth().background(FinalBark, RoundedCornerShape(14.dp)).padding(14.dp, 16.dp), verticalAlignment = Alignment.CenterVertically) { Column(modifier = Modifier.weight(1f)) { Text("${state.points} pts", fontSize=30.sp, fontWeight=androidx.compose.ui.text.font.FontWeight.Bold, color=FinalEarthLight); Text("EARNED THIS WEEK", fontSize = 13.sp, color=FinalWhite.copy(alpha=0.4f), letterSpacing=0.06.sp) }; androidx.compose.material3.Button(onClick={ android.widget.Toast.makeText(context, "Giftcard store coming soon!", android.widget.Toast.LENGTH_SHORT).show() }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = FinalSage), shape = RoundedCornerShape(10.dp)) { Text("Redeem →", fontSize = 14.sp, fontWeight=androidx.compose.ui.text.font.FontWeight.Bold) } }
                                Spacer(modifier = Modifier.height(14.dp)); Text("YOUR TITLES", fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalMuted, letterSpacing = 0.08.sp); Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { Box(modifier = Modifier.background(FinalSage, RoundedCornerShape(20.dp)).padding(horizontal=13.dp, vertical=6.dp)) { Text("Neck Newbie", fontSize = 13.sp, fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold, color=FinalWhite) }; Box(modifier = Modifier.background(FinalSage, RoundedCornerShape(20.dp)).padding(horizontal=13.dp, vertical=6.dp)) { Text("Posture Pro", fontSize = 13.sp, fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold, color=FinalWhite) }; Box(modifier = Modifier.background(FinalMist, RoundedCornerShape(20.dp)).padding(horizontal=13.dp, vertical=6.dp)) { Text("Spine Savant", fontSize = 13.sp, fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold, color=Color(0xFFBBBBBB)) } }
                            } else {
                                Text("Feature coming soon...", fontSize = 14.sp, color = FinalMuted, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
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
    hasCameraPerm: Boolean, hasNotifPerm: Boolean,
    onFixPerm: (String) -> Unit, onLogout: () -> Unit,
    telemetryEnabled: Boolean, onTelemetryChange: (Boolean) -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onShowPrivacyPolicy: () -> Unit = {},
    onShowAbout: () -> Unit = {},
    onDeleteAccount: () -> Unit = {},
    isDeletingAccount: Boolean = false,
    deleteAccountError: String? = null,
    onClearDeleteError: () -> Unit = {}
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
                        fontSize = 13.sp,
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
            androidx.compose.material3.Text("Sets how long you must be reading before posture is evaluated.", fontSize = 14.sp, color = FinalMuted)
            Spacer(modifier = Modifier.height(12.dp))
            // Shared presets (BUG-07) rendered with terse "N Minutes" labels. The 15-second
            // testing option only exists in debug builds (BUG-04) so real users never see it.
            val options = buildList {
                if (BuildConfig.DEBUG) add(15_000L to "15 Seconds (Testing)")
                IntervalOptions.presets.forEach { (ms, _) -> add(ms to "${ms / 60000} Minutes") }
            }
            options.forEach { (ms, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { onIntervalChange(ms) }
                ) {
                    androidx.compose.material3.RadioButton(selected = (selectedInterval == ms), onClick = { onIntervalChange(ms) }, colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = FinalMoss))
                    androidx.compose.material3.Text(label, fontSize = 14.sp, color = FinalBark)
                }
            }

            var customMinutesText by remember { mutableStateOf("") }
            val isCustomSelected = options.none { it.first == selectedInterval }
            
            var isCustomExpanded by remember { mutableStateOf(false) }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { 
                    isCustomExpanded = true
                    if (customMinutesText.isNotEmpty()) {
                        customMinutesText.toLongOrNull()?.let { onIntervalChange(it * 60 * 1000L) }
                    }
                }
            ) {
                androidx.compose.material3.RadioButton(
                    selected = isCustomSelected,
                    onClick = { /* Handled by Row */ },
                    colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = FinalMoss)
                )
                androidx.compose.material3.Text("Custom", fontSize = 14.sp, color = FinalBark)
            }
            
            if (isCustomSelected || isCustomExpanded) {
                Row(
                    verticalAlignment = Alignment.CenterVertically, 
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 8.dp)
                ) {
                    androidx.compose.material3.OutlinedTextField(
                        value = customMinutesText,
                        onValueChange = { customMinutesText = it },
                        modifier = Modifier.width(120.dp).height(56.dp),
                        label = { androidx.compose.material3.Text("Minutes") },
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                        singleLine = true,
                        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = FinalMoss,
                            focusedLabelColor = FinalMoss,
                            cursorColor = FinalMoss
                        )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    androidx.compose.material3.Button(
                        onClick = {
                            val mins = customMinutesText.toLongOrNull()
                            if (mins != null && mins > 0) {
                                onIntervalChange(mins * 60 * 1000L)
                            }
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = FinalMoss),
                        modifier = Modifier.height(56.dp)
                    ) {
                        androidx.compose.material3.Text("Apply")
                    }
                }
                
                if (selectedInterval > 0 && options.none { it.first == selectedInterval }) {
                    val currentMins = selectedInterval / (60 * 1000L)
                    val currentSecs = selectedInterval / 1000L
                    val textLabel = if (currentMins > 0) "$currentMins minutes" else "$currentSecs seconds"
                    androidx.compose.material3.Text(
                        "Currently set to $textLabel",
                        fontSize = 14.sp,
                        color = FinalMoss,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp)
                    )
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
            if (hasCameraPerm && hasNotifPerm) { androidx.compose.material3.Text("All systems nominal. Ready to protect your neck!", fontSize = 14.sp, color = FinalMoss) }
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
                        fontSize = 14.sp,
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

        // ─── Health / Medical Disclaimer (B-04, M-01) ──────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(FinalSagePale.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                .border(1.dp, FinalSageLight, RoundedCornerShape(14.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                androidx.compose.material3.Text("ℹ️", fontSize = 16.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    androidx.compose.material3.Text(
                        "Wellness Disclaimer",
                        fontSize = 14.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                        color = FinalMoss
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    androidx.compose.material3.Text(
                        "NudgeUp is a wellness tool, not a medical device. It does not diagnose, treat, cure, or prevent any disease. Consult a healthcare professional for medical advice.",
                        fontSize = 13.sp,
                        color = FinalBarkSoft,
                        lineHeight = 16.sp
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.fillMaxWidth().background(FinalWhite, RoundedCornerShape(14.dp)).border(1.dp, FinalMist, RoundedCornerShape(14.dp)).padding(16.dp)) {
            androidx.compose.material3.Text("Legal & About", fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalBark)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onShowAbout() }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Text("About NudgeUp", fontSize = 14.sp, color = FinalBark)
                androidx.compose.material3.Text("→", fontSize = 14.sp, color = FinalMuted)
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(FinalMist))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onShowPrivacyPolicy() }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                androidx.compose.material3.Text("Privacy Policy", fontSize = 14.sp, color = FinalBark)
                androidx.compose.material3.Text("→", fontSize = 14.sp, color = FinalMuted)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // ─── Account Section (with Deletion — B-01) ──────────────────
        var showDeleteConfirmation by remember { mutableStateOf(false) }

        Column(modifier = Modifier.fillMaxWidth().background(FinalCoral.copy(alpha=0.1f), RoundedCornerShape(14.dp)).border(1.dp, FinalCoral.copy(alpha=0.3f), RoundedCornerShape(14.dp)).padding(16.dp)) {
            androidx.compose.material3.Text("Account", fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalCoral)
            Spacer(modifier = Modifier.height(12.dp))

            androidx.compose.material3.Button(onClick = onLogout, modifier = Modifier.fillMaxWidth(), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = FinalBark)) {
                androidx.compose.material3.Text("Log Out", color = FinalWhite)
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (deleteAccountError != null) {
                androidx.compose.material3.Text(
                    deleteAccountError,
                    fontSize = 14.sp,
                    color = FinalCoral,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (isDeletingAccount) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = FinalCoral, strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    androidx.compose.material3.Text("Deleting account...", fontSize = 13.sp, color = FinalCoral)
                }
            } else {
                androidx.compose.material3.OutlinedButton(
                    onClick = { showDeleteConfirmation = true },
                    modifier = Modifier.fillMaxWidth(),
                    border = androidx.compose.foundation.BorderStroke(1.dp, FinalCoral),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = FinalCoral)
                ) {
                    androidx.compose.material3.Text("Delete Account", color = FinalCoral)
                }
            }
        }

        // Delete Account Confirmation Dialog
        if (showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                title = {
                    Text(
                        "Delete your account?",
                        style = MaterialTheme.typography.headlineSmall,
                        color = FinalCoral
                    )
                },
                text = {
                    Column {
                        Text(
                            "This will permanently delete:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            color = FinalBark
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        listOf(
                            "Your profile and preferences",
                            "All posture history and session logs",
                            "Exercise progress and points",
                            "Crash reports linked to your account"
                        ).forEach { item ->
                            Text(
                                "  •  $item",
                                style = MaterialTheme.typography.bodySmall,
                                color = FinalBarkSoft,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "This action cannot be undone.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = FinalCoral
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteConfirmation = false
                            onClearDeleteError()
                            onDeleteAccount()
                        }
                    ) {
                        Text("Delete Everything", color = FinalCoral, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation = false }) {
                        Text("Cancel", color = FinalMoss)
                    }
                }
            )
        }
    }
}
