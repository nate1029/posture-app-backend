import sys

path = r'c:\Users\Naiteek\Downloads\postureapp\didi project\NeckGuardApp\app\src\main\java\com\example\neckguard\MainActivity.kt'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_ui = """
@Composable
fun RewardsTab() {
    var expandedSection by remember { mutableStateOf("week") }

    Column(modifier = Modifier.fillMaxSize().background(FinalCream)) {
        // HDR
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Brush.linearGradient(colors = listOf(FinalSage, FinalSageLight)))
                .padding(top = 52.dp, start = 22.dp, end = 22.dp, bottom = 24.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.background(FinalMoss, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 6.dp)) {
                        Text("NudgeUp ↑", color = FinalCream, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(modifier = Modifier.size(36.dp).background(FinalEarthPale, CircleShape).border(2.dp, FinalEarthPale, CircleShape), contentAlignment = Alignment.Center) {
                        Text("👤", fontSize = 15.sp)
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text("PROGRESS & REWARDS", fontSize = 12.sp, color = FinalWhite.copy(alpha=0.55f), letterSpacing = 0.07.sp)
                Spacer(modifier = Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("82", fontSize = 56.sp, fontWeight = FontWeight.Bold, color = FinalWhite)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text("TODAY'S SCORE", fontSize = 10.sp, color = FinalWhite.copy(alpha=0.6f), letterSpacing = 0.07.sp)
                        Text("Superb posture 🌟", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FinalWhite)
                        Text("↑ +6 vs yesterday", fontSize = 11.sp, color = FinalWhite.copy(alpha=0.6f))
                    }
                }
            }
        }
        
        // Accordions
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val sections = listOf(
                Triple("week", "📅", "This week" to "Apr 14–18 • Avg score 84"),
                Triple("lb", "🏆", "Leaderboard" to "Your rank: #3 this week"),
                Triple("friends", "👥", "Friends' streaks" to "3 friends connected"),
                Triple("rewards", "🎁", "Rewards" to "420 pts • Posture Pro title")
            )
            
            sections.forEach { (key, icon, texts) ->
                val (title, sub) = texts
                val isExpanded = expandedSection == key
                val iconBg = when(key) {
                    "week" -> FinalSagePale
                    "lb" -> FinalEarthLight
                    "friends" -> Color(0xFFDCF0F7)
                    else -> FinalEarthPale
                }
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FinalWhite, RoundedCornerShape(16.dp))
                        .border(1.dp, FinalMist, RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedSection = if(isExpanded) "" else key }
                            .padding(14.dp, 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(36.dp).background(iconBg, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                            Text(icon, fontSize = 17.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FinalBark)
                            Text(sub, fontSize = 10.sp, color = FinalMuted)
                        }
                        Text(if(isExpanded) "▲" else "▼", fontSize = 11.sp, color = FinalMuted)
                    }
                    
                    if (isExpanded) {
                        Box(modifier = Modifier.fillMaxWidth().padding(horizontal=16.dp).height(1.dp).background(FinalMist))
                        Column(modifier = Modifier.padding(16.dp)) {
                            if (key == "week") {
                                // Rings
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    listOf("M" to "✓", "T" to "✓", "W" to "✓", "T" to "!", "F" to "F", "S" to "", "S" to "").forEach { (day, stat) ->
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Box(
                                                modifier = Modifier.size(32.dp).border(2.5.dp, if(stat=="✓" || stat=="F") FinalSage else if (stat=="!") FinalEarth else Color(0xFFE0E0D8), CircleShape).background(if(stat=="✓") FinalSagePale else if(stat=="F") FinalSage else if(stat=="!") FinalEarthPale else Color.Transparent, CircleShape),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(if(stat=="✓") "✓" else if(stat=="!") "!" else if(stat=="F") day else day, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = if(stat=="✓"||stat=="F") FinalMoss else if(stat=="!") FinalEarth else Color.LightGray)
                                            }
                                            Spacer(modifier = Modifier.height(3.dp))
                                            Text(day, fontSize=9.sp, color=FinalMuted)
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                // Stats Grid
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Column(modifier = Modifier.weight(1f).background(FinalMist, RoundedCornerShape(12.dp)).padding(12.dp)) { Text("11.4h", fontSize=22.sp, fontWeight=FontWeight.Bold, color=FinalBark); Text("Time tracked", fontSize=10.sp, color=FinalMuted) }
                                    Column(modifier = Modifier.weight(1f).background(FinalMist, RoundedCornerShape(12.dp)).padding(12.dp)) { Text("9/14", fontSize=22.sp, fontWeight=FontWeight.Bold, color=FinalBark); Text("Exercises done", fontSize=10.sp, color=FinalMuted) }
                                }
                            } else if (key == "rewards") {
                                Row(modifier = Modifier.fillMaxWidth().background(FinalBark, RoundedCornerShape(14.dp)).padding(14.dp, 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("420 pts", fontSize=30.sp, fontWeight=FontWeight.Bold, color=FinalEarthLight)
                                        Text("EARNED THIS WEEK", fontSize=10.sp, color=FinalWhite.copy(alpha=0.4f), letterSpacing=0.06.sp)
                                    }
                                    androidx.compose.material3.Button(onClick={}, colors = ButtonDefaults.buttonColors(containerColor = FinalSage), shape = RoundedCornerShape(10.dp)) {
                                        Text("Redeem →", fontSize=12.sp, fontWeight=FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(14.dp))
                                Text("YOUR TITLES", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = FinalMuted, letterSpacing = 0.08.sp)
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                    Box(modifier = Modifier.background(FinalSage, RoundedCornerShape(20.dp)).padding(horizontal=13.dp, vertical=6.dp)) { Text("Neck Newbie", fontSize=11.sp, fontWeight=FontWeight.SemiBold, color=FinalWhite) }
                                    Box(modifier = Modifier.background(FinalSage, RoundedCornerShape(20.dp)).padding(horizontal=13.dp, vertical=6.dp)) { Text("Posture Pro", fontSize=11.sp, fontWeight=FontWeight.SemiBold, color=FinalWhite) }
                                    Box(modifier = Modifier.background(FinalMist, RoundedCornerShape(20.dp)).padding(horizontal=13.dp, vertical=6.dp)) { Text("Spine Savant", fontSize=11.sp, fontWeight=FontWeight.SemiBold, color=Color(0xFFBBBBBB)) }
                                }
                            } else {
                                Text("Feature coming soon...", fontSize = 12.sp, color = FinalMuted, fontStyle = FontStyle.Italic)
                            }
                        }
                    }
                }
            }
        }
    }
}
"""

start_idx = 0
for i, l in enumerate(lines):
    if l.startswith("fun RewardsTab"):
        start_idx = i
        break

# The file goes to 1160+ lines, and RewardsTab is followed by BadgeItem etc.
# We will just replace from RewardsTab to the EOF, since BadgeItem is just helper functions for the old Rewards tab.
final_lines = lines[:start_idx] + [new_ui]

with open(path, 'w', encoding='utf-8') as f:
    f.writelines(final_lines)

print("Updated RewardsTab in MainActivity.kt!")
