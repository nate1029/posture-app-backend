import sys

main_path = r'c:\Users\Naiteek\Downloads\postureapp\didi project\NeckGuardApp\app\src\main\java\com\example\neckguard\MainActivity.kt'
with open(main_path, 'r', encoding='utf-8') as f:
    text_main = f.read()

# Update RewardsTab signature
text_main = text_main.replace('fun RewardsTab() {', 'fun RewardsTab(viewModel: com.example.neckguard.ui.MainViewModel) {\n    val state by viewModel.rewardsState.collectAsState()\n    var expandedSection = state.expandedSection')
text_main = text_main.replace('var expandedSection by remember { mutableStateOf("week") }', '')
# Bind expandedSection assignment to ViewModel
text_main = text_main.replace('expandedSection = if(isExpanded) "" else key', 'viewModel.setRewardsSection(key)')

# Use state fields for week rings. The original is:
# listOf("M" to "✓", "T" to "✓", "W" to "✓", "T" to "!", "F" to "F", "S" to "", "S" to "")
text_main = text_main.replace('listOf("M" to "✓", "T" to "✓", "W" to "✓", "T" to "!", "F" to "F", "S" to "", "S" to "")', 'state.weekLog')
# Stats grid
text_main = text_main.replace('Text("11.4h",', 'Text(String.format("%.1f", state.timeTrackedHours) + "h",')
text_main = text_main.replace('Text("9/14",', 'Text("${state.exercisesDoneTotal}/${state.totalRequiredExercises}",')
text_main = text_main.replace('Text("420 pts",', 'Text("${state.points} pts",')

with open(main_path, 'w', encoding='utf-8') as f:
    f.write(text_main)

ex_path = r'c:\Users\Naiteek\Downloads\postureapp\didi project\NeckGuardApp\app\src\main\java\com\example\neckguard\ui\ExercisesScreen.kt'
with open(ex_path, 'r', encoding='utf-8') as f:
    text_ex = f.read()

text_ex = text_ex.replace('fun ExercisesScreen() {', 'fun ExercisesScreen(viewModel: MainViewModel) {\n    val state by viewModel.exercisesState.collectAsState()')
text_ex = text_ex.replace('var activeCategory by remember { mutableStateOf(categories.firstOrNull() ?: "") }', 'val activeCategory = state.activeCategory')
text_ex = text_ex.replace('var expandedExerciseId by remember { mutableStateOf<String?>(null) }', 'val expandedExerciseId = state.expandedExerciseId')
text_ex = text_ex.replace('var doneIds by remember { mutableStateOf(setOf<String>()) }', 'val doneIds = state.doneIds')

# Bind click handlers
text_ex = text_ex.replace('activeCategory = cat; expandedExerciseId = null', 'viewModel.setExercisesCategory(cat)')
text_ex = text_ex.replace('expandedExerciseId = if(isExpanded) null else ex.title', 'viewModel.toggleExercise(ex.title)')

# On bottom sheet dismiss
text_ex = text_ex.replace('doneIds = doneIds + selectedExercise!!.title // Mark done when dismissed for demo', 'viewModel.markExerciseDone(selectedExercise!!.title)')
text_ex = text_ex.replace('doneIds = doneIds + selectedExercise!!.title', 'viewModel.markExerciseDone(selectedExercise!!.title)')

with open(ex_path, 'w', encoding='utf-8') as f:
    f.write(text_ex)

print("Linked states in Rewards and Exercises!")
