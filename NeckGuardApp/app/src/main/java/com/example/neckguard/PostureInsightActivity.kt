package com.example.neckguard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.neckguard.data.UserRepository
import com.example.neckguard.ui.theme.*

/**
 * Displays a "Why was my posture bad?" summary when the user taps
 * "Learn more" from a bad-posture result notification.
 *
 * Receives extras:
 *  - neck_pitch  (Float)  — measured neck flexion in degrees
 *  - spinal_load_kg (Int) — estimated spinal load
 *  - posture_state (String) — POOR, MODERATE, or GOOD
 */
class PostureInsightActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val neckPitch = intent.getFloatExtra("neck_pitch", 0f)
        val spinalLoadKg = intent.getIntExtra("spinal_load_kg", 12)
        val postureState = intent.getStringExtra("posture_state") ?: "POOR"

        // Pick next exercise for CTA
        val prefs = SecurePrefs.get(this)
        val repository = UserRepository(prefs)
        val assignedList = repository.assignedExercisesList
        val completedList = repository.completedExercisesTodayList
        val targetExercise = assignedList.firstOrNull { !completedList.contains(it) }
            ?: assignedList.firstOrNull() ?: "Chin Tuck"

        setContent {
            PostureInsightScreen(
                neckPitch = neckPitch,
                spinalLoadKg = spinalLoadKg,
                postureState = postureState,
                targetExercise = targetExercise,
                onExerciseTap = {
                    val exerciseIntent = Intent(this, MainActivity::class.java).apply {
                        action = "OPEN_EXERCISE"
                        putExtra("exercise_name", targetExercise)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    }
                    startActivity(exerciseIntent)
                    finish()
                },
                onClose = { finish() }
            )
        }
    }
}

@Composable
fun PostureInsightScreen(
    neckPitch: Float,
    spinalLoadKg: Int,
    postureState: String,
    targetExercise: String,
    onExerciseTap: () -> Unit,
    onClose: () -> Unit
) {
    val isPoor = postureState == "POOR"
    val severityBg = if (isPoor) PoorBg else ModBg
    val severityText = if (isPoor) PoorText else ModText
    val severityLabel = if (isPoor) "Poor Posture Detected" else "Moderate Posture Detected"
    val headWeightMultiple = String.format("%.1f", spinalLoadKg / 5f)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        // ── Top bar with close button ──
        Spacer(modifier = Modifier.statusBarsPadding())
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Your Posture Insight \uD83D\uDD0D",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Slate
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(TealWash)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center
            ) {
                Text("\u2715", fontSize = 16.sp, color = Slate)
            }
        }
        Text(
            text = "Here\u2019s what our sensors detected",
            fontSize = 14.sp,
            color = SlateMuted,
            modifier = Modifier.padding(top = 4.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // ── Severity badge ──
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(severityBg)
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = severityLabel,
                color = severityText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Card 1: What Was Detected ──
        InsightCard(title = "What Was Detected") {
            Text(
                text = "Your neck was tilted ~${neckPitch.toInt()}\u00b0 forward",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Slate
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "That puts approximately ${spinalLoadKg}kg of force on your cervical spine \u2014 about ${headWeightMultiple}x the weight of your head.",
                fontSize = 14.sp,
                color = SlateMuted,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Visual load indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("0\u00b0", fontSize = 12.sp, color = SlateMuted)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(TealWash)
                ) {
                    val fraction = (neckPitch / 60f).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(fraction)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isPoor) Alert else Amber)
                    )
                }
                Text("60\u00b0", fontSize = 12.sp, color = SlateMuted)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Card 2: Why It Matters ──
        InsightCard(title = "Why It Matters") {
            val explanation = if (isPoor) {
                "At ${neckPitch.toInt()}\u00b0, your cervical spine is bearing ${spinalLoadKg}kg \u2014 " +
                "nearly ${headWeightMultiple}x the weight of your head. Sustained forward tilt " +
                "compresses spinal discs and strains neck muscles, which can lead to chronic " +
                "stiffness, tension headaches, and long-term damage.\n\n" +
                "Research by Dr. Kenneth Hansraj (2014) found that even 15\u00b0 of forward " +
                "tilt increases cervical spine load to 12kg, and it escalates rapidly from there."
            } else {
                "Even mild forward tilt (~${neckPitch.toInt()}\u00b0) increases the load on your " +
                "cervical spine beyond neutral. While not immediately harmful, sustained moderate " +
                "tilt can develop into chronic text neck over time if not corrected.\n\n" +
                "The good news? Small corrections now prevent bigger problems later."
            }

            Text(
                text = explanation,
                fontSize = 14.sp,
                color = SlateMuted,
                lineHeight = 20.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Card 3: How to Fix It ──
        InsightCard(title = "How to Fix It") {
            val tips = listOf(
                "\uD83D\uDCF1  Bring your phone to eye level \u2014 reduce the angle",
                "\uD83E\uDDD8  Take a 30-second break to roll your shoulders",
                "\uD83E\uDD14  Be mindful of your posture when sitting for long periods",
                "\uD83D\uDCAA  Try a quick exercise to strengthen your neck muscles"
            )
            tips.forEach { tip ->
                Text(
                    text = tip,
                    fontSize = 14.sp,
                    color = Slate,
                    modifier = Modifier.padding(vertical = 4.dp),
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Exercise CTA Button ──
        Button(
            onClick = onExerciseTap,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Teal)
        ) {
            Text(
                text = "Try $targetExercise \uD83D\uDCAA",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(modifier = Modifier.height(40.dp).navigationBarsPadding())
    }
}

@Composable
fun InsightCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(White)
            .border(1.dp, CardBorder, RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        Text(
            text = title,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = Teal
        )
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}
