import sys
import re

main_path = r'c:\Users\Naiteek\Downloads\postureapp\didi project\NeckGuardApp\app\src\main\java\com\example\neckguard\MainActivity.kt'
with open(main_path, 'r', encoding='utf-8') as f:
    text = f.read()

# Replace HomeNudgeCmp
old_nudge = """@Composable
fun HomeNudgeCmp() {
    Column(modifier = Modifier.fillMaxWidth().background(FinalCoral, shape = RoundedCornerShape(14.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.Top) { Text("📱", fontSize = 22.sp); Spacer(modifier = Modifier.width(10.dp)); Column { Text("TODAY'S NUDGE", fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite.copy(alpha=0.55f), letterSpacing=0.1.sp); Spacer(modifier = Modifier.height(3.dp)); Text("Raise your phone to eye level right now", fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite, lineHeight = 18.sp) } }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("🟢 Easy", "⏱ 5 seconds", "No eqpt").forEach { tag -> Box(modifier = Modifier.background(FinalWhite.copy(alpha=0.18f), shape = RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) { Text(tag, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalWhite) } } }
        Spacer(modifier = Modifier.height(8.dp))
        Column(modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha=0.1f), shape = RoundedCornerShape(10.dp)).padding(10.dp)) { Text("WHY THIS WORKS", fontSize = 8.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite.copy(alpha=0.5f), letterSpacing = 0.1.sp); Spacer(modifier = Modifier.height(2.dp)); Text("Every 10° of forward head tilt adds ~10 lbs of load on your cervical spine.", fontSize = 10.sp, color = FinalWhite.copy(alpha=0.8f), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, lineHeight = 14.sp) }
    }
}"""

new_nudge = """@Composable
fun HomeNudgeCmp(dashState: com.example.neckguard.ui.DashboardState) {
    Column(modifier = Modifier.fillMaxWidth().background(FinalCoral, shape = RoundedCornerShape(14.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.Top) { Text("📱", fontSize = 22.sp); Spacer(modifier = Modifier.width(10.dp)); Column { Text("TODAY'S NUDGE", fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite.copy(alpha=0.55f), letterSpacing=0.1.sp); Spacer(modifier = Modifier.height(3.dp)); Text(dashState.todayNudge.instruction, fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite, lineHeight = 18.sp) } }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { dashState.todayNudge.tags.forEach { tag -> Box(modifier = Modifier.background(FinalWhite.copy(alpha=0.18f), shape = RoundedCornerShape(20.dp)).padding(horizontal = 8.dp, vertical = 3.dp)) { Text(tag, fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalWhite) } } }
        Spacer(modifier = Modifier.height(8.dp))
        Column(modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha=0.1f), shape = RoundedCornerShape(10.dp)).padding(10.dp)) { Text("WHY THIS WORKS", fontSize = 8.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite.copy(alpha=0.5f), letterSpacing = 0.1.sp); Spacer(modifier = Modifier.height(2.dp)); Text(dashState.todayNudge.reasoning, fontSize = 10.sp, color = FinalWhite.copy(alpha=0.8f), fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, lineHeight = 14.sp) }
    }
}"""

# Replace HomeDetectBannerCmp
old_banner = """@Composable
fun HomeDetectBannerCmp(dashState: com.example.neckguard.ui.DashboardState) {
    Row(modifier = Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.linearGradient(colors = listOf(FinalBark, FinalBarkSoft)), shape = RoundedCornerShape(14.dp)).padding(16.dp)) {
        Text("👁️", fontSize = 26.sp); Spacer(modifier = Modifier.width(12.dp)); Column { Text("DETECTED TODAY", fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite.copy(alpha=0.45f), letterSpacing = 0.1.sp); Spacer(modifier = Modifier.height(4.dp)); Text("High screen distance detected", fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite, lineHeight = 19.sp); Spacer(modifier = Modifier.height(3.dp)); Text("Your phone closer than 30cm for ${dashState.highScreenDistancePct}%.", fontSize = 11.sp, color = FinalWhite.copy(alpha=0.5f), lineHeight = 15.sp); Spacer(modifier = Modifier.height(8.dp)); Box(modifier = Modifier.border(1.dp, FinalWhite.copy(alpha=0.2f), RoundedCornerShape(20.dp)).background(FinalWhite.copy(alpha=0.12f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) { Text("Eye strain • Moderate severity", fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalWhite.copy(alpha=0.7f)) } }
    }
}"""

new_banner = """@Composable
fun HomeDetectBannerCmp(dashState: com.example.neckguard.ui.DashboardState) {
    Row(modifier = Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.linearGradient(colors = listOf(FinalBark, FinalBarkSoft)), shape = RoundedCornerShape(14.dp)).padding(16.dp)) {
        Text("👁️", fontSize = 26.sp); Spacer(modifier = Modifier.width(12.dp)); Column { Text("DETECTED TODAY", fontSize = 9.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite.copy(alpha=0.45f), letterSpacing = 0.1.sp); Spacer(modifier = Modifier.height(4.dp)); Text(dashState.detectedToday.title, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalWhite, lineHeight = 19.sp); Spacer(modifier = Modifier.height(3.dp)); Text(dashState.detectedToday.subtitle, fontSize = 11.sp, color = FinalWhite.copy(alpha=0.5f), lineHeight = 15.sp); Spacer(modifier = Modifier.height(8.dp)); Box(modifier = Modifier.border(1.dp, FinalWhite.copy(alpha=0.2f), RoundedCornerShape(20.dp)).background(FinalWhite.copy(alpha=0.12f), RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 4.dp)) { Text(dashState.detectedToday.severityTag, fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalWhite.copy(alpha=0.7f)) } }
    }
}"""

# Fix call to HomeNudgeCmp
text = text.replace('HomeNudgeCmp()', 'HomeNudgeCmp(dashState)')

text = text.replace(old_nudge, new_nudge)
text = text.replace(old_banner, new_banner)

with open(main_path, 'w', encoding='utf-8') as f:
    f.write(text)

print('Updated UI Components.')
