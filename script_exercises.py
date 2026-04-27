import sys

path = r'c:\Users\Naiteek\Downloads\postureapp\didi project\NeckGuardApp\app\src\main\java\com\example\neckguard\ui\ExercisesScreen.kt'
with open(path, 'r', encoding='utf-8') as f:
    lines = f.readlines()

new_ui = """
// ── Shape tokens (local) ─────────────────────────────────────────
private val RCard = 20.dp
private val RPill = 40.dp

val FinalBark = Color(0xFF3D2E1E)
val FinalBarkSoft = Color(0xFF5C4A35)
val FinalSage = Color(0xFF7A9E7E)
val FinalSageLight = Color(0xFFB5CEB8)
val FinalSagePale = Color(0xFFE8F0E9)
val FinalMoss = Color(0xFF4A6741)
val FinalMist = Color(0xFFEEF2EE)
val FinalMuted = Color(0xFF8A8A7A)
val FinalWhite = Color(0xFFFFFFFF)
val FinalCream = Color(0xFFFAF8F3)
val FinalEarthPale = Color(0xFFF7F0E6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisesScreen() {
    var selectedExercise by remember { mutableStateOf<Exercise?>(null) }
    val groupedExercises = ExerciseData.exercises.groupBy { it.category }
    val categories = groupedExercises.keys.toList()
    var activeCategory by remember { mutableStateOf(categories.firstOrNull() ?: "") }
    var expandedExerciseId by remember { mutableStateOf<String?>(null) }
    
    // Fake state for demo
    var doneIds by remember { mutableStateOf(setOf<String>()) }

    Column(modifier = Modifier.fillMaxSize().background(FinalCream)) {
        // HDR
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Brush.linearGradient(colors = listOf(FinalBark, FinalBarkSoft)))
                .padding(top = 52.dp, start = 22.dp, end = 22.dp, bottom = 20.dp)
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
                Spacer(modifier = Modifier.height(16.dp))
                Text("Your exercises", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = FinalWhite)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Evidence-based • assigned to your posture profile", fontSize = 12.sp, color = FinalWhite.copy(alpha=0.45f))
                Spacer(modifier = Modifier.height(14.dp))
                
                // Tabs
                androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    items(categories.size) { i ->
                        val cat = categories[i]
                        val isActive = cat == activeCategory
                        Box(
                            modifier = Modifier
                                .border(1.5.dp, if(isActive) FinalSage else FinalWhite.copy(alpha=0.2f), RoundedCornerShape(20.dp))
                                .background(if(isActive) FinalSage else Color.Transparent, RoundedCornerShape(20.dp))
                                .clickable { activeCategory = cat; expandedExerciseId = null }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(cat, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if(isActive) FinalWhite else FinalWhite.copy(alpha=0.5f))
                        }
                    }
                }
            }
        }
        
        // List
        val list = groupedExercises[activeCategory] ?: emptyList()
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start=16.dp, end=16.dp, top=16.dp, bottom=100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(list.size) { i ->
                val ex = list[i]
                val isExpanded = expandedExerciseId == ex.title
                val isDone = doneIds.contains(ex.title)
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FinalWhite, RoundedCornerShape(16.dp))
                        .border(1.dp, FinalMist, RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expandedExerciseId = if(isExpanded) null else ex.title }
                            .padding(14.dp, 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(34.dp).background(FinalSageLight.copy(alpha=0.4f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                            Text("${i+1}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FinalBark)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(ex.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FinalBark)
                                if (isDone) {
                                    Spacer(modifier = Modifier.width(7.dp))
                                    Box(modifier = Modifier.background(FinalSage, RoundedCornerShape(20.dp)).padding(horizontal=7.dp, vertical=2.dp)) {
                                        Text("Done ✓", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = FinalWhite)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(ex.reps, fontSize = 11.sp, color = FinalMuted)
                        }
                        Text(if(isExpanded) "▲" else "▼", fontSize = 10.sp, color = FinalMuted)
                    }
                    
                    if (isExpanded) {
                        Column(modifier = Modifier.padding(start=16.dp, end=16.dp, bottom=14.dp)) {
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(FinalMist))
                            Spacer(modifier = Modifier.height(14.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(FinalEarthPale, RoundedCornerShape(10.dp)).padding(8.dp, 12.dp)) {
                                Text("🔁", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(ex.reps, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FinalBarkSoft)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Steps simulation
                            val steps = ex.description.split(". ").filter { it.isNotBlank() }
                            steps.forEachIndexed { si, stepText ->
                                Row(modifier = Modifier.padding(bottom = 9.dp)) {
                                    Box(modifier = Modifier.size(20.dp).background(FinalSagePale, CircleShape), contentAlignment = Alignment.Center) {
                                        Text("${si+1}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = FinalMoss)
                                    }
                                    Spacer(modifier = Modifier.width(9.dp))
                                    Text(stepText + (if(!stepText.endsWith(".")) "." else ""), fontSize = 12.sp, color = FinalBarkSoft, lineHeight = 18.sp)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            if (isDone) {
                                androidx.compose.material3.Button(
                                    onClick = { },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = FinalSagePale),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("✓ Completed", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinalMoss)
                                }
                            } else {
                                androidx.compose.material3.Button(
                                    onClick = { selectedExercise = ex }, // Opens Bottom Sheet
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = FinalBark),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Start Exercise", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinalCream)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedExercise != null) {
        ModalBottomSheet(
            onDismissRequest = { 
                doneIds = doneIds + selectedExercise!!.title // Mark done when dismissed for demo
                selectedExercise = null 
            },
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = FinalWhite
        ) {
            ExerciseDetailContent(
                exercise = selectedExercise!!,
                onClose = { 
                    doneIds = doneIds + selectedExercise!!.title
                    selectedExercise = null 
                }
            )
        }
    }
}
"""

insert_idx = 0
for i, l in enumerate(lines):
    if l.startswith('// ── Shape tokens'):
        insert_idx = i
        break
    if l.strip() == '@OptIn(ExperimentalMaterial3Api::class)':
        insert_idx = i
        break

final_lines = lines[:insert_idx] + [new_ui + '\n']

add = False
for l in lines:
    if l.startswith('@Composable') and 'fun ExerciseDetailContent' in l:
        add = True
    if add:
        final_lines.append(l)

with open(path, 'w', encoding='utf-8') as f:
    f.writelines(final_lines)

print("Updated ExercisesScreen.kt!")
