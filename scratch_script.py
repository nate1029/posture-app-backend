import sys

path = r'c:\Users\Naiteek\Downloads\postureapp\didi project\NeckGuardApp\app\src\main\java\com\example\neckguard\MainActivity.kt'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_ui = """
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

@Composable
private fun HomeTab(
    userName: String,
    isAppActive: Boolean,
    onToggleService: (Boolean) -> Unit,
    onSettingsClick: () -> Unit
) {
    val context = LocalContext.current
    val prefs = com.example.neckguard.SecurePrefs.get(context)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(FinalCream)
    ) {
        // HOME HDR
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
                // TOPBAR
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
                        modifier = Modifier
                            .size(36.dp)
                            .background(FinalWhite, shape = CircleShape)
                            .border(2.dp, FinalMist, CircleShape)
                            .clickable { onSettingsClick() },
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
                   Text(userName, fontSize = 26.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic, color = FinalWhite)
                   Text(" 👋", fontSize = 26.sp)
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text("Your posture scan is complete for today.", fontSize = 12.sp, color = FinalWhite.copy(alpha=0.6f))
                Spacer(modifier = Modifier.height(14.dp))

                // SCORE ROW
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FinalWhite.copy(alpha = 0.15f), shape = RoundedCornerShape(16.dp))
                        .padding(14.dp, 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("82", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = FinalWhite)
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("POSTURE SCORE", fontSize = 10.sp, letterSpacing = 0.07.sp, color = FinalWhite.copy(alpha=0.6f))
                        Text("Superb 🌟", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FinalWhite)
                        Text("↑ +6 vs yesterday", fontSize = 11.sp, color = FinalWhite.copy(alpha=0.6f)) 
                    }
                    
                    Box(modifier = Modifier.size(52.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            drawArc(
                                color = FinalWhite.copy(alpha=0.25f),
                                startAngle = 0f, sweepAngle = 360f, useCenter = false,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                            )
                            drawArc(
                                color = FinalWhite,
                                startAngle = -90f, sweepAngle = 360f * 0.82f, useCenter = false,
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(10.dp))
                
                Row(
                    modifier = Modifier
                        .background(FinalWhite.copy(alpha=0.2f), shape = RoundedCornerShape(20.dp))
                        .clickable { onToggleService(!isAppActive) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isAppActive) {
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
        } // End HDR
        
        // BODY
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // STREAK
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FinalBark, shape = RoundedCornerShape(14.dp))
                    .padding(horizontal = 16.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("⚡", fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(7.dp))
                    Text("5-day streak", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FinalEarthLight)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Next nudge in ", fontSize = 11.sp, color = FinalWhite.copy(alpha=0.4f))
                    Text("18 min", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = FinalWhite.copy(alpha=0.75f))
                }
            }
            
            // NUDGE
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FinalCoral, shape = RoundedCornerShape(14.dp))
                    .padding(16.dp)
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
                        Box(
                            modifier = Modifier
                                .background(FinalWhite.copy(alpha=0.18f), shape = RoundedCornerShape(20.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(tag, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = FinalWhite)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha=0.1f), shape = RoundedCornerShape(10.dp))
                        .padding(10.dp)
                ) {
                    Text("WHY THIS WORKS", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = FinalWhite.copy(alpha=0.5f), letterSpacing = 0.1.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Every 10° of forward head tilt adds ~10 lbs of load on your cervical spine.", fontSize = 10.sp, color = FinalWhite.copy(alpha=0.8f), fontStyle = FontStyle.Italic, lineHeight = 14.sp)
                }
            }
            
            // Section Divider
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Box(modifier = Modifier.weight(1f).height(1.dp).background(FinalMist))
                Spacer(modifier = Modifier.width(10.dp))
                Text("TODAY'S EXERCISES", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.1.sp, color = FinalMuted)
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f).height(1.dp).background(FinalMist))
            }
            
            // TODAY'S EXERCISES
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FinalWhite, shape = RoundedCornerShape(14.dp))
                    .border(1.dp, FinalMist, RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Your assigned routine", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FinalBark)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Based on today's scan • 2/3 completed", fontSize = 10.sp, color = FinalMuted)
                    }
                    Text("See all →", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FinalSage) 
                }
                Spacer(modifier = Modifier.height(10.dp))
                
                val exercises = listOf(
                    Triple("Chin Tuck", "Cervical", true),
                    Triple("Scapular Retractions", "Strengthening", true),
                    Triple("Cervical Flexion", "Cervical", false)
                )
                exercises.forEach { (name, cat, isDone) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 7.dp)
                            .background(FinalMist, shape = RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(9.dp).background(FinalSageLight, CircleShape))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            name, 
                            fontSize = 12.sp, 
                            fontWeight = FontWeight.SemiBold, 
                            color = if (isDone) FinalMuted else FinalBark,
                            textDecoration = if (isDone) androidx.compose.ui.text.style.TextDecoration.LineThrough else null,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(cat, fontSize = 9.sp, fontWeight = FontWeight.Medium, color = FinalMuted)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isDone) "✓" else "→", fontSize = 11.sp, color = FinalMuted)
                    }
                }
            }
            
            // Section Divider
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Box(modifier = Modifier.weight(1f).height(1.dp).background(FinalMist))
                Spacer(modifier = Modifier.width(10.dp))
                Text("RECOMMENDED FOR YOU", fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.1.sp, color = FinalMuted)
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f).height(1.dp).background(FinalMist))
            }
            
            // DETECT BANNER
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics.Brush.linearGradient(colors = listOf(FinalBark, FinalBarkSoft)),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(16.dp)
            ) {
                Text("👁️", fontSize = 26.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text("DETECTED TODAY", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = FinalWhite.copy(alpha=0.45f), letterSpacing = 0.1.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("High screen distance detected", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = FinalWhite, lineHeight = 19.sp)
                    Spacer(modifier = Modifier.height(3.dp))
                    Text("Your phone closer than 30cm for 68%.", fontSize = 11.sp, color = FinalWhite.copy(alpha=0.5f), lineHeight = 15.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .border(1.dp, FinalWhite.copy(alpha=0.2f), RoundedCornerShape(20.dp))
                            .background(FinalWhite.copy(alpha=0.12f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                         Text("Eye strain • Moderate severity", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = FinalWhite.copy(alpha=0.7f))
                    }
                }
            }
            
            // REC CARDS
            val recs = listOf(
                Triple("20-20-20 Rule", "Eye Relief", "Every 20 min"),
                Triple("Blinking Exercise", "Eye Relief", "10 reps/break")
            )
            recs.forEachIndexed { i, rec ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FinalWhite, shape = RoundedCornerShape(14.dp))
                        .border(1.dp, FinalMist, RoundedCornerShape(14.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier.size(38.dp).background(FinalSagePale, RoundedCornerShape(11.dp)),
                            contentAlignment = Alignment.Center
                        ) {
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(FinalSagePale, RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💡", fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (i==0) "Closest match for sustained near-vision screen use detected today."
                            else "Reduces dry eye caused by reduced blink rate.",
                            fontSize = 10.sp, color = FinalMoss, fontStyle = FontStyle.Italic, lineHeight = 14.sp
                        )
                    }
                }
            }
        }
    }
}
"""

new_lines = []
skip = False
for index, line in enumerate(lines):
    if line.strip() == '@Composable' and index + 1 < len(lines) and 'fun HomeTab' in lines[index+1]:
        skip = True
        new_lines.append(new_ui + "\n")
        
    if skip and line.strip() == '}':
        # we need to make sure we are skipping MiniSquircleCard as well which comes right after HomeTab
        # Actually it's safer to just skip until we see SettingsTab
        pass
        
    if skip and line.strip() == '@Composable' and index + 1 < len(lines) and 'fun SettingsTab' in lines[index+1]:
        skip = False
        
    if not skip:
        new_lines.append(line)

# Since we might have deleted `enum class MiniCardType`, let's remove it if it exists.
cleanup_lines = []
skip_enum = False
for line in new_lines:
    if line.startswith("enum class MiniCardType"):
        continue
    cleanup_lines.append(line)

with open(path, 'w', encoding='utf-8') as f:
    f.writelines(cleanup_lines)

print("Done inserting new UI!")
