package com.example.neckguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared header widgets so the brand pill and settings button render IDENTICALLY on every
 * tab. Previously each screen inlined its own copy, which drifted apart (different font
 * sizes, emoji-vs-vector gear, different circle colors) — see UI_POLISH_BACKLOG BUG-01/02.
 * Centralising them here makes that drift impossible.
 */

/** Top-left "NudgeUp ↑" brand pill. Same on Home, Progress, and Exercises. */
@Composable
fun NudgeUpBrandPill() {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .background(FinalMoss, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text("NudgeUp ↑", color = FinalCream, fontSize = 15.sp, fontWeight = FontWeight.Bold)
    }
}

/** Top-right circular settings button. Uses the Material vector icon (not an emoji). */
@Composable
fun SettingsIconButton(onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .size(36.dp)
            .background(FinalWhite, CircleShape)
            .border(2.dp, FinalMist, CircleShape)
            .clip(CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Default.Settings,
            contentDescription = "Settings",
            tint = FinalMuted,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Single source of truth for check-interval presets, shared by Onboarding and Settings so a
 * value chosen in one is always a selectable preset in the other (BUG-07). Each entry is
 * (milliseconds → onboarding-style label). Settings renders its own terse label from the ms.
 */
object IntervalOptions {
    val presets: List<Pair<Long, String>> = listOf(
        15 * 60 * 1000L to "Every 15 min — I need help",
        30 * 60 * 1000L to "Every 30 min — recommended",
        45 * 60 * 1000L to "Every 45 min — balanced",
        60 * 60 * 1000L to "Every 60 min — light touch",
        90 * 60 * 1000L to "Every 90 min — just nudges"
    )
}
