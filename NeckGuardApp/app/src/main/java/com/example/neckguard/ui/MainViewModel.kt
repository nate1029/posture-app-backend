package com.example.neckguard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.neckguard.data.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.isActive
import java.util.Calendar
import com.example.neckguard.data.local.PostureLogDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.asFlow
import com.example.neckguard.RemoteConfigManager
import com.google.firebase.crashlytics.FirebaseCrashlytics

sealed class AppState {
    object Loading : AppState()
    object Unauthenticated : AppState()
    object NeedsOnboarding : AppState()
    object Ready : AppState()

    /**
     * Authenticated successfully, but the post-login `fetchProfile` call
     * failed transiently (network down, 5xx). We surface this as a
     * dedicated state instead of routing to [NeedsOnboarding] (which
     * would silently overwrite the user's real Supabase profile when they
     * re-onboarded — see bug B-05).
     *
     * The UI for this state shows a minimal "couldn't reach the server"
     * screen with a Retry button that re-runs [MainViewModel.checkStatus].
     */
    object ProfileFetchFailed : AppState()
}

/** 
 * UI State Models exposing the basic backend gamification / posture algorithm.
 */
data class DashboardState(
    val postureScore: Int = 0,
    val scoreDelta: Int = 0,
    val streakDays: Int = 0,
    val nextNudgeMins: Int = 18,
    val isAppActive: Boolean = false,
    val activeExercisesCount: Int = 3,
    val completedExercisesCount: Int = 0,
    val highScreenDistancePct: Int = 68,
    val assignedExercises: List<String> = emptyList(),
    val todayNudge: NudgeData = NudgeData("Raise your phone to eye level right now", listOf("🟢 Easy", "⏱ 5 seconds", "No eqpt"), "Every 10° of forward head tilt adds ~10 lbs of load on your cervical spine."),
    val detectedToday: DetectionData = DetectionData("High screen distance detected", "Your phone closer than 30cm for 24%.", "Eye strain • Moderate severity")
)

data class NudgeData(val instruction: String, val tags: List<String>, val reasoning: String)
data class DetectionData(val title: String, val subtitle: String, val severityTag: String)

data class RewardsState(
    val expandedSection: String = "week",
    val timeTrackedHours: Float = 0f,
    val exercisesDoneTotal: Int = 0,
    val totalRequiredExercises: Int = 21,
    val points: Int = 0,
    val weekLog: List<Pair<String, String>> = listOf("S" to "", "M" to "", "T" to "", "W" to "", "T" to "", "F" to "", "S" to "")
)

data class ExercisesState(
    val activeCategory: String = "Cervical Movements",
    val expandedExerciseId: String? = null,
    val doneIds: Set<String> = emptySet()
)

class MainViewModel(
    private val repository: UserRepository,
    private val postureLogDao: PostureLogDao
) : ViewModel() {
    private val _appState = MutableStateFlow<AppState>(AppState.Loading)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    // Screen States
    val dashboardState = MutableStateFlow(DashboardState())
    val rewardsState = MutableStateFlow(RewardsState())
    val exercisesState = MutableStateFlow(ExercisesState())

    init {
        checkStatus()
        startGamificationAndPostureEngine()
    }

    /**
     * Day boundary as epoch-millis. Pushed to a new value every time we
     * detect a calendar-day change (either at startup or via the periodic
     * checker below). The today's-stats flow uses [flatMapLatest] on this
     * trigger so it transparently re-subscribes to the DAO query with the
     * fresh `startOfToday`, instead of forever aggregating against the
     * boundary captured at app launch. (B-06)
     */
    private val todayBoundary: MutableStateFlow<Long> = MutableStateFlow(dayBoundsMillis(0).first)

    /**
     * Replaces previous simulation loop with real backend logic driven by Room DB
     * and SharedPreferences.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private fun startGamificationAndPostureEngine() = viewModelScope.launch(Dispatchers.IO) {
        // Run rollover-if-needed on launch, then once a minute thereafter
        // so a long-running app session that crosses midnight picks it up.
        // 60 s cadence is chosen because Android can't reliably scheduling
        // anything finer in Doze; we don't need accuracy beyond a few
        // minutes of wall-clock anyway. (B-06)
        runDayRolloverIfNeeded()
        refreshLocalGamificationState()

        launch {
            while (isActive) {
                delay(60_000L)
                if (runDayRolloverIfNeeded()) {
                    refreshLocalGamificationState()
                    todayBoundary.value = dayBoundsMillis(0).first
                }
            }
        }

        // ── Streak & Week Log ────────────────────────────────────────────
        launch {
            postureLogDao.getAllLogs().asFlow().collect { logs ->
                val weekLog = buildWeekLog(logs)
                val streak = computeStreak(logs)

                dashboardState.value = dashboardState.value.copy(
                    streakDays = streak
                )
                rewardsState.value = rewardsState.value.copy(
                    weekLog = weekLog
                )
            }
        }

        // ── Today's Stats Loop ───────────────────────────────────────────
        // The inner DAO flows are re-subscribed every time `todayBoundary`
        // changes so the SUMs always span "today" rather than whatever day
        // it was when the process started.
        launch {
            todayBoundary
                .flatMapLatest { startOfToday ->
                    postureLogDao.getCumulativeUsageSince(startOfToday).asFlow()
                        .combine(postureLogDao.getCumulativeSlouchedSince(startOfToday).asFlow()) { total, slouched ->
                            Pair(total ?: 0L, slouched ?: 0L)
                        }
                }
                .collect { (totalMs, slouchedMs) -> handleTodayStatsTick(totalMs, slouchedMs) }
        }
    }

    /**
     * Persists yesterday's score (computed from DB), shuffles a new daily
     * exercise assignment, and clears today's completion list — but ONLY if
     * the calendar day-of-year has actually advanced since we last did so.
     *
     * Returns true if a rollover occurred, false otherwise. Idempotent.
     */
    private suspend fun runDayRolloverIfNeeded(): Boolean {
        val currentDay = Calendar.getInstance().get(Calendar.DAY_OF_YEAR)
        if (repository.currentDayOfYear == currentDay) return false

        val (yStart, yEnd) = dayBoundsMillis(-1)
        val yesterdayTotal = postureLogDao.getCumulativeUsageBetween(yStart, yEnd) ?: 0L
        val yesterdaySlouched = postureLogDao.getCumulativeSlouchedBetween(yStart, yEnd) ?: 0L
        val yesterdayScore = if (yesterdayTotal > 0) {
            (((yesterdayTotal - yesterdaySlouched).toDouble() / yesterdayTotal) * 100)
                .toInt()
                .coerceIn(0, 100)
        } else {
            UserRepository.NO_YESTERDAY_DATA
        }
        repository.setYesterdayScore(yesterdayScore)
        repository.setCurrentDayOfYear(currentDay)

        val assigned = ExerciseData.exercises.shuffled().take(3).map { it.title }
        repository.setAssignedExercisesList(assigned)
        repository.setCompletedExercisesTodayList(emptySet())
        return true
    }

    /**
     * Reads the assigned-exercises and completed-today lists from prefs and
     * pushes them into the various StateFlows so the UI reflects the
     * current day's challenge. Called at startup and after every detected
     * day rollover. Cheap; pure-read + state copy.
     */
    private fun refreshLocalGamificationState() {
        val completedTodaySet = repository.completedExercisesTodayList
        val assignedSet = repository.assignedExercisesList

        dashboardState.value = dashboardState.value.copy(
            activeExercisesCount = if (assignedSet.isEmpty()) 3 else assignedSet.size,
            completedExercisesCount = completedTodaySet.size,
            assignedExercises = if (assignedSet.isEmpty()) listOf("Chin Tuck", "Scapular Retractions", "Cervical Flexion") else assignedSet
        )
        rewardsState.value = rewardsState.value.copy(
            points = repository.lifetimePoints,
            exercisesDoneTotal = repository.totalExercisesDone
        )
        exercisesState.value = exercisesState.value.copy(
            doneIds = completedTodaySet
        )
    }

    private fun handleTodayStatsTick(totalMs: Long, slouchedMs: Long) {
        val healthyMs = totalMs - slouchedMs
        val newScore = if (totalMs > 0) {
            ((healthyMs.toDouble() / totalMs) * 100).toInt().coerceIn(0, 100)
        } else {
            0
        }

        // delta is only meaningful when we have *real* data for both today
        // and yesterday. NO_YESTERDAY_DATA is a sentinel meaning "yesterday
        // had zero usage logs" (e.g. first install, or app paused all day)
        // — in that case we show 0 instead of fabricating a +N delta
        // against an implicit zero.
        val ys = repository.yesterdayScore
        val delta = if (totalMs > 0 && ys != UserRepository.NO_YESTERDAY_DATA) {
            newScore - ys
        } else {
            0
        }

        // Dynamic Recommendations — pulled from Firebase Remote Config. The
        // "your score yesterday was X" copy only fires when we actually
        // have a yesterday score; otherwise we fall through to the moderate
        // variant (no specific number to call out).
        val rc = RemoteConfigManager
        val haveYesterday = ys != UserRepository.NO_YESTERDAY_DATA
        val nudge = if (haveYesterday && ys < 70) {
            NudgeData(
                rc.nudgeCriticalInstruction,
                rc.nudgeCriticalTags,
                "Your posture score yesterday was $ys. ${rc.nudgeCriticalReasoning}"
            )
        } else {
            NudgeData(
                rc.nudgeModerateInstruction,
                rc.nudgeModerateTags,
                rc.nudgeModerateReasoning
            )
        }

        val detect = if (haveYesterday && ys < 70) {
            DetectionData(
                rc.detectBadTitle,
                "Your score dropped to $ys.",
                rc.detectBadSeverity
            )
        } else {
            DetectionData(
                rc.detectGoodTitle,
                rc.detectGoodSubtitle,
                rc.detectGoodSeverity
            )
        }

        dashboardState.value = dashboardState.value.copy(
            postureScore = newScore,
            scoreDelta = delta,
            todayNudge = nudge,
            detectedToday = detect
        )
        rewardsState.value = rewardsState.value.copy(
            timeTrackedHours = (totalMs / (1000f * 60f * 60f))
        )

        // ── B-21: Wire the previously-orphaned daily-score analytics event
        // so the Firebase dashboard can track score progression over time.
        // We rate-limit by score+streak combination so we don't spam Firebase
        // every single second the cumulative stats query re-emits.
        maybeLogDailyScore(newScore, delta)
    }

    /**
     * Throttles the firing of [com.example.neckguard.NudgeAnalytics.logDailyScore]
     * so we emit at most once per (score, streak) tuple per process. Without
     * this, every recomputation would fire a fresh event — the underlying
     * DAO flow re-emits whenever the user logs a single sensor sample.
     */
    @Volatile private var lastLoggedScoreSig: Int = Int.MIN_VALUE
    private fun maybeLogDailyScore(score: Int, delta: Int) {
        val streak = dashboardState.value.streakDays
        // 16-bit pack: streak in lower bits, score in upper. Sufficient for
        // dedup since both are 0..100/0..7 in practice.
        val sig = (score shl 16) or (streak and 0xFFFF)
        if (sig == lastLoggedScoreSig) return
        lastLoggedScoreSig = sig
        try {
            com.example.neckguard.NudgeAnalytics.logDailyScore(score, delta, streak)
        } catch (_: Throwable) {}
    }

    // --- UI HOOKS ---

    fun setAppActive(active: Boolean) {
        dashboardState.value = dashboardState.value.copy(isAppActive = active)
    }

    fun setRewardsSection(section: String) {
        rewardsState.value = rewardsState.value.copy(
            expandedSection = if(rewardsState.value.expandedSection == section) "" else section
        )
    }

    fun setExercisesCategory(cat: String) {
        exercisesState.value = exercisesState.value.copy(activeCategory = cat, expandedExerciseId = null)
    }

    fun toggleExercise(id: String) {
         val current = exercisesState.value
         exercisesState.value = current.copy(expandedExerciseId = if(current.expandedExerciseId == id) null else id)
    }

    fun focusAndExpandExercise(id: String) {
        val cat = com.example.neckguard.ui.ExerciseData.exercises.firstOrNull { it.title == id }?.category ?: "Cervical"
        exercisesState.value = exercisesState.value.copy(activeCategory = cat, expandedExerciseId = id)
    }

    fun markExerciseDone(id: String) {
         val current = exercisesState.value
         // Idempotent: marking the same exercise twice within the same day
         // doesn't double-credit. (Belt + braces against B-31 in case the
         // UI ever wires up a duplicate caller.)
         if (current.doneIds.contains(id)) return

         exercisesState.value = current.copy(doneIds = current.doneIds + id, expandedExerciseId = null)

         // Update Preferences (atomic counters under COUNTER_LOCK).
         repository.addPoints(RemoteConfigManager.pointsPerExercise)
         repository.addExerciseDone()
         repository.addCompletedExerciseToday(id)
         val completedSet = repository.completedExercisesTodayList

         // Track in Firebase Analytics
         com.example.neckguard.NudgeAnalytics.logExerciseComplete(id, exercisesState.value.activeCategory)
         
         // Update UI State instantly
         rewardsState.value = rewardsState.value.copy(
             points = repository.lifetimePoints,
             exercisesDoneTotal = repository.totalExercisesDone
         )
         dashboardState.value = dashboardState.value.copy(
             completedExercisesCount = completedSet.size
         )
    }

    // --- AUTH ---

    fun checkStatus() {
        viewModelScope.launch {
            val next = withContext(Dispatchers.IO) {
                if (!repository.hydrateSession()) {
                    // Firebase says not logged in
                    return@withContext AppState.Unauthenticated
                }

                // Firebase says logged in. Ensure Supabase bridge session
                // exists (needed for REST calls — profile fetch, crash
                // uploads, etc.). If SupabaseClient already has tokens
                // (hydrated from prefs above), skip the bridge.
                if (com.example.neckguard.SupabaseClient.accessToken.isNullOrEmpty()) {
                    val bridgeOk = try {
                        com.example.neckguard.FirebaseAuthManager.bridgeToSupabase()
                    } catch (t: Throwable) {
                        android.util.Log.e("MainViewModel", "Bridge exception: ${t.message}", t)
                        false
                    }
                    android.util.Log.d("MainViewModel",
                        "Bridge result=$bridgeOk, " +
                        "supabaseToken=${com.example.neckguard.SupabaseClient.accessToken?.take(10)}..., " +
                        "supabaseUid=${com.example.neckguard.SupabaseClient.userId}"
                    )
                }

                if (repository.hasCompletedOnboarding()) {
                    AppState.Ready
                } else {
                    when (repository.restoreProfileFromSupabase()) {
                        UserRepository.RestoreResult.Restored -> AppState.Ready
                        UserRepository.RestoreResult.NoProfile -> AppState.NeedsOnboarding
                        UserRepository.RestoreResult.TransientError -> AppState.ProfileFetchFailed
                    }
                }
            }
            _appState.value = next

            // Tag Crashlytics & Analytics with the Firebase user ID.
            if (next is AppState.Ready || next is AppState.NeedsOnboarding || next is AppState.ProfileFetchFailed) {
                val uid = com.example.neckguard.FirebaseAuthManager.currentUser()?.uid ?: "unknown"
                try {
                    FirebaseCrashlytics.getInstance().setUserId(uid)
                    com.example.neckguard.NudgeAnalytics.setUserId(uid)
                } catch (_: Throwable) {}

                try {
                    com.example.neckguard.CrashReporter.flushPendingCrash()
                } catch (_: Throwable) {}

                if (next is AppState.Ready && repository.hasPendingProfileSync()) {
                    viewModelScope.launch(Dispatchers.IO) {
                        try { repository.retryPendingProfileSync() } catch (_: Throwable) {}
                    }
                }
            }
        }
    }

    fun finishOnboarding() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.completeOnboarding() }
            _appState.value = AppState.Ready
        }
    }

    fun logout(context: android.content.Context) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.logout(context) }
            _appState.value = AppState.Unauthenticated
        }
    }

    // ───────────────────────── Helpers ─────────────────────────────────

    /**
     * Returns inclusive `[start, end]` epoch-millis bounds for the day
     * `daysAgo` days before today (0 = today, 1 = yesterday, …).
     *
     * The end boundary uses 23:59:59.999 — previously we set seconds=59
     * but left milliseconds at whatever Calendar.getInstance() captured
     * (B-04), which meant logs written between :59.001 and :59.999 were
     * sometimes counted, sometimes not.
     *
     * Each call constructs a fresh Calendar so the start/end pair never
     * mutate a shared instance.
     */
    private fun dayBoundsMillis(daysAgo: Int): Pair<Long, Long> {
        val start = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, daysAgo)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val end = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, daysAgo)
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        return start to end
    }

    /** Builds the 7-day display log oldest → newest for the rewards screen. */
    private fun buildWeekLog(logs: List<com.example.neckguard.data.local.PostureLog>): List<Pair<String, String>> {
        val dayLabels = listOf("S", "M", "T", "W", "T", "F", "S")
        val out = mutableListOf<Pair<String, String>>()
        for (daysAgo in 6 downTo 0) {
            val (start, end) = dayBoundsMillis(-daysAgo)
            val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -daysAgo) }
            val label = dayLabels[cal.get(Calendar.DAY_OF_WEEK) - 1]
            val sessionCount = logs.count { it.timestampStartMs in start..end }
            val marker = when {
                sessionCount >= 2 -> "✓"
                daysAgo == 0      -> label  // today, not met yet — show the day letter
                else              -> "F"    // past day, missed
            }
            out += label to marker
        }
        return out
    }

    /**
     * Streak = consecutive days, walking *backwards from today*, where the
     * user logged at least 2 posture sessions. Today counts.
     *
     * The previous implementation walked oldest → newest, reset on any past
     * gap, and explicitly excluded today (`if (i > 0) streak++`). Result:
     * a perfect 7-day run displayed as `streak = 6`, and a gap on day -5
     * zeroed the count even if the last 4 days were perfect.
     */
    private fun computeStreak(logs: List<com.example.neckguard.data.local.PostureLog>): Int {
        var streak = 0
        for (daysAgo in 0..6) {
            val (start, end) = dayBoundsMillis(-daysAgo)
            val sessionCount = logs.count { it.timestampStartMs in start..end }
            if (sessionCount >= 2) streak++ else break
        }
        return streak
    }
}

class MainViewModelFactory(
    private val repository: UserRepository,
    private val postureLogDao: PostureLogDao
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository, postureLogDao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
