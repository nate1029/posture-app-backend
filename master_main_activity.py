import sys
import re

main_path = r'c:\Users\Naiteek\Downloads\postureapp\didi project\NeckGuardApp\app\src\main\java\com\example\neckguard\MainActivity.kt'
with open(main_path, 'r', encoding='utf-8') as f:
    text = f.read()

if 'import androidx.compose.runtime.collectAsState' not in text:
    text = text.replace('import androidx.compose.runtime.getValue', 'import androidx.compose.runtime.getValue\nimport androidx.compose.runtime.collectAsState\nimport androidx.compose.ui.graphics.Color\nimport com.example.neckguard.ui.MainViewModel')

# The original routing is inside Scaffold { paddingValues -> Column { if (currentTab == "Home") ... } }
# Let's replace the entire AppScreen body.
start_appscreen = text.find('@Composable\nfun AppScreen(viewModel: com.example.neckguard.ui.MainViewModel) {')
if start_appscreen == -1:
   start_appscreen = text.find('@Composable\nfun AppScreen(viewModel: MainViewModel) {')

if start_appscreen == -1:
   print("Could not find AppScreen")
   sys.exit(1)

# we will keep everything before AppScreen.
text_before = text[:start_appscreen]

new_appscreen = """@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@androidx.compose.runtime.Composable
fun AppScreen(viewModel: com.example.neckguard.ui.MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = com.example.neckguard.SecurePrefs.get(context)

    var hasCameraPerm by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED) }
    var hasNotifPerm by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true)
    }
    var hasActivityPerm by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf(if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACTIVITY_RECOGNITION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        } else true)
    }

    val pm = context.getSystemService(android.content.Context.POWER_SERVICE) as android.os.PowerManager
    var isIgnoringBatteryOptimizations by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(pm.isIgnoringBatteryOptimizations(context.packageName)) }
    
    val dashState by viewModel.dashboardState.collectAsState()
    var currentTab by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("Home") }
    var selectedInterval by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(prefs.getLong("IntervalPreferenceMs", 30 * 60 * 1000L)) }

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

    androidx.compose.runtime.LaunchedEffect(Unit) {
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

    Box(modifier = Modifier.fillMaxSize().background(FinalCream)) {
        androidx.compose.animation.AnimatedContent(
            targetState = currentTab,
            transitionSpec = {
                if (targetState == "Settings" || initialState == "Settings") {
                    (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)) + androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(200)) { it / 4 }) androidx.compose.animation.togetherWith
                    (androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)) + androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(150)) { -it / 5 })
                } else {
                    (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(200)) + androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(200)) { it / 8 }) androidx.compose.animation.togetherWith
                    (androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)))
                }
            },
            modifier = Modifier.fillMaxSize(),
            label = "tab_transition"
        ) { tab ->
            when (tab) {
                "Exercises" -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        com.example.neckguard.ui.ExercisesScreen(viewModel)
                    }
                }
                "Settings" -> {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        Spacer(modifier = Modifier.height(48.dp))
                        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, indication = null) { currentTab = "Home" }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = FinalMuted)
                                Spacer(modifier = Modifier.width(8.dp))
                                androidx.compose.material3.Text("Back to Dashboard", style = androidx.compose.material3.MaterialTheme.typography.bodyLarge, color = FinalMuted)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            // I will keep the original Settings cards simple for now to avoid compilation errors on undefined composables like "SettingsTab"
                            androidx.compose.material3.Text("Settings coming soon", color = FinalBark)
                        }
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
                "Rewards" -> {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        RewardsTab(viewModel)
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
                        }, onSettingsClick = { currentTab = "Settings" })
                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            }
        }

        // Bottom Nav Bar hook
        androidx.compose.material3.NavigationBar(
            containerColor = FinalWhite,
            modifier = Modifier.align(Alignment.BottomCenter).border(1.dp, FinalMist)
        ) {
            val tabs = listOf("Home", "Progress", "Exercises", "Rewards")
            val icons = listOf(androidx.compose.material.icons.Icons.Default.Home, androidx.compose.material.icons.Icons.Default.DateRange, androidx.compose.material.icons.Icons.Default.FavoriteBorder, androidx.compose.material.icons.Icons.Default.Star)
            val mapping = listOf("Home", "Rewards", "Exercises", "Rewards")
            
            tabs.forEachIndexed { index, title ->
                val targetTab = mapping[index]
                androidx.compose.material3.NavigationBarItem(
                    selected = (currentTab == targetTab),
                    onClick = { currentTab = targetTab },
                    icon = { androidx.compose.material3.Icon(icons[index], contentDescription = title) },
                    label = { androidx.compose.material3.Text(title, style = androidx.compose.material3.MaterialTheme.typography.labelSmall) },
                    colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
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
}
"""

new_components = """
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
    viewModel: com.example.neckguard.ui.MainViewModel,
    onToggleService: (Boolean) -> Unit,
    onSettingsClick: () -> Unit
) {
    val dashState by viewModel.dashboardState.collectAsState()

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
            HomeNudgeCmp()
            HomeSectionDivider("TODAY'S EXERCISES")
            HomeExercisesCmp(dashState)
            HomeSectionDivider("RECOMMENDED FOR YOU")
            HomeDetectBannerCmp(dashState)
            HomeRecCardsCmp()
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
                     androidx.compose.material3.Text("NudgeUp ↑", color = FinalCream, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                }
                Box(modifier = Modifier.size(36.dp).background(FinalWhite, shape = CircleShape).border(2.dp, FinalMist, CircleShape).clickable { onSettings() }, contentAlignment = Alignment.Center) {
                    androidx.compose.material3.Text("⚙", fontSize = 20.sp)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.Text("SATURDAY, 18 APRIL", fontSize = 12.sp, color = FinalWhite.copy(alpha=0.65f), letterSpacing = 0.06.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row {
               androidx.compose.material3.Text("Hey, ", fontSize = 26.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite)
               androidx.compose.material3.Text(userName, fontSize = 26.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = FinalWhite)
               androidx.compose.material3.Text(" 👋", fontSize = 26.sp)
            }
            Spacer(modifier = Modifier.height(2.dp))
            androidx.compose.material3.Text("Your posture scan is complete for today.", fontSize = 12.sp, color = FinalWhite.copy(alpha=0.6f))
            Spacer(modifier = Modifier.height(14.dp))
            Row(modifier = Modifier.fillMaxWidth().background(FinalWhite.copy(alpha = 0.15f), shape = RoundedCornerShape(16.dp)).padding(14.dp, 16.dp), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.Text("${dashState.postureScore}", fontSize = 48.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    androidx.compose.material3.Text("POSTURE SCORE", fontSize = 10.sp, letterSpacing = 0.07.sp, color = FinalWhite.copy(alpha=0.6f))
                    val title = if(dashState.postureScore > 80) "Superb 🌟" else if(dashState.postureScore > 60) "Good 👍" else "Needs Work ⚠️"
                    androidx.compose.material3.Text(title, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalWhite)
                    androidx.compose.material3.Text(if(dashState.scoreDelta >= 0) "↑ +${dashState.scoreDelta} vs yesterday" else "↓ ${dashState.scoreDelta} vs yesterday", fontSize = 11.sp, color = FinalWhite.copy(alpha=0.6f))
                }
                Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
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
                    androidx.compose.material3.Text("MONITORING ACTIVE", fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite, letterSpacing = 0.06.sp)
                } else {
                    Box(modifier = Modifier.size(7.dp).background(FinalWhite.copy(alpha=0.5f), CircleShape))
                    Spacer(modifier = Modifier.width(5.dp))
                    androidx.compose.material3.Text("PAUSED (TAP TO RESUME)", fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite.copy(alpha=0.8f), letterSpacing = 0.06.sp)
                }
            }
        }
    }
}

@Composable
fun HomeStreakCmp(dashState: com.example.neckguard.ui.DashboardState) {
    Row(modifier = Modifier.fillMaxWidth().background(FinalBark, shape = RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 11.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) { androidx.compose.material3.Text("⚡", fontSize = 16.sp); Spacer(modifier = Modifier.width(7.dp)); androidx.compose.material3.Text("${dashState.streakDays}-day streak", fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalEarthLight) }
        Row(verticalAlignment = Alignment.CenterVertically) { androidx.compose.material3.Text("Next nudge in ", fontSize = 11.sp, color = FinalWhite.copy(alpha=0.4f)); androidx.compose.material3.Text("${dashState.nextNudgeMins} min", fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite.copy(alpha=0.75f)) }
    }
}

@Composable
fun HomeNudgeCmp() {
    Column(modifier = Modifier.fillMaxWidth().background(FinalCoral, shape = RoundedCornerShape(14.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.Top) { androidx.compose.material3.Text("📱", fontSize = 22.sp); Spacer(modifier = Modifier.width(10.dp)); Column { androidx.compose.material3.Text("TODAY'S NUDGE", fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite.copy(alpha=0.55f), letterSpacing=0.1.sp); Spacer(modifier = Modifier.height(3.dp)); androidx.compose.material3.Text("Raise your phone to eye level right now", fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite, lineHeight = 18.sp) } }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("🟢 Easy", "⏱ 5 seconds", "No eqpt").forEach { tag -> Box(modifier = Modifier.background(FinalWhite.copy(alpha=0.18f), shape = RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) { androidx.compose.material3.Text(tag, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalWhite) } } }
        Spacer(modifier = Modifier.height(8.dp))
        Column(modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha=0.1f), shape = RoundedCornerShape(10.dp)).padding(10.dp)) { androidx.compose.material3.Text("WHY THIS WORKS", fontSize = 8.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite.copy(alpha=0.5f), letterSpacing = 0.1.sp); Spacer(modifier = Modifier.height(2.dp)); androidx.compose.material3.Text("Every 10° of forward head tilt adds ~10 lbs of load on your cervical spine.", fontSize = 10.sp, color = FinalWhite.copy(alpha=0.8f), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, lineHeight = 14.sp) }
    }
}

@Composable
fun HomeSectionDivider(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) { Box(modifier = Modifier.weight(1f).height(1.dp).background(FinalMist)); Spacer(modifier = Modifier.width(10.dp)); androidx.compose.material3.Text(title, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, letterSpacing = 0.1.sp, color = FinalMuted); Spacer(modifier = Modifier.width(10.dp)); Box(modifier = Modifier.weight(1f).height(1.dp).background(FinalMist)) }
}

@Composable
fun HomeExercisesCmp(dashState: com.example.neckguard.ui.DashboardState) {
    Column(modifier = Modifier.fillMaxWidth().background(FinalWhite, shape = RoundedCornerShape(14.dp)).border(1.dp, FinalMist, RoundedCornerShape(14.dp)).padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Column { androidx.compose.material3.Text("Your assigned routine", fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalBark); Spacer(modifier = Modifier.height(2.dp)); androidx.compose.material3.Text("Based on today's scan • ${dashState.completedExercisesCount}/${dashState.activeExercisesCount} completed", fontSize = 10.sp, color = FinalMuted) }; androidx.compose.material3.Text("See all →", fontSize = 11.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalSage) }
        Spacer(modifier = Modifier.height(10.dp))
        listOf(Triple("Chin Tuck", "Cervical", true), Triple("Scapular Retractions", "Strengthening", true), Triple("Cervical Flexion", "Cervical", false)).forEach { (name, cat, isDone) ->
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp).background(FinalMist, shape = RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(9.dp).background(FinalSageLight, CircleShape)); Spacer(modifier = Modifier.width(10.dp)); androidx.compose.material3.Text(name, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = if (isDone) FinalMuted else FinalBark, textDecoration = if (isDone) androidx.compose.ui.text.style.TextDecoration.LineThrough else null, modifier = Modifier.weight(1f)); Spacer(modifier = Modifier.width(10.dp)); androidx.compose.material3.Text(cat, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium, color = FinalMuted); Spacer(modifier = Modifier.width(4.dp)); androidx.compose.material3.Text(if (isDone) "✓" else "→", fontSize = 11.sp, color = FinalMuted)
            }
        }
    }
}

@Composable
fun HomeDetectBannerCmp(dashState: com.example.neckguard.ui.DashboardState) {
    Row(modifier = Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.linearGradient(colors = listOf(FinalBark, FinalBarkSoft)), shape = RoundedCornerShape(14.dp)).padding(16.dp)) {
        androidx.compose.material3.Text("👁️", fontSize = 26.sp); Spacer(modifier = Modifier.width(12.dp)); Column { androidx.compose.material3.Text("DETECTED TODAY", fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite.copy(alpha=0.45f), letterSpacing = 0.1.sp); Spacer(modifier = Modifier.height(4.dp)); androidx.compose.material3.Text("High screen distance detected", fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite, lineHeight = 19.sp); Spacer(modifier = Modifier.height(3.dp)); androidx.compose.material3.Text("Your phone closer than 30cm for ${dashState.highScreenDistancePct}%.", fontSize = 11.sp, color = FinalWhite.copy(alpha=0.5f), lineHeight = 15.sp); Spacer(modifier = Modifier.height(8.dp)); Box(modifier = Modifier.border(1.dp, FinalWhite.copy(alpha=0.2f), RoundedCornerShape(20.dp)).background(FinalWhite.copy(alpha=0.12f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) { androidx.compose.material3.Text("Eye strain • Moderate severity", fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalWhite.copy(alpha=0.7f)) } }
    }
}

@Composable
fun HomeRecCardsCmp() {
    listOf(Triple("20-20-20 Rule", "Eye Relief", "Every 20 min"), Triple("Blinking Exercise", "Eye Relief", "10 reps/break")).forEachIndexed { i, rec ->
        Column(modifier = Modifier.fillMaxWidth().background(FinalWhite, shape = RoundedCornerShape(14.dp)).border(1.dp, FinalMist, RoundedCornerShape(14.dp)).padding(bottom = 10.dp)) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(38.dp).background(FinalSagePale, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) { androidx.compose.material3.Text("${i+1}", fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalMoss) }; Spacer(modifier = Modifier.width(12.dp)); Column(modifier = Modifier.weight(1f)) { androidx.compose.material3.Text(rec.first, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalBark); Spacer(modifier = Modifier.height(3.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.background(FinalSagePale, RoundedCornerShape(20.dp)).padding(horizontal=8.dp, vertical=2.dp)) { androidx.compose.material3.Text(rec.second, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalMoss) }; Spacer(modifier = Modifier.width(8.dp)); androidx.compose.material3.Text(rec.third, fontSize = 10.sp, color = FinalMuted) } }; androidx.compose.material3.Text("→", fontSize = 13.sp, color = FinalMuted) }
            Row(modifier = Modifier.fillMaxWidth().background(FinalSagePale, RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { androidx.compose.material3.Text("💡", fontSize = 12.sp); Spacer(modifier = Modifier.width(6.dp)); androidx.compose.material3.Text(if (i==0) "Closest match for sustained near-vision screen use detected today." else "Reduces dry eye caused by reduced blink rate.", fontSize = 10.sp, color = FinalMoss, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, lineHeight = 14.sp) }
        }
    }
}

@Composable
fun RewardsTab(viewModel: com.example.neckguard.ui.MainViewModel) {
    val state by viewModel.rewardsState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().background(FinalCream)) {
        Box(modifier = Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.linearGradient(colors = listOf(FinalSage, FinalSageLight))).padding(top = 52.dp, start = 22.dp, end = 22.dp, bottom = 24.dp)) {
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.background(FinalMoss, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 6.dp)) { androidx.compose.material3.Text("NudgeUp ↑", color = FinalCream, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) }; Box(modifier = Modifier.size(36.dp).background(FinalEarthPale, CircleShape).border(2.dp, FinalEarthPale, CircleShape), contentAlignment = Alignment.Center) { androidx.compose.material3.Text("👤", fontSize = 15.sp) } }
                Spacer(modifier = Modifier.height(10.dp)); androidx.compose.material3.Text("PROGRESS & REWARDS", fontSize = 12.sp, color = FinalWhite.copy(alpha=0.55f), letterSpacing = 0.07.sp); Spacer(modifier = Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) { androidx.compose.material3.Text("82", fontSize = 56.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite); Spacer(modifier = Modifier.width(16.dp)); Column { androidx.compose.material3.Text("TODAY'S SCORE", fontSize = 10.sp, color = FinalWhite.copy(alpha=0.6f), letterSpacing = 0.07.sp); androidx.compose.material3.Text("Superb posture 🌟", fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalWhite); androidx.compose.material3.Text("↑ +6 vs yesterday", fontSize = 11.sp, color = FinalWhite.copy(alpha=0.6f)) } }
            }
        }
        
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val sections = listOf(
                Triple("week", "📅", Pair("This week", "Apr 14–18 • Avg score 84")),
                Triple("lb", "🏆", Pair("Leaderboard", "Your rank: #3 this week")),
                Triple("friends", "👥", Pair("Friends' streaks", "3 friends connected")),
                Triple("rewards", "🎁", Pair("Rewards", "420 pts • Posture Pro title"))
            )
            
            sections.forEach { item ->
                val key = item.first; val icon = item.second; val title = item.third.first; val sub = item.third.second
                val isExpanded = state.expandedSection == key
                val iconBg = when(key) { "week" -> FinalSagePale; "lb" -> FinalEarthLight; "friends" -> Color(0xFFDCF0F7); else -> FinalEarthPale }
                
                Column(modifier = Modifier.fillMaxWidth().background(FinalWhite, RoundedCornerShape(16.dp)).border(1.dp, FinalMist, RoundedCornerShape(16.dp))) {
                    Row(modifier = Modifier.fillMaxWidth().clickable { viewModel.setRewardsSection(key) }.padding(14.dp, 16.dp), verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(36.dp).background(iconBg, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { androidx.compose.material3.Text(icon, fontSize = 17.sp) }; Spacer(modifier = Modifier.width(10.dp)); Column(modifier = Modifier.weight(1f)) { androidx.compose.material3.Text(title, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalBark); androidx.compose.material3.Text(sub, fontSize = 10.sp, color = FinalMuted) }; androidx.compose.material3.Text(if(isExpanded) "▲" else "▼", fontSize = 11.sp, color = FinalMuted) }
                    if (isExpanded) {
                        Box(modifier = Modifier.fillMaxWidth().padding(horizontal=16.dp).height(1.dp).background(FinalMist))
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (key == "week") {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    state.weekLog.forEach { item2 -> val day = item2.first; val stat = item2.second; Column(horizontalAlignment = Alignment.CenterHorizontally) { Box(modifier = Modifier.size(32.dp).border(2.5.dp, if(stat=="✓" || stat=="F") FinalSage else if (stat=="!") FinalEarth else Color(0xFFE0E0D8), CircleShape).background(if(stat=="✓") FinalSagePale else if(stat=="F") FinalSage else if(stat=="!") FinalEarthPale else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) { androidx.compose.material3.Text(if(stat=="✓") "✓" else if(stat=="!") "!" else if(stat=="F") day else day, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = if(stat=="✓"||stat=="F") FinalMoss else if(stat=="!") FinalEarth else Color.LightGray) }; Spacer(modifier = Modifier.height(3.dp)); androidx.compose.material3.Text(day, fontSize=9.sp, color=FinalMuted) } }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Column(modifier = Modifier.weight(1f).background(FinalMist, RoundedCornerShape(12.dp)).padding(12.dp)) { androidx.compose.material3.Text(String.format("%.1f", state.timeTrackedHours) + "h", fontSize=22.sp, fontWeight=androidx.compose.ui.text.font.FontWeight.Bold, color=FinalBark); androidx.compose.material3.Text("Time tracked", fontSize=10.sp, color=FinalMuted) }; Column(modifier = Modifier.weight(1f).background(FinalMist, RoundedCornerShape(12.dp)).padding(12.dp)) { androidx.compose.material3.Text("${state.exercisesDoneTotal}/${state.totalRequiredExercises}", fontSize=22.sp, fontWeight=androidx.compose.ui.text.font.FontWeight.Bold, color=FinalBark); androidx.compose.material3.Text("Exercises done", fontSize=10.sp, color=FinalMuted) } }
                            } else if (key == "rewards") {
                                Row(modifier = Modifier.fillMaxWidth().background(FinalBark, RoundedCornerShape(14.dp)).padding(14.dp, 16.dp), verticalAlignment = Alignment.CenterVertically) { Column(modifier = Modifier.weight(1f)) { androidx.compose.material3.Text("${state.points} pts", fontSize=30.sp, fontWeight=androidx.compose.ui.text.font.FontWeight.Bold, color=FinalEarthLight); androidx.compose.material3.Text("EARNED THIS WEEK", fontSize=10.sp, color=FinalWhite.copy(alpha=0.4f), letterSpacing=0.06.sp) }; androidx.compose.material3.Button(onClick={}, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = FinalSage), shape = RoundedCornerShape(10.dp)) { androidx.compose.material3.Text("Redeem →", fontSize=12.sp, fontWeight=androidx.compose.ui.text.font.FontWeight.Bold) } }
                                Spacer(modifier = Modifier.height(14.dp)); androidx.compose.material3.Text("YOUR TITLES", fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalMuted, letterSpacing = 0.08.sp); Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) { Box(modifier = Modifier.background(FinalSage, RoundedCornerShape(20.dp)).padding(horizontal=13.dp, vertical=6.dp)) { androidx.compose.material3.Text("Neck Newbie", fontSize=11.sp, fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold, color=FinalWhite) }; Box(modifier = Modifier.background(FinalSage, RoundedCornerShape(20.dp)).padding(horizontal=13.dp, vertical=6.dp)) { androidx.compose.material3.Text("Posture Pro", fontSize=11.sp, fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold, color=FinalWhite) }; Box(modifier = Modifier.background(FinalMist, RoundedCornerShape(20.dp)).padding(horizontal=13.dp, vertical=6.dp)) { androidx.compose.material3.Text("Spine Savant", fontSize=11.sp, fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold, color=Color(0xFFBBBBBB)) } }
                            } else {
                                androidx.compose.material3.Text("Feature coming soon...", fontSize = 12.sp, color = FinalMuted, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            }
                        }
                    }
                }
            }
        }
    }
}
"""

with open(main_path, 'w', encoding='utf-8') as f:
    f.write(text_before + new_appscreen + new_components)

print("Absolute MainActivity Refactor SUCCESS!")
