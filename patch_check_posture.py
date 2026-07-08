"""
Modify CheckPostureActivity.kt:
1. Change fireResultNotification to accept pitch data
2. Change CTA from exercise to "Learn more" -> PostureInsightActivity
"""
import sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

path = r'c:\Users\Naiteek\Downloads\postureapp\didi project\NeckGuardApp\app\src\main\java\com\example\neckguard\CheckPostureActivity.kt'
with open(path, 'r', encoding='utf-8-sig') as f:
    txt = f.read()

patches = []

# ── PATCH 1: Change the call site to pass pitch ──
# Change fireResultNotification(message) to fireResultNotification(message, trueNeckPitch)
old_call = '                fireResultNotification(message)\n                finish()'
new_call = '                fireResultNotification(message, trueNeckPitch)\n                finish()'
if old_call in txt:
    txt = txt.replace(old_call, new_call, 1)
    patches.append("1: pass pitch to fireResultNotification")
else:
    print("PATCH 1 NOT FOUND")

# ── PATCH 2: Replace the entire fireResultNotification function ──
old_func = '''    private fun fireResultNotification(text: String) {
        val manager = getSystemService(NotificationManager::class.java)

        val style = NotificationCompat.BigTextStyle().bigText(text)

        val prefs = SecurePrefs.get(this)
        val repository = com.example.neckguard.data.UserRepository(prefs)
        val assignedList = repository.assignedExercisesList
        val completedList = repository.completedExercisesTodayList
        val targetExercise = assignedList.firstOrNull { !completedList.contains(it) } ?: assignedList.firstOrNull() ?: "Chin Tuck"

        val mainIntent = android.content.Intent(this, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = android.app.PendingIntent.getActivity(
            this, 1002, mainIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val exerciseIntent = android.content.Intent(this, MainActivity::class.java).apply {
            action = "OPEN_EXERCISE"
            putExtra("exercise_name", targetExercise)
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, 1001, exerciseIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, "neckguard_alert_channel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Posture Result")
            .setContentText(text)
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(mainPendingIntent)
            .setTimeoutAfter(RESULT_NOTIFICATION_TIMEOUT_MS)
            .addAction(0, "Do $targetExercise", pendingIntent)

        manager.notify(ALERT_NOTIFICATION_ID, builder.build())
    }'''

new_func = '''    private fun fireResultNotification(text: String, neckPitch: Float = 0f) {
        val manager = getSystemService(NotificationManager::class.java)

        val style = NotificationCompat.BigTextStyle().bigText(text)

        val isBadPosture = neckPitch > 15f

        // Spinal load estimate (Hansraj 2014)
        val spinalLoadKg = when {
            neckPitch < 15f -> 12
            neckPitch < 25f -> 18
            neckPitch < 35f -> 22
            neckPitch < 45f -> 27
            else            -> 32
        }
        val postureState = when {
            neckPitch > 35f -> "POOR"
            neckPitch > 15f -> "MODERATE"
            else            -> "GOOD"
        }

        // Main tap -> opens dashboard
        val mainIntent = android.content.Intent(this, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = android.app.PendingIntent.getActivity(
            this, 1002, mainIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = NotificationCompat.Builder(this, "neckguard_alert_channel")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Posture Result")
            .setContentText(text)
            .setStyle(style)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(mainPendingIntent)
            .setTimeoutAfter(RESULT_NOTIFICATION_TIMEOUT_MS)

        if (isBadPosture) {
            // Bad posture -> "Why this happened?" -> PostureInsightActivity
            val insightIntent = android.content.Intent(this, PostureInsightActivity::class.java).apply {
                putExtra("neck_pitch", neckPitch)
                putExtra("spinal_load_kg", spinalLoadKg)
                putExtra("posture_state", postureState)
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val insightPendingIntent = android.app.PendingIntent.getActivity(
                this, 1003, insightIntent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(0, "Why this happened? \\uD83D\\uDD0D", insightPendingIntent)
        } else {
            // Good posture -> exercise CTA (keep existing behavior)
            val prefs = SecurePrefs.get(this)
            val repository = com.example.neckguard.data.UserRepository(prefs)
            val assignedList = repository.assignedExercisesList
            val completedList = repository.completedExercisesTodayList
            val targetExercise = assignedList.firstOrNull { !completedList.contains(it) }
                ?: assignedList.firstOrNull() ?: "Chin Tuck"

            val exerciseIntent = android.content.Intent(this, MainActivity::class.java).apply {
                action = "OPEN_EXERCISE"
                putExtra("exercise_name", targetExercise)
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val exercisePendingIntent = android.app.PendingIntent.getActivity(
                this, 1001, exerciseIntent,
                android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(0, "Try $targetExercise \\uD83D\\uDCAA", exercisePendingIntent)
        }

        manager.notify(ALERT_NOTIFICATION_ID, builder.build())
    }'''

if old_func in txt:
    txt = txt.replace(old_func, new_func, 1)
    patches.append("2: replaced fireResultNotification with insight CTA logic")
else:
    print("PATCH 2 NOT FOUND")
    idx = txt.find('private fun fireResultNotification')
    print(f"  Function at idx: {idx}")
    if idx >= 0:
        print(f"  Context: {repr(txt[idx:idx+200])}")

print(f"Patches applied: {patches}")

with open(path, 'w', encoding='utf-8-sig') as f:
    f.write(txt)
print(f"File written: {len(txt)} chars")
