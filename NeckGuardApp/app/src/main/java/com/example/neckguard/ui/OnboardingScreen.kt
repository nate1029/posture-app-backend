package com.example.neckguard.ui

import android.content.SharedPreferences
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun OnboardingScreen(prefs: SharedPreferences, onComplete: () -> Unit) {
    var currentStep by remember { mutableStateOf(0) }
    
    // User selections
    var name by remember { mutableStateOf("") }
    var ageGroup by remember { mutableStateOf("") }
    var notificationVibe by remember { mutableStateOf("") }
    var usageContext by remember { mutableStateOf("") }
    var neckHealth by remember { mutableStateOf("") }
    var checkIntervalMs by remember { mutableStateOf(30 * 60 * 1000L) } // Default 30 min

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 48.dp, start = 24.dp, end = 24.dp, bottom = 24.dp)
    ) {
        // Progress Bar
        LinearProgressIndicator(
            progress = { (currentStep + 1) / 6f },
            modifier = Modifier.fillMaxWidth().height(8.dp),
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Slide Animation Content
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally(animationSpec = tween(300)) { width -> width } + fadeIn() togetherWith
                            slideOutHorizontally(animationSpec = tween(300)) { width -> -width } + fadeOut()
                } else {
                    slideInHorizontally(animationSpec = tween(300)) { width -> -width } + fadeIn() togetherWith
                            slideOutHorizontally(animationSpec = tween(300)) { width -> width } + fadeOut()
                }
            }, label = "onboarding_animation"
        ) { step ->
            when (step) {
                0 -> QuestionName(name) { name = it }
                1 -> QuestionSingleChoice(
                    title = "How old are you?",
                    subtitle = "Affects stretch recommendations and how we talk to you.",
                    options = listOf("Under 18", "18–24", "25–34", "35–44", "45–54", "55+"),
                    selected = ageGroup,
                    onSelect = { ageGroup = it }
                )
                2 -> QuestionSingleChoice(
                    title = "Pick your notification vibe",
                    subtitle = "How should we remind you? Be honest.",
                    options = listOf(
                        "Chaotic — memes, gen-z humour, unhinged energy",
                        "Hype mode — motivational, hustle, 'you got this'",
                        "Calm & mindful — gentle, soft, no pressure",
                        "Just the facts — clean, direct, no fluff"
                    ),
                    selected = notificationVibe,
                    onSelect = { notificationVibe = it }
                )
                3 -> QuestionSingleChoice(
                    title = "What's your phone situation mostly?",
                    subtitle = "",
                    options = listOf(
                        "Student — classes, studying, scrolling",
                        "Desk job — computer + phone all day",
                        "Work from home — flexible hours, couch included",
                        "Just a casual user — scroll, watch, chill"
                    ),
                    selected = usageContext,
                    onSelect = { usageContext = it }
                )
                4 -> QuestionSingleChoice(
                    title = "How's your neck right now?",
                    subtitle = "No judgment — this helps us calibrate how urgent your reminders should be.",
                    options = listOf(
                        "All good, no issues — just here to stay on top of things",
                        "Occasional stiffness — gets achy after long sessions",
                        "Pretty frequent discomfort — my neck is basically always tense",
                        "Chronic / ongoing pain — already seeing or should see a physio"
                    ),
                    selected = neckHealth,
                    onSelect = { neckHealth = it }
                )
                5 -> QuestionIntervalSelection(checkIntervalMs) { checkIntervalMs = it }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        val coroutineScope = rememberCoroutineScope()
        var syncingProfile by remember { mutableStateOf(false) }
        var syncError by remember { mutableStateOf<String?>(null) }

        if (syncError != null) {
            Text(syncError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        // Navigation Buttons
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (currentStep > 0 && !syncingProfile) {
                OutlinedButton(
                    onClick = { currentStep-- },
                    modifier = Modifier.height(50.dp)
                ) { Text("Back") }
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }

            if (syncingProfile) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = {
                        if (currentStep < 5) {
                            currentStep++
                        } else {
                            syncingProfile = true
                            syncError = null
                            coroutineScope.launch {
                                // Upload Questionnaire to Supabase!
                                val success = com.example.neckguard.SupabaseClient.saveProfile(
                                    name, ageGroup, notificationVibe, usageContext, neckHealth, checkIntervalMs
                                )
                                syncingProfile = false
                                
                                if (success) {
                                    // Finish Onboarding
                                    prefs.edit().apply {
                                        putString("UserName", name)
                                        putString("UserAgeGrp", ageGroup)
                                        putString("UserVibe", notificationVibe)
                                        putString("UserContext", usageContext)
                                        putString("UserHealth", neckHealth)
                                        putLong("IntervalPreferenceMs", checkIntervalMs)
                                        putBoolean("OnboardingComplete", true)
                                    }.apply()
                                    onComplete()
                                } else {
                                    syncError = "Failed to sync to Supabase. Check internet connection!"
                                }
                            }
                        }
                    },
                    enabled = when (currentStep) {
                        0 -> name.isNotBlank()
                        1 -> ageGroup.isNotBlank()
                        2 -> notificationVibe.isNotBlank()
                        3 -> usageContext.isNotBlank()
                        4 -> neckHealth.isNotBlank()
                        else -> true
                    },
                    modifier = Modifier.height(50.dp)
                ) {
                    Text(if (currentStep == 5) "Finish Setup" else "Continue")
                }
            }
        }
    }
}

@Composable
fun QuestionName(name: String, onNameChange: (String) -> Unit) {
    Column {
        Text("Hey, what should we call you?", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("We'll use this in your personalized reminders.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text("Your Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
    }
}

@Composable
fun QuestionSingleChoice(title: String, subtitle: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        if (subtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(24.dp))
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(options) { option ->
                val isSelected = option == selected
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(option) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Text(
                            option, 
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun QuestionIntervalSelection(selectedMs: Long, onSelect: (Long) -> Unit) {
    val options = listOf(
        15 * 60 * 1000L to "Every 15 min — I need help",
        30 * 60 * 1000L to "Every 30 min — recommended",
        45 * 60 * 1000L to "Every 45 min — balanced",
        60 * 60 * 1000L to "Every 60 min — light touch",
        90 * 60 * 1000L to "Every 90 min — just nudges"
    )
    
    Column {
        Text("How often should we check in?", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("You can always change this later in settings.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(24.dp))
        
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(options) { (ms, label) ->
                val isSelected = ms == selectedMs
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(ms) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp)) {
                        Text(
                            label, 
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
