"""
Add posture summary to dashboard:
1. Add totalMonitoredMs, healthyMs, slouchedMs to DashboardState
2. Pass them in handleTodayStatsTick
3. Add HomePostureSummaryCmp composable to MainActivity
4. Insert it in the Home tab layout
"""
import sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

# ── PATCH MainViewModel.kt ──
vm_path = r'c:\Users\Naiteek\Downloads\postureapp\didi project\NeckGuardApp\app\src\main\java\com\example\neckguard\ui\MainViewModel.kt'
with open(vm_path, 'r', encoding='utf-8-sig') as f:
    vm = f.read()

patches = []

# 1. Add fields to DashboardState
old_ds = '    val detectedToday: DetectionData = DetectionData("High screen distance detected", "Your phone closer than 30cm for 24%.", "Eye strain • Moderate severity")\n)'
new_ds = '    val detectedToday: DetectionData = DetectionData("High screen distance detected", "Your phone closer than 30cm for 24%.", "Eye strain • Moderate severity"),\n    val totalMonitoredMs: Long = 0L,\n    val healthyMs: Long = 0L,\n    val slouchedMs: Long = 0L\n)'
if old_ds in vm:
    vm = vm.replace(old_ds, new_ds, 1)
    patches.append("1: added time fields to DashboardState")
else:
    print("PATCH 1 NOT FOUND")

# 2. Pass them in handleTodayStatsTick -> dashboardState.value update
old_update = '            nudgesToday = repository.nudgesFiredToday\n        )'
new_update = '            nudgesToday = repository.nudgesFiredToday,\n            totalMonitoredMs = totalMs,\n            healthyMs = totalMs - slouchedMs,\n            slouchedMs = slouchedMs\n        )'
if old_update in vm:
    vm = vm.replace(old_update, new_update, 1)
    patches.append("2: passing time data to DashboardState")
else:
    print("PATCH 2 NOT FOUND")

with open(vm_path, 'w', encoding='utf-8-sig') as f:
    f.write(vm)
print(f"MainViewModel patches: {patches}")

# ── PATCH MainActivity.kt ──
ma_path = r'c:\Users\Naiteek\Downloads\postureapp\didi project\NeckGuardApp\app\src\main\java\com\example\neckguard\MainActivity.kt'
with open(ma_path, 'r', encoding='utf-8-sig') as f:
    ma = f.read()

patches2 = []

# 3. Insert HomePostureSummaryCmp call in Home tab layout (after HomeStatsRowCmp)
old_layout = '            HomeStatsRowCmp(dashState)\n            HomeStreakCmp(dashState)'
new_layout = '            HomeStatsRowCmp(dashState)\n            HomePostureSummaryCmp(dashState)\n            HomeStreakCmp(dashState)'
if old_layout in ma:
    ma = ma.replace(old_layout, new_layout, 1)
    patches2.append("3: added HomePostureSummaryCmp to layout")
else:
    print("PATCH 3 NOT FOUND")

# 4. Add the HomePostureSummaryCmp composable function right before HomeStreakCmp
old_streak_fn = '@Composable\nfun HomeStreakCmp(dashState: com.example.neckguard.ui.DashboardState) {'
new_summary_and_streak = '''@Composable
fun HomePostureSummaryCmp(dashState: com.example.neckguard.ui.DashboardState) {
    val totalMs = dashState.totalMonitoredMs
    val healthyMs = dashState.healthyMs
    val slouchedMs = dashState.slouchedMs

    // Don't show if no data yet
    if (totalMs <= 0L) return

    val healthyPct = ((healthyMs.toFloat() / totalMs) * 100).toInt().coerceIn(0, 100)
    val slouchedPct = 100 - healthyPct

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
                Text("\\uD83D\\uDCCA", fontSize = 17.sp)
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
fun HomeStreakCmp(dashState: com.example.neckguard.ui.DashboardState) {'''

if old_streak_fn in ma:
    ma = ma.replace(old_streak_fn, new_summary_and_streak, 1)
    patches2.append("4: added HomePostureSummaryCmp composable")
else:
    print("PATCH 4 NOT FOUND")

with open(ma_path, 'w', encoding='utf-8-sig') as f:
    f.write(ma)
print(f"MainActivity patches: {patches2}")
print("Done!")
