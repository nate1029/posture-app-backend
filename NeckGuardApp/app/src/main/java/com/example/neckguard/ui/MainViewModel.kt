package com.example.neckguard.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.neckguard.data.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed class AppState {
    object Loading : AppState()
    object Unauthenticated : AppState()
    object NeedsOnboarding : AppState()
    object Ready : AppState()
}

class MainViewModel(private val repository: UserRepository) : ViewModel() {
    private val _appState = MutableStateFlow<AppState>(AppState.Loading)
    val appState: StateFlow<AppState> = _appState.asStateFlow()

    init {
        checkStatus()
    }

    fun checkStatus() {
        if (repository.hydrateSession()) {
            if (repository.hasCompletedOnboarding()) {
                _appState.value = AppState.Ready
            } else {
                _appState.value = AppState.NeedsOnboarding
            }
        } else {
            _appState.value = AppState.Unauthenticated
        }
    }

    fun finishOnboarding() {
        repository.completeOnboarding()
        _appState.value = AppState.Ready
    }
    
    fun logout() {
        repository.logout()
        _appState.value = AppState.Unauthenticated
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
