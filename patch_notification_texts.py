"""
Patch NeckGuardService.kt and CheckPostureActivity.kt to swap the cheesy and scientific texts
for the pre- and post-camera notifications.
"""
import sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

# --- 1. NeckGuardService.kt ---
ngs_path = r'c:\Users\Naiteek\Downloads\postureapp\didi project\NeckGuardApp\app\src\main\java\com\example\neckguard\service\NeckGuardService.kt'
with open(ngs_path, 'r', encoding='utf-8-sig') as f:
    ngs = f.read()

old_ngs_block = '''        val spinalLoadKg = when {
            flexion < 15f -> 12
            flexion < 25f -> 18
            flexion < 35f -> 22
            flexion < 45f -> 27
            else          -> 32
        }

        // One-liner shown in collapsed notification -- specific sensor diagnosis
        val diagnosisLine = when (postureState) {
            com.example.neckguard.engine.PostureEngine.PostureState.POOR ->
                "~${flexion.toInt()}\u00b0 neck tilt \u2014 ~${spinalLoadKg}kg on your spine \uD83D\uDEA8 Tap to check"
            com.example.neckguard.engine.PostureEngine.PostureState.MODERATE ->
                "Mild forward tilt (~${flexion.toInt()}\u00b0) detected. Tap for a quick scan"
            else ->
                "Your ${intervalMins}-min check-in is here \uD83D\uDC40 Tap to verify posture"
        }

        val nudgeTitles = listOf(
            "Quick check-in \uD83D\uDC4B",
            "Posture moment \uD83E\uDDD8",
            "Hey, how's your neck? \uD83E\uDD14",
            "Time for a posture check \u2728",
            "Slouch alert? Let's find out \uD83D\uDD0D",
            "Your spine called \uD83D\uDCDE",
            "Neck check! \uD83D\uDCAA",
            "Let's do a quick scan \uD83D\uDCF8"
        )

        val expandedBodies = when (postureState) {
            com.example.neckguard.engine.PostureEngine.PostureState.POOR -> listOf(
                "Sensors picked up a ~${flexion.toInt()}\u00b0 neck tilt \uD83D\uDC80 That's ${spinalLoadKg}kg on your cervical spine \u2014 more than a toddler on your head. The Slouch Monster is winning. Tap for a 2-sec camera scan! \uD83D\uDCAA",
                "Bestie, your neck is at ~${flexion.toInt()}\u00b0 forward flex. ${spinalLoadKg}kg pressing (Hansraj 2014). Giving goblin energy. Tap to check yourself before you wreck yourself \uD83D\uDC40",
                "ALERT: ~${flexion.toInt()}\u00b0 neck flexion = ~${spinalLoadKg}kg spinal load. Not a vibe. Let the camera verify \u2014 2 seconds is all we need \uD83D\uDCF8"
            )
            com.example.neckguard.engine.PostureEngine.PostureState.MODERATE -> listOf(
                "Mild forward tilt (~${flexion.toInt()}\u00b0) detected. Your neck is working harder than it should (~${spinalLoadKg}kg). Fix now = no stiff neck tonight. Tap to verify \uD83C\uDF3F",
                "Slightly leaning in \u2014 ~${flexion.toInt()}\u00b0 puts about ${spinalLoadKg}kg on your spine. Let's fix it before it becomes a problem. Quick tap? \u2728",
                "Mild slouch spotted (~${flexion.toInt()}\u00b0). Your neck noticed. 2-second camera scan? \uD83D\uDD25"
            )
            else -> listOf(
                "It's been ${intervalMins} mins! Posture might've drifted. Quick camera check \u2014 literally 2 seconds \uD83D\uDCF8",
                "Periodic check-in! Sensors show neutral posture \uD83D\uDC4D but let's confirm with the camera \uD83D\uDC7E",
                "Your ${intervalMins}-min wellness check-in. Quick scan keeps your streak alive and your neck happy \uD83D\uDD25"
            )
        }'''

new_ngs_block = '''        // Gen-Z cheesy notification (NO scientific data directly here!)
        val diagnosisLine = "Hey, maybe you're in a bad posture \uD83D\uDC40 let's check"
        
        val nudgeTitles = listOf(
            "Your posture is calling \uD83D\uDCDE",
            "Goblin mode detected? \uD83D\uDC40",
            "Hey, how's your neck? \uD83E\uDD14",
            "Slouch alert? Let's find out \uD83D\uDD0D",
            "Neck check bestie \u2728"
        )

        val expandedBodies = listOf(
            "Your posture is calling... all you need to do is click this!! \uD83D\uDD25",
            "Are we slouching? The sensors say maybe. Let's do a 2-second scan to be sure \uD83D\uDCF8",
            "Bestie, it's posture check time. Tap to see if you're giving shrimp posture \uD83E\uDD90"
        )'''

if old_ngs_block in ngs:
    ngs = ngs.replace(old_ngs_block, new_ngs_block, 1)
    print("Patched NeckGuardService.kt successfully")
else:
    print("Could not find the target block in NeckGuardService.kt")

with open(ngs_path, 'w', encoding='utf-8-sig') as f:
    f.write(ngs)


# --- 2. CheckPostureActivity.kt ---
cpa_path = r'c:\Users\Naiteek\Downloads\postureapp\didi project\NeckGuardApp\app\src\main\java\com\example\neckguard\CheckPostureActivity.kt'
with open(cpa_path, 'r', encoding='utf-8-sig') as f:
    cpa = f.read()

old_cpa_block = '''    private fun fireResultNotification(text: String, neckPitch: Float = 0f) {
        val manager = getSystemService(NotificationManager::class.java)

        val style = NotificationCompat.BigTextStyle().bigText(text)

        val isBadPosture = neckPitch > 15f'''

new_cpa_block = '''    private fun fireResultNotification(text: String, neckPitch: Float = 0f) {
        val manager = getSystemService(NotificationManager::class.java)
        
        val isBadPosture = neckPitch > 15f
        
        // Spinal load estimate (Hansraj 2014)
        val spinalLoadKg = when {
            neckPitch < 15f -> 12
            neckPitch < 25f -> 18
            neckPitch < 35f -> 22
            neckPitch < 45f -> 27
            else            -> 32
        }

        // Incorporate scientific data directly in the result notification
        val scientificText = if (isBadPosture) {
            "$text\\n\\nSensors picked up a ~${neckPitch.toInt()}° neck tilt. That's ~${spinalLoadKg}kg on your cervical spine!"
        } else {
            text
        }

        val style = NotificationCompat.BigTextStyle().bigText(scientificText)'''

if old_cpa_block in cpa:
    cpa = cpa.replace(old_cpa_block, new_cpa_block, 1)
    print("Patched CheckPostureActivity.kt successfully")
else:
    print("Could not find the target block in CheckPostureActivity.kt")

# Make sure we don't redefine spinalLoadKg below in the same method
old_spinal_load = '''        // Spinal load estimate (Hansraj 2014)
        val spinalLoadKg = when {
            neckPitch < 15f -> 12
            neckPitch < 25f -> 18
            neckPitch < 35f -> 22
            neckPitch < 45f -> 27
            else            -> 32
        }
        val postureState = when {'''

new_spinal_load = '''        val postureState = when {'''

if old_spinal_load in cpa:
    cpa = cpa.replace(old_spinal_load, new_spinal_load, 1)
    print("Removed duplicate spinal load block from CheckPostureActivity.kt")
else:
    print("Could not find duplicate spinal load block in CheckPostureActivity.kt")

# Update builder ContentText to use scientificText
old_builder_text = '''            .setContentTitle("Posture Result")
            .setContentText(text)
            .setStyle(style)'''
new_builder_text = '''            .setContentTitle("Posture Result")
            .setContentText(scientificText)
            .setStyle(style)'''

if old_builder_text in cpa:
    cpa = cpa.replace(old_builder_text, new_builder_text, 1)
    print("Updated builder text in CheckPostureActivity.kt")
else:
    print("Could not find builder text block in CheckPostureActivity.kt")


with open(cpa_path, 'w', encoding='utf-8-sig') as f:
    f.write(cpa)
