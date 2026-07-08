"""
Split sendPostureAlert() into sendReminderNotification() and sendBadPostureNotification()
in NeckGuardService.kt, and update callers.
"""
import sys, io, re
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

path = r'c:\Users\Naiteek\Downloads\postureapp\didi project\NeckGuardApp\app\src\main\java\com\example\neckguard\service\NeckGuardService.kt'
with open(path, 'r', encoding='utf-8-sig') as f:
    txt = f.read()

original_len = len(txt)
print(f"Read {original_len} chars")
patches = []

# ── PATCH A: Update intervalTimerRunnable to call sendReminderNotification() ──
old_interval = '                Log.d(TAG, "Time-based interval nudge firing")\n                sendPostureAlert()\n'
new_interval = '                Log.d(TAG, "Time-based interval nudge firing")\n                sendReminderNotification()\n'
if old_interval in txt:
    txt = txt.replace(old_interval, new_interval, 1)
    patches.append("A: interval timer -> sendReminderNotification()")
else:
    print("PATCH A NOT FOUND")

# ── PATCH B: Update sensor path (line 744) to call sendBadPostureNotification() ──
# This is inside the if(totalUsageThisSession >= ...) block
old_sensor = '                    sendPostureAlert()\n\n                }\n\n            } else if (state == PostureEngine.PostureState.GOOD'
new_sensor = '                    sendBadPostureNotification()\n\n                }\n\n            } else if (state == PostureEngine.PostureState.GOOD'
if old_sensor in txt:
    txt = txt.replace(old_sensor, new_sensor, 1)
    patches.append("B: sensor path -> sendBadPostureNotification()")
else:
    print("PATCH B NOT FOUND")
    # Try finding without double newlines
    idx = txt.find('sendPostureAlert()')
    while idx >= 0:
        context = txt[max(0,idx-100):idx+100]
        if 'getAlertCooldownMs' in context or 'timeSinceLastAlert' in context:
            print(f"  Found sensor sendPostureAlert at idx {idx}")
            print(f"  Context: {repr(context)}")
            break
        idx = txt.find('sendPostureAlert()', idx+1)

# ── PATCH C: Update ALERT_PENDING state to call sendBadPostureNotification() ──
old_pending = '            AppState.ALERT_PENDING -> {\n\n                sendPostureAlert()\n'
new_pending = '            AppState.ALERT_PENDING -> {\n\n                sendBadPostureNotification()\n'
if old_pending in txt:
    txt = txt.replace(old_pending, new_pending, 1)
    patches.append("C: ALERT_PENDING -> sendBadPostureNotification()")
else:
    print("PATCH C NOT FOUND")

# ── PATCH D: Rename sendPostureAlert() to sendBadPostureNotification() ──
# and add the new sendReminderNotification() right before it
old_func_header = '    private fun sendPostureAlert() {\n\n        Log.d(TAG, "FIRING POSTURE ALERT!")\n'

new_functions = '''    // ── Type 1: Reminder Notification ──────────────────────────────────
    // Fires from the periodic timer. Posture is fine — we're just nudging
    // the user to try an exercise and keep their streak alive.
    private fun sendReminderNotification() {
        Log.d(TAG, "FIRING REMINDER NOTIFICATION")

        flushCurrentSessionToDb()

        lastAlertTimeMs = System.currentTimeMillis()
        cumulativeUsageTimerMs = 0L
        screenOnSessionStart = System.currentTimeMillis()
        prefs.edit().putLong("CumulativeUsageMs", 0L).apply()
        intervalTimerHandler.removeCallbacksAndMessages(null)
        scheduleIntervalNudge()

        // Pick next uncompleted exercise for the CTA
        val repository = com.example.neckguard.data.UserRepository(prefs)
        val assignedList = repository.assignedExercisesList
        val completedList = repository.completedExercisesTodayList
        val targetExercise = assignedList.firstOrNull { !completedList.contains(it) }
            ?: assignedList.firstOrNull() ?: "Chin Tuck"

        // Dashboard intent (tap body -> opens app)
        val mainIntent = Intent(this, com.example.neckguard.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = android.app.PendingIntent.getActivity(
            this, 1010, mainIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Exercise intent (CTA button -> opens exercise)
        val exerciseIntent = Intent(this, com.example.neckguard.MainActivity::class.java).apply {
            action = "OPEN_EXERCISE"
            putExtra("exercise_name", targetExercise)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val exercisePendingIntent = android.app.PendingIntent.getActivity(
            this, 1011, exerciseIntent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        // Pick a random message from the reminder pool
        val messages = com.example.neckguard.NotificationPool.reminderMessages
        val lastIdx = prefs.getInt("lastReminderMsgIdx", -1)
        var idx = messages.indices.random()
        while (idx == lastIdx && messages.size > 1) idx = messages.indices.random()
        prefs.edit().putInt("lastReminderMsgIdx", idx).apply()

        val body = messages[idx].replace("{exercise}", targetExercise)

        val reminderTitles = listOf(
            "Time for a stretch \\uD83E\\uDDD8",
            "Exercise check-in \\u2728",
            "Quick stretch break \\uD83D\\uDCAA",
            "Your body will thank you \\uD83D\\uDE4F",
            "Stretch o'clock! \\u23F0",
            "Wellness moment \\uD83C\\uDF3F",
            "Keep the streak alive \\uD83D\\uDD25",
            "Hey champion \\uD83C\\uDFC6"
        )
        val lastTitleIdx = prefs.getInt("lastReminderTitleIdx", -1)
        var titleIdx = reminderTitles.indices.random()
        while (titleIdx == lastTitleIdx && reminderTitles.size > 1) titleIdx = reminderTitles.indices.random()
        prefs.edit().putInt("lastReminderTitleIdx", titleIdx).apply()

        val builder = NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(reminderTitles[titleIdx])
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(mainPendingIntent)
            .addAction(0, "Try $targetExercise \\uD83D\\uDCAA", exercisePendingIntent)
            .setAutoCancel(true)
            .setTimeoutAfter(120_000L)

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(ALERT_NOTIFICATION_ID, builder.build())

        com.example.neckguard.data.UserRepository(prefs).addNudgeFiredToday()
    }

    // ── Type 2: Bad Posture Notification ─────────────────────────────────
    // Fires when sensors detect sustained bad posture. Tap -> camera check.
    private fun sendBadPostureNotification() {

        Log.d(TAG, "FIRING BAD POSTURE NOTIFICATION!")

'''

if old_func_header in txt:
    txt = txt.replace(old_func_header, new_functions, 1)
    patches.append("D: split sendPostureAlert into two functions")
else:
    print("PATCH D NOT FOUND")
    idx = txt.find('private fun sendPostureAlert()')
    print(f"  sendPostureAlert at idx: {idx}")
    if idx >= 0:
        print(f"  Context: {repr(txt[idx:idx+200])}")

print(f"\nPatches applied: {patches}")
print(f"File grew from {original_len} to {len(txt)} chars")

with open(path, 'w', encoding='utf-8-sig') as f:
    f.write(txt)
print("File written successfully.")
