"""
Patch to add a weekly bar chart to the Progress tab.
"""
import sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

vm_path = r'c:\Users\Naiteek\Downloads\postureapp\didi project\NeckGuardApp\app\src\main\java\com\example\neckguard\ui\MainViewModel.kt'
with open(vm_path, 'r', encoding='utf-8-sig') as f:
    vm = f.read()

# Add DailyPostureStat data class
old_ds = 'data class RewardsState('
new_ds = 'data class DailyPostureStat(val dayLabel: String, val totalMs: Long, val slouchedMs: Long, val hasData: Boolean)\n\ndata class RewardsState('
if old_ds in vm:
    vm = vm.replace(old_ds, new_ds, 1)

# Add weekLogStats to RewardsState
old_rs = '    val weekLog: List<Pair<String, String>> = listOf("S" to "", "M" to "", "T" to "", "W" to "", "T" to "", "F" to "", "S" to "")\n)'
new_rs = '    val weekLog: List<Pair<String, String>> = listOf("S" to "", "M" to "", "T" to "", "W" to "", "T" to "", "F" to "", "S" to ""),\n    val weekLogStats: List<DailyPostureStat> = emptyList()\n)'
if old_rs in vm:
    vm = vm.replace(old_rs, new_rs, 1)

# Update the launch block to populate weekLogStats
old_launch = '''        // ── Week Log ────────────────────────────────────────────
        launch {
            postureLogDao.getAllLogs().asFlow().collect { logs ->
                val weekLog = buildWeekLog(logs)

                rewardsState.value = rewardsState.value.copy(
                    weekLog = weekLog
                )
            }
        }'''
new_launch = '''        // ── Week Log ────────────────────────────────────────────
        launch {
            postureLogDao.getAllLogs().asFlow().collect { logs ->
                val weekLog = buildWeekLog(logs)
                val weekLogStats = buildWeekLogStats(logs)

                rewardsState.value = rewardsState.value.copy(
                    weekLog = weekLog,
                    weekLogStats = weekLogStats
                )
            }
        }'''
if old_launch in vm:
    vm = vm.replace(old_launch, new_launch, 1)

# Add buildWeekLogStats function
old_build = '    private fun buildWeekLog(logs: List<com.example.neckguard.data.local.PostureLog>): List<Pair<String, String>> {'
new_build = '''    private fun buildWeekLogStats(logs: List<com.example.neckguard.data.local.PostureLog>): List<DailyPostureStat> {
        val dayLabels = listOf("S", "M", "T", "W", "T", "F", "S")
        val out = mutableListOf<DailyPostureStat>()
        for (daysAgo in 6 downTo 0) {
            val (start, end) = dayBoundsMillis(-daysAgo)
            val cal = java.util.Calendar.getInstance().apply { add(java.util.Calendar.DAY_OF_YEAR, -daysAgo) }
            val label = dayLabels[cal.get(java.util.Calendar.DAY_OF_WEEK) - 1]
            
            val dayLogs = logs.filter { it.timestampStartMs in start..end }
            val totalMs = dayLogs.sumOf { it.durationMs }
            val slouchedMs = dayLogs.sumOf { it.slouchedMs }
            
            out.add(DailyPostureStat(label, totalMs, slouchedMs, dayLogs.isNotEmpty()))
        }
        return out
    }

    private fun buildWeekLog(logs: List<com.example.neckguard.data.local.PostureLog>): List<Pair<String, String>> {'''
if old_build in vm:
    vm = vm.replace(old_build, new_build, 1)

with open(vm_path, 'w', encoding='utf-8-sig') as f:
    f.write(vm)

print("ViewModel patched.")


ma_path = r'c:\Users\Naiteek\Downloads\postureapp\didi project\NeckGuardApp\app\src\main\java\com\example\neckguard\MainActivity.kt'
with open(ma_path, 'r', encoding='utf-8-sig') as f:
    ma = f.read()

# Replace the "This week" row in RewardsTab with the bar chart
old_ui = '''                            if (key == "week") {
                                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                    state.weekLog.forEach { item2 -> val day = item2.first; val stat = item2.second; Column(horizontalAlignment = Alignment.CenterHorizontally) { Box(modifier = Modifier.size(32.dp).border(2.5.dp, if(stat=="✓" || stat=="F") FinalSage else if (stat=="!") FinalEarth else Color(0xFFE0E0D8), CircleShape).background(if(stat=="✓") FinalSagePale else if(stat=="F") FinalSage else if(stat=="!") FinalEarthPale else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) { Text(if(stat=="✓") "✓" else if(stat=="!") "!" else if(stat=="F") day else day, fontSize = 12.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = if(stat=="✓"||stat=="F") FinalMoss else if(stat=="!") FinalEarth else Color.LightGray) }; Spacer(modifier = Modifier.height(3.dp)); Text(day, fontSize = 12.sp, color=FinalMuted) } }
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Column(modifier = Modifier.weight(1f).background(FinalMist, RoundedCornerShape(12.dp)).padding(12.dp)) { Text(String.format("%.1f", state.timeTrackedHours) + "h", fontSize=22.sp, fontWeight=androidx.compose.ui.text.font.FontWeight.Bold, color=FinalBark); Text("Time tracked", fontSize = 13.sp, color=FinalMuted) }; Column(modifier = Modifier.weight(1f).background(FinalMist, RoundedCornerShape(12.dp)).padding(12.dp)) { Text("${state.exercisesDoneTotal}/${state.totalRequiredExercises}", fontSize=22.sp, fontWeight=androidx.compose.ui.text.font.FontWeight.Bold, color=FinalBark); Text("Exercises done", fontSize = 13.sp, color=FinalMuted) } }
                            } else if (key == "rewards") {'''

new_ui = '''                            if (key == "week") {
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
                            } else if (key == "rewards") {'''

if old_ui in ma:
    ma = ma.replace(old_ui, new_ui, 1)
else:
    print("UI NOT FOUND")

with open(ma_path, 'w', encoding='utf-8-sig') as f:
    f.write(ma)

print("MainActivity patched.")
