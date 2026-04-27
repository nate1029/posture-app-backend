package com.example.neckguard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.neckguard.data.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class AppState {
    object Loading : AppState()
    object Unauthenticated : AppState()
    object NeedsOnboarding : AppState()
    object Ready : AppState()
}

/** 
 * UI State Models exposing the basic backend gamification / posture algorithm.
 */
data class DashboardState(
    val postureScore: Int = 82,
    val scoreDelta: Int = 6,
    val streakDays: Int = 5,
    val nextNudgeMins: Int = 18,
    val isAppActive: Boolean = false,
    val activeExercisesCount: Int = 3,
    val completedExercisesCount: Int = 2,
    val highScreenDistancePct: Int = 68
)

data class RewardsState(
    val expandedSection: String = "week",
    val timeTrackedHours: Float = 11.4f,
    val exercisesDoneTotal: Int = 9,
    val totalRequiredExercises: Int = 14,
    val points: Int = 420,
    val weekLog: List<Pair<String, String>> = listOf("M" to "✓", "T" to "✓", "W" to "✓", "T" to "!", "F" to "F", "S" to "", "S" to "")
)

data class ExercisesState(
    val activeCategory: String = "Cervical Movements",
    val expandedExerciseId: String? = null,
    val doneIds: Set<String> = emptySet()
)

class MainViewModel(private val repository: UserRepository) : ViewModel() {
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
     * V1 BASIC BACKEND ALGORITHM LOOP:
     * This simulates the dynamic scaling of the posture score and cooldowns.
     * In the future, this hooks directly into Room databases or Supabase fetches.
     */
    private fun startGamificationAndPostureEngine() = viewModelScope.launch(Dispatchers.IO) {
        var totalMinutesActive = 0
        var totalGoodPostureMinutes = 0
        
        while(true) {
            delay(60000L) // 1 Minute Simulation Tick
            
            val dash = dashboardState.value
            if (dash.isAppActive) {
                totalMinutesActive++
                // Simulate: The user has an 80% chance to hold good posture for this minute block
                if (Math.random() > 0.2) totalGoodPostureMinutes++

                // Dynamic Score Algorithm
                val newScore = ((totalGoodPostureMinutes.toFloat() / totalMinutesActive) * 100).toInt().coerceIn(0, 100)
                
                // Nudge Timer Countdown
                var newNudge = dash.nextNudgeMins - 1
                if (newNudge <= 0) newNudge = 30 // Cooldown reset

                // Dynamic Points Stream (1 minute of good posture = roughly 2 points)
                val newPts = rewardsState.value.points + if (Math.random() > 0.2) 2 else 0

                dashboardState.value = dash.copy(
                    postureScore = newScore,
                    nextNudgeMins = newNudge
                )
                
                rewardsState.value = rewardsState.value.copy(
                    points = newPts,
                    timeTrackedHours = rewardsState.value.timeTrackedHours + 0.016f // adds fraction of hour
                )
            }
        }
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

    fun markExerciseDone(id: String) {
         val current = exercisesState.value
         exercisesState.value = current.copy(doneIds = current.doneIds + id, expandedExerciseId = null)
         
         // Backend Algo: Add game points & stats upon completion
         rewardsState.value = rewardsState.value.copy(
             points = rewardsState.value.points + 50,
             exercisesDoneTotal = rewardsState.value.exercisesDoneTotal + 1
         )
         dashboardState.value = dashboardState.value.copy(
             completedExercisesCount = dashboardState.value.completedExercisesCount + 1
         )
    }

    // --- AUTH ---

    fun checkStatus() {
        viewModelScope.launch {
            val next = withContext(Dispatchers.IO) {
                if (repository.hydrateSession()) {
                    if (repository.hasCompletedOnboarding()) AppState.Ready
                    else AppState.NeedsOnboarding
                } else {
                    AppState.Unauthenticated
                }
            }
            _appState.value = next
        }
    }

    fun finishOnboarding() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.completeOnboarding() }
            _appState.value = AppState.Ready
        }
    }

    fun logout() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { repository.logout() }
            _appState.value = AppState.Unauthenticated
        }
    }
}

class MainViewModelFactory(private val repository: UserRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
