package com.example.neckguard.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.neckguard.ui.theme.*
import kotlinx.coroutines.delay

data class Exercise(
    val title: String,
    val category: String,
    val description: String,
    val reps: String,
    val durationSecs: Int,
    val lottieRawName: String?
)

object ExerciseData {
    val exercises = listOf(
        // Cervical Movements
        Exercise("Cervical Flexion", "Cervical Movements", "Slowly bring your chin toward your chest. Return to the starting position.", "10 repetitions", 0, null),
        Exercise("Cervical Extension", "Cervical Movements", "Slowly look upward toward the ceiling. Return to the starting position.", "10 repetitions", 0, null),
        Exercise("Cervical Side Flexion", "Cervical Movements", "Tilt your ear toward your shoulder. Perform on both sides.", "10 reps each side", 0, null),
        Exercise("Cervical Rotation", "Cervical Movements", "Slowly rotate your head to the left and right. Keep the movement controlled and pain-free.", "10 reps each side", 0, null),
        Exercise("Chin Tuck", "Cervical Movements", "Pull your chin straight backward without bending your neck forward. Hold for 5 seconds.", "10 repetitions", 5, "anim_chin_tuck"),

        // Stretching Exercises
        Exercise("Upper Trapezius Stretch", "Stretching Exercises", "Tilt your head to one side, bringing your ear toward your shoulder. Use your hand to apply gentle pressure. Keep opposite shoulder relaxed.", "3 times each side", 30, null),
        Exercise("Levator Scapulae Stretch", "Stretching Exercises", "Turn your head 45 degrees to one side. Look down toward your armpit. Apply gentle pressure with your hand.", "3 times each side", 30, null),
        Exercise("Seated Pectoral Stretch", "Stretching Exercises", "Sit or stand upright. Interlock your fingers behind your back. Gently pull your shoulders back and open your chest.", "3 repetitions", 30, null),

        // Strengthening and Postural Exercises
        Exercise("Scapular Retractions", "Strengthening", "Pull your shoulder blades back and down. Keep your neck relaxed.", "10 repetitions", 5, null),
        Exercise("Shoulder Shrugs", "Strengthening", "Lift your shoulders up toward your ears. Hold briefly and then relax slowly down.", "10 repetitions", 0, null),
        Exercise("Seated Thoracic Extension", "Strengthening", "Sit upright with your hands behind your head. Gently extend your upper back backward over the chair. Return slowly.", "10 repetitions", 0, null),
        Exercise("Seated Thoracic Mobility", "Strengthening", "Sit upright with your hands on your thighs. Round your upper back forward. Then straighten your back to an upright position.", "10 repetitions", 0, null),
        Exercise("Isometric Neck Strengthening", "Strengthening", "Press your head gently into your hand in each direction (forward, backward, sides). Do not allow neck to move.", "10 reps / 5s hold", 5, null),

        // Eye Relief
        Exercise("20-20-20 Rule", "Eye Relief", "Every 20 minutes, look at an object approximately 20 feet away for 20 seconds.", "1 repetition", 20, null),
        Exercise("Blinking Exercise", "Eye Relief", "Close and open your eyes slowly and fully. Repeat during each screen break.", "10 repetitions", 0, null),
        Exercise("Near-Far Focus Shift", "Eye Relief", "Look at a near object for 5 seconds. Then look at a distant object for 5 seconds.", "10 repetitions", 10, null)
    )
}

// Card color assignment per category
private val categoryColors = mapOf(
    "Cervical Movements" to CardTealWash,
    "Stretching Exercises" to CardGolden,
    "Strengthening" to CardPeriwinkle,
    "Eye Relief" to CardSand
)

// Arrow button bg — darkest shade of card color family
private val arrowBgColors = mapOf(
    "Cervical Movements" to Teal,
    "Stretching Exercises" to Color(0xFF9E8C20),
    "Strengthening" to Color(0xFF6B71A0),
    "Eye Relief" to Color(0xFF8A7E65)
)


// ── Shape tokens (local) ─────────────────────────────────────────
private val RCard = 20.dp
private val RPill = 40.dp

val FinalBark = Color(0xFF3D2E1E)
val FinalBarkSoft = Color(0xFF5C4A35)
val FinalSage = Color(0xFF7A9E7E)
val FinalSageLight = Color(0xFFB5CEB8)
val FinalSagePale = Color(0xFFE8F0E9)
val FinalMoss = Color(0xFF4A6741)
val FinalMist = Color(0xFFEEF2EE)
val FinalMuted = Color(0xFF8A8A7A)
val FinalWhite = Color(0xFFFFFFFF)
val FinalCream = Color(0xFFFAF8F3)
val FinalEarthPale = Color(0xFFF7F0E6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExercisesScreen(viewModel: MainViewModel, onSettingsClick: () -> Unit) {
    val state by viewModel.exercisesState.collectAsState()
    var selectedExercise by remember { mutableStateOf<Exercise?>(null) }
    val groupedExercises = ExerciseData.exercises.groupBy { it.category }
    val categories = groupedExercises.keys.toList()
    val activeCategory = state.activeCategory
    val expandedExerciseId = state.expandedExerciseId
    
    // Fake state for demo
    val doneIds = state.doneIds

    Column(modifier = Modifier.fillMaxSize().background(FinalCream)) {
        // HDR
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Brush.linearGradient(colors = listOf(FinalBark, FinalBarkSoft)))
                .padding(top = 52.dp, bottom = 20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.background(FinalMoss, RoundedCornerShape(20.dp)).padding(horizontal = 14.dp, vertical = 6.dp)) {
                        Text("NudgeUp ↑", color = FinalCream, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Box(modifier = Modifier.size(36.dp).background(FinalEarthPale, CircleShape).border(2.dp, FinalEarthPale, CircleShape).clip(CircleShape).clickable { onSettingsClick() }, contentAlignment = Alignment.Center) {
                        Text("⚙", fontSize = 15.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Your exercises", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = FinalWhite, modifier = Modifier.padding(horizontal = 22.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text("Evidence-based • assigned to your posture profile", fontSize = 12.sp, color = FinalWhite.copy(alpha=0.45f), modifier = Modifier.padding(horizontal = 22.dp))
                Spacer(modifier = Modifier.height(14.dp))
                
                // Tabs
                androidx.compose.foundation.lazy.LazyRow(
                    contentPadding = PaddingValues(horizontal = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    items(categories.size) { i ->
                        val cat = categories[i]
                        val isActive = cat == activeCategory
                        Box(
                            modifier = Modifier
                                .border(1.5.dp, if(isActive) FinalSage else FinalWhite.copy(alpha=0.2f), RoundedCornerShape(20.dp))
                                .background(if(isActive) FinalSage else Color.Transparent, RoundedCornerShape(20.dp))
                                .clickable { viewModel.setExercisesCategory(cat) }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(cat, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if(isActive) FinalWhite else FinalWhite.copy(alpha=0.5f))
                        }
                    }
                }
            }
        }
        
        // List
        val list = groupedExercises[activeCategory] ?: emptyList()
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start=16.dp, end=16.dp, top=16.dp, bottom=100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(list.size) { i ->
                val ex = list[i]
                val isExpanded = expandedExerciseId == ex.title
                val isDone = doneIds.contains(ex.title)
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(FinalWhite, RoundedCornerShape(16.dp))
                        .border(1.dp, FinalMist, RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.toggleExercise(ex.title)
                            }
                            .padding(14.dp, 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(34.dp).background(FinalSageLight.copy(alpha=0.4f), RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                            Text("${i+1}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FinalBark)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(ex.title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = FinalBark)
                                if (isDone) {
                                    Spacer(modifier = Modifier.width(7.dp))
                                    Box(modifier = Modifier.background(FinalSage, RoundedCornerShape(20.dp)).padding(horizontal=7.dp, vertical=2.dp)) {
                                        Text("Done ✓", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = FinalWhite)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(ex.reps, fontSize = 11.sp, color = FinalMuted)
                        }
                        Text(if(isExpanded) "▲" else "▼", fontSize = 10.sp, color = FinalMuted)
                    }
                    
                    if (isExpanded) {
                        Column(modifier = Modifier.padding(start=16.dp, end=16.dp, bottom=14.dp)) {
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(FinalMist))
                            Spacer(modifier = Modifier.height(14.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(FinalEarthPale, RoundedCornerShape(10.dp)).padding(8.dp, 12.dp)) {
                                Text("🔁", fontSize = 14.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(ex.reps, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = FinalBarkSoft)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Steps simulation
                            val steps = ex.description.split(". ").filter { it.isNotBlank() }
                            steps.forEachIndexed { si, stepText ->
                                Row(modifier = Modifier.padding(bottom = 9.dp)) {
                                    Box(modifier = Modifier.size(20.dp).background(FinalSagePale, CircleShape), contentAlignment = Alignment.Center) {
                                        Text("${si+1}", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = FinalMoss)
                                    }
                                    Spacer(modifier = Modifier.width(9.dp))
                                    Text(stepText + (if(!stepText.endsWith(".")) "." else ""), fontSize = 12.sp, color = FinalBarkSoft, lineHeight = 18.sp)
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            if (isDone) {
                                androidx.compose.material3.Button(
                                    onClick = { },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = FinalSagePale),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("✓ Completed", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinalMoss)
                                }
                            } else {
                                androidx.compose.material3.Button(
                                    onClick = { selectedExercise = ex }, // Opens Bottom Sheet
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = FinalBark),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Start Exercise", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinalCream)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedExercise != null) {
        // Marking the exercise complete now requires the user to tap an
        // explicit "I'm done" button inside [ExerciseDetailContent]. Dragging
        // the sheet closed (or hardware back) just dismisses without
        // crediting points — previously both [onDismissRequest] AND the
        // close button called markExerciseDone, which (a) credited points
        // for accidental dismissals and (b) double-credited when the user
        // tapped the close button (which itself triggered onDismissRequest).
        // (B-30 + B-31)
        ModalBottomSheet(
            onDismissRequest = { selectedExercise = null },
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = FinalWhite
        ) {
            ExerciseDetailContent(
                exercise = selectedExercise!!,
                onMarkDone = {
                    viewModel.markExerciseDone(selectedExercise!!.title)
                    selectedExercise = null
                },
                onDismiss = { selectedExercise = null }
            )
        }
    }
}


@Composable
fun ExerciseDetailContent(
    exercise: Exercise,
    onMarkDone: () -> Unit,
    onDismiss: () -> Unit
) {
    var timerRunning by remember { mutableStateOf(false) }
    var timeLeft by remember { mutableStateOf(exercise.durationSecs) }

    LaunchedEffect(timerRunning) {
        if (timerRunning && timeLeft > 0) {
            while (timeLeft > 0) {
                kotlinx.coroutines.delay(1000L)
                timeLeft--
            }
            timerRunning = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- 1. Top Section: The Rive Animation ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(TealWash),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { context ->
                    app.rive.runtime.kotlin.RiveAnimationView(context).apply {
                        setRiveResource(com.example.neckguard.R.raw.girl, autoplay = true)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- 2. Info Section ---
        Text(
            text = exercise.title,
            style = MaterialTheme.typography.headlineLarge,
            color = Slate,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = exercise.category.uppercase(),
            fontFamily = DmSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
            letterSpacing = 0.96.sp,
            color = TealSoft
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = exercise.description,
            style = MaterialTheme.typography.bodyLarge,
            color = SlateMuted,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        // --- 3. Controls & Timer ---
        if (exercise.durationSecs > 0) {
            Button(
                onClick = { 
                    if (timeLeft == 0) timeLeft = exercise.durationSecs
                    timerRunning = !timerRunning 
                },
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (timerRunning) Amber else Teal
                ),
                elevation = ButtonDefaults.buttonElevation(0.dp)
            ) {
                Text(
                    text = if (timeLeft == 0) "✓" else "${timeLeft}s",
                    fontFamily = DmSerifDisplay,
                    fontSize = 22.sp,
                    color = White
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(40.dp))
                    .background(TealWash)
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    exercise.reps,
                    fontFamily = DmSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Teal
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- 4. Explicit completion controls ---
        // Only the "I'm done" button credits the user with points and marks
        // the exercise as complete. Dismiss / drag-to-close just exit.
        Button(
            onClick = onMarkDone,
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Teal)
        ) {
            Text(
                "I'm done",
                fontFamily = DmSans,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = White
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
            Text(
                "Cancel",
                fontFamily = DmSans,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                color = SlateMuted
            )
        }
    }
}
