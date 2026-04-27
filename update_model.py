import sys

main_path = r'c:\Users\Naiteek\Downloads\postureapp\didi project\NeckGuardApp\app\src\main\java\com\example\neckguard\ui\MainViewModel.kt'
with open(main_path, 'r', encoding='utf-8') as f:
    text = f.read()

state_decl_old = """data class DashboardState(
    val isAppActive: Boolean = false,
    val postureScore: Int = 82,
    val scoreDelta: Int = 6,
    val streakDays: Int = 3,
    val nextNudgeMins: Int = 12,
    val completedExercisesCount: Int = 2,
    val activeExercisesCount: Int = 5,
    val highScreenDistancePct: Int = 24
)"""
    
state_decl_new = """data class DashboardState(
    val isAppActive: Boolean = false,
    val postureScore: Int = 82,
    val scoreDelta: Int = 6,
    val streakDays: Int = 3,
    val nextNudgeMins: Int = 12,
    val completedExercisesCount: Int = 2,
    val activeExercisesCount: Int = 5,
    val highScreenDistancePct: Int = 24,
    val todayNudge: NudgeData = NudgeData("Raise your phone to eye level right now", listOf("🟢 Easy", "⏱ 5 seconds", "No eqpt"), "Every 10° of forward head tilt adds ~10 lbs of load on your cervical spine."),
    val detectedToday: DetectionData = DetectionData("High screen distance detected", "Your phone closer than 30cm for 24%.", "Eye strain • Moderate severity")
)

data class NudgeData(val instruction: String, val tags: List<String>, val reasoning: String)
data class DetectionData(val title: String, val subtitle: String, val severityTag: String)
"""
text = text.replace(state_decl_old, state_decl_new)

# Generate a list of 7 nudges and attach it to the gamification logic in MainViewModel!
# We can dynamically set the nudge based on the day or just randomly.
# Let's add the static lists inside the ViewModel init or inside the loop.
gamification_old = """                _dashboardState.value = _dashboardState.value.copy(
                    postureScore = newScore,
                    scoreDelta = delta,
                    nextNudgeMins = newNudge,
                    highScreenDistancePct = newDist
                )"""

gamification_new = """                
                val nudges = listOf(
                    NudgeData("Raise your phone to eye level right now", listOf("🟢 Easy", "⏱ 5 sec", "No eqpt"), "Every 10° of forward head tilt adds ~10 lbs of load on your cervical spine."),
                    NudgeData("Tuck your chin slightly inwards for 10s", listOf("🔵 Moderate", "⏱ 10 sec", "Cervical"), "Chin tucks strengthen deep neck flexors and combat text neck."),
                    NudgeData("Roll your shoulders back and down 5 times", listOf("🟢 Easy", "⏱ 15 sec", "Mobility"), "Relieves built-up tension in the upper trapezius muscles."),
                    NudgeData("Look 20 feet away for 20 seconds", listOf("👁️ Eyes", "⏱ 20 sec", "Rest"), "The 20-20-20 rule prevents digital eye strain and dry eyes."),
                    NudgeData("Stretch your neck gently side-to-side", listOf("🔵 Moderate", "⏱ 20 sec", "Stretch"), "Improves lateral cervical mobility and reduces stiffness."),
                    NudgeData("Squeeze your shoulder blades together", listOf("💪 Strength", "⏱ 5 sec", "Posture"), "Activating rhomboids prevents the 'rounded shoulders' posture."),
                    NudgeData("Stand up and shake out your arms", listOf("🏃 Movement", "⏱ 10 sec", "Bloodflow"), "Breaking static posture resets muscle tension and improves circulation.")
                )
                val detections = listOf(
                    DetectionData("High screen distance detected", "Your phone closer than 30cm for 24%.", "Eye strain • Moderate severity"),
                    DetectionData("Asymmetrical leaning detected", "Left-side leaning for 45 mins straight.", "Spinal imbalance • Low severity"),
                    DetectionData("Prolonged text neck", "Looking straight down for over 22 mins.", "Neck strain • High severity"),
                    DetectionData("Optimal alignment maintained", "Maintained perfect spine for 2+ hours.", "Excellent • No action needed")
                )
                
                _dashboardState.value = _dashboardState.value.copy(
                    postureScore = newScore,
                    scoreDelta = delta,
                    nextNudgeMins = newNudge,
                    highScreenDistancePct = newDist,
                    todayNudge = nudges.random(),
                    detectedToday = detections.random()
                )"""
text = text.replace(gamification_old, gamification_new)

with open(main_path, 'w', encoding='utf-8') as f:
    f.write(text)
print('Updated MainViewModel.')
