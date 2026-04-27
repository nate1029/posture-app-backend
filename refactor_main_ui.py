import sys
import re

main_path = r'c:\Users\Naiteek\Downloads\postureapp\didi project\NeckGuardApp\app\src\main\java\com\example\neckguard\MainActivity.kt'
with open(main_path, 'r', encoding='utf-8') as f:
    text = f.read()

# We need to change the AppScreen calls to pass the viewModel
text = text.replace('HomeTab(userName, isAppActive, onToggleService = { active ->', 'HomeTab(userName, viewModel, onToggleService = { active ->')
text = text.replace('RewardsTab()', 'RewardsTab(viewModel)')
text = text.replace('com.example.neckguard.ui.ExercisesScreen()', 'com.example.neckguard.ui.ExercisesScreen(viewModel)')

print('Updated AppScreen routing.')

with open(main_path, 'w', encoding='utf-8') as f:
    f.write(text)

# We want to replace fun HomeTab(...) with a split version
# First let's extract the HomeTab string using regex
hometab_pattern = r'@Composable\nfun HomeTab.*?\{.*?(?=// BODY).*?\}'
# Actually, standard regex might fail on such a massive block of nested braces.
# I will supply the new HomeTab composables here:

new_hometab = """
@Composable
fun HomeTab(
    userName: String,
    viewModel: com.example.neckguard.ui.MainViewModel,
    onToggleService: (Boolean) -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = com.example.neckguard.SecurePrefs.get(context)
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
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    colors = listOf(FinalMoss, FinalSage, FinalSageLight)
                )
            )
            .padding(top = 52.dp, start = 22.dp, end = 22.dp, bottom = 20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(FinalMoss, shape = RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                     Text("NudgeUp ↑", color = FinalCream, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier.size(36.dp).background(FinalWhite, shape = CircleShape).border(2.dp, FinalMist, CircleShape).clickable { onSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚙", fontSize = 20.sp)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("SATURDAY, 18 APRIL", fontSize = 12.sp, color = FinalWhite.copy(alpha=0.65f), letterSpacing = 0.06.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row {
               Text("Hey, ", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = FinalWhite)
               Text(userName, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = FinalWhite)
               Text(" 👋", fontSize = 26.sp)
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text("Your posture scan is complete for today.", fontSize = 12.sp, color = FinalWhite.copy(alpha=0.6f))
            Spacer(modifier = Modifier.height(14.dp))
            // Score Row
            Row(
                modifier = Modifier.fillMaxWidth().background(FinalWhite.copy(alpha = 0.15f), shape = RoundedCornerShape(16.dp)).padding(14.dp, 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${dashState.postureScore}", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = FinalWhite)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("POSTURE SCORE", fontSize = 10.sp, letterSpacing = 0.07.sp, color = FinalWhite.copy(alpha=0.6f))
                    val title = if(dashState.postureScore > 80) "Superb 🌟" else if(dashState.postureScore > 60) "Good 👍" else "Needs Work ⚠️"
                    Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FinalWhite)
                    Text(if(dashState.scoreDelta >= 0) "↑ +${dashState.scoreDelta} vs yesterday" else "↓ ${dashState.scoreDelta} vs yesterday", fontSize = 11.sp, color = FinalWhite.copy(alpha=0.6f))
                }
                Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        drawArc(color = FinalWhite.copy(alpha=0.25f), startAngle = 0f, sweepAngle = 360f, useCenter = false, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx()))
                        drawArc(color = FinalWhite, startAngle = -90f, sweepAngle = 360f * (dashState.postureScore/100f), useCenter = false, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round))
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.background(FinalWhite.copy(alpha=0.2f), shape = RoundedCornerShape(20.dp)).clickable { onToggle(!dashState.isAppActive) }.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (dashState.isAppActive) {
                    Box(modifier = Modifier.size(7.dp).background(FinalWhite, CircleShape))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("MONITORING ACTIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FinalWhite, letterSpacing = 0.06.sp)
                } else {
                    Box(modifier = Modifier.size(7.dp).background(FinalWhite.copy(alpha=0.5f), CircleShape))
                    Spacer(modifier = Modifier.width(5.dp))
                    Text("PAUSED (TAP TO RESUME)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FinalWhite.copy(alpha=0.8f), letterSpacing = 0.06.sp)
                }
            }
        }
    }
}

@Composable
fun HomeStreakCmp(dashState: com.example.neckguard.ui.DashboardState) {
    Row(
        modifier = Modifier.fillMaxWidth().background(FinalBark, shape = RoundedCornerShape(14.dp)).padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("⚡", fontSize = 16.sp)
            Spacer(modifier = Modifier.width(7.dp))
            Text("${dashState.streakDays}-day streak", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FinalEarthLight)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Next nudge in ", fontSize = 11.sp, color = FinalWhite.copy(alpha=0.4f))
            Text("${dashState.nextNudgeMins} min", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FinalWhite.copy(alpha=0.75f))
        }
    }
}

@Composable
fun HomeNudgeCmp() {
    Column(
        modifier = Modifier.fillMaxWidth().background(FinalCoral, shape = RoundedCornerShape(14.dp)).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text("📱", fontSize = 22.sp)
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text("TODAY'S NUDGE", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = FinalWhite.copy(alpha=0.55f), letterSpacing=0.1.sp)
                Spacer(modifier = Modifier.height(3.dp))
                Text("Raise your phone to eye level right now", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = FinalWhite, lineHeight = 18.sp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("🟢 Easy", "⏱ 5 seconds", "No eqpt").forEach { tag ->
                Box(modifier = Modifier.background(FinalWhite.copy(alpha=0.18f), shape = RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                    Text(tag, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = FinalWhite)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Column(modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha=0.1f), shape = RoundedCornerShape(10.dp)).padding(10.dp)) {
            Text("WHY THIS WORKS", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = FinalWhite.copy(alpha=0.5f), letterSpacing = 0.1.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text("Every 10° of forward head tilt adds ~10 lbs of load on your cervical spine.", fontSize = 10.sp, color = FinalWhite.copy(alpha=0.8f), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, lineHeight = 14.sp)
        }
    }
}

@Composable
fun HomeSectionDivider(title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Box(modifier = Modifier.weight(1f).height(1.dp).background(FinalMist))
        Spacer(modifier = Modifier.width(10.dp))
        Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.1.sp, color = FinalMuted)
        Spacer(modifier = Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f).height(1.dp).background(FinalMist))
    }
}

@Composable
fun HomeExercisesCmp(dashState: com.example.neckguard.ui.DashboardState) {
    Column(
        modifier = Modifier.fillMaxWidth().background(FinalWhite, shape = RoundedCornerShape(14.dp)).border(1.dp, FinalMist, RoundedCornerShape(14.dp)).padding(16.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Your assigned routine", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FinalBark)
                Spacer(modifier = Modifier.height(2.dp))
                Text("Based on today's scan • ${dashState.completedExercisesCount}/${dashState.activeExercisesCount} completed", fontSize = 10.sp, color = FinalMuted)
            }
            Text("See all →", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FinalSage) 
        }
        Spacer(modifier = Modifier.height(10.dp))
        listOf(Triple("Chin Tuck", "Cervical", true), Triple("Scapular Retractions", "Strengthening", true), Triple("Cervical Flexion", "Cervical", false)).forEach { (name, cat, isDone) ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 7.dp).background(FinalMist, shape = RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(9.dp).background(FinalSageLight, CircleShape))
                Spacer(modifier = Modifier.width(10.dp))
                Text(name, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (isDone) FinalMuted else FinalBark, textDecoration = if (isDone) androidx.compose.ui.text.style.TextDecoration.LineThrough else null, modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(10.dp))
                Text(cat, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = FinalMuted)
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (isDone) "✓" else "→", fontSize = 11.sp, color = FinalMuted)
            }
        }
    }
}

@Composable
fun HomeDetectBannerCmp(dashState: com.example.neckguard.ui.DashboardState) {
    Row(
        modifier = Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.linearGradient(colors = listOf(FinalBark, FinalBarkSoft)), shape = RoundedCornerShape(14.dp)).padding(16.dp)
    ) {
        Text("👁️", fontSize = 26.sp)
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text("DETECTED TODAY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = FinalWhite.copy(alpha=0.45f), letterSpacing = 0.1.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Text("High screen distance detected", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FinalWhite, lineHeight = 19.sp)
            Spacer(modifier = Modifier.height(3.dp))
            Text("Your phone closer than 30cm for ${dashState.highScreenDistancePct}%.", fontSize = 11.sp, color = FinalWhite.copy(alpha=0.5f), lineHeight = 15.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.border(1.dp, FinalWhite.copy(alpha=0.2f), RoundedCornerShape(20.dp)).background(FinalWhite.copy(alpha=0.12f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) {
                 Text("Eye strain • Moderate severity", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = FinalWhite.copy(alpha=0.7f))
            }
        }
    }
}

@Composable
fun HomeRecCardsCmp() {
    listOf(Triple("20-20-20 Rule", "Eye Relief", "Every 20 min"), Triple("Blinking Exercise", "Eye Relief", "10 reps/break")).forEachIndexed { i, rec ->
        Column(modifier = Modifier.fillMaxWidth().background(FinalWhite, shape = RoundedCornerShape(14.dp)).border(1.dp, FinalMist, RoundedCornerShape(14.dp)).padding(bottom = 10.dp)) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(38.dp).background(FinalSagePale, RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                    Text("${i+1}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FinalMoss)
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(rec.first, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FinalBark)
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.background(FinalSagePale, RoundedCornerShape(20.dp)).padding(horizontal=8.dp, vertical=2.dp)) {
                             Text(rec.second, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = FinalMoss)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(rec.third, fontSize = 10.sp, color = FinalMuted)
                    }
                }
                Text("→", fontSize = 13.sp, color = FinalMuted)
            }
            Row(modifier = Modifier.fillMaxWidth().background(FinalSagePale, RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp)).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("💡", fontSize = 12.sp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (i==0) "Closest match for sustained near-vision screen use detected today." else "Reduces dry eye caused by reduced blink rate.", fontSize = 10.sp, color = FinalMoss, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, lineHeight = 14.sp)
            }
        }
    }
}
"""

with open(main_path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

out = []
skip = False
for i, l in enumerate(lines):
    if l.startswith("fun HomeTab("):
        # backtrack to remove @Composable
        out.pop()
        skip = True
        out.append(new_hometab)
        
    if skip and "fun RewardsTab" in l:
        skip = False
        # wait, we must backtrack and pop the @Composable
        out.append("@Composable\n")
        
    if not skip:
        out.append(l)

with open(main_path, 'w', encoding='utf-8') as f:
    f.writelines(out)

print("HomeTab extracted & optimized!")
