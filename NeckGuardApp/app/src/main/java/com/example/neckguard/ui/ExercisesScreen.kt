package com.example.neckguard.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
    val videoResId: Int?
)

object ExerciseData {
    val exercises = listOf(
        // Cervical Movements
        Exercise("Cervical Flexion", "Cervical Movements", "Slowly bring your chin toward your chest. Return to the starting position.", "10 repetitions", 0, com.example.neckguard.R.raw.vid_cervical_flexion),
        Exercise("Cervical Extension", "Cervical Movements", "Slowly look upward toward the ceiling. Return to the starting position.", "10 repetitions", 0, com.example.neckguard.R.raw.vid_cervical_extension),
        Exercise("Cervical Side Flexion", "Cervical Movements", "Tilt your ear toward your shoulder. Perform on both sides.", "10 reps each side", 0, com.example.neckguard.R.raw.vid_cervical_side_flexion),
        Exercise("Cervical Rotation", "Cervical Movements", "Slowly rotate your head to the left and right. Keep the movement controlled and pain-free.", "10 reps each side", 0, com.example.neckguard.R.raw.vid_cervical_rotation),
        Exercise("Chin Tuck", "Cervical Movements", "Pull your chin straight backward without bending your neck forward. Hold for 5 seconds.", "10 repetitions", 5, com.example.neckguard.R.raw.vid_chin_tuck),

        // Stretching Exercises
        Exercise("Upper Trapezius Stretch", "Stretching Exercises", "Tilt your head to one side, bringing your ear toward your shoulder. Use your hand to apply gentle pressure. Keep opposite shoulder relaxed.", "3 times each side", 30, com.example.neckguard.R.raw.vid_trapezius_stretch),
        Exercise("Levator Scapulae Stretch", "Stretching Exercises", "Turn your head 45 degrees to one side. Look down toward your armpit. Apply gentle pressure with your hand.", "3 times each side", 30, com.example.neckguard.R.raw.vid_levator_stretch),
        Exercise("Seated Pectoral Stretch", "Stretching Exercises", "Sit or stand upright. Interlock your fingers behind your back. Gently pull your shoulders back and open your chest.", "3 repetitions", 30, com.example.neckguard.R.raw.vid_pectoral_stretch),

        // Strengthening and Postural Exercises
        Exercise("Scapular Retractions", "Strengthening", "Pull your shoulder blades back and down. Keep your neck relaxed.", "10 repetitions", 0, com.example.neckguard.R.raw.vid_scapular_retractions),
        Exercise("Shoulder Shrugs", "Strengthening", "Lift your shoulders up toward your ears. Hold briefly and then relax slowly down.", "10 repetitions", 0, com.example.neckguard.R.raw.vid_shoulder_shrugs),
        Exercise("Seated Thoracic Extension", "Strengthening", "Sit upright with your hands behind your head. Gently extend your upper back backward over the chair. Return slowly.", "10 repetitions", 0, com.example.neckguard.R.raw.vid_thoracic_extension),
        Exercise("Seated Thoracic Mobility", "Strengthening", "Sit upright with your hands on your thighs. Round your upper back forward. Then straighten your back to an upright position.", "10 repetitions", 0, com.example.neckguard.R.raw.vid_thoracic_mobility),
        Exercise("Isometric Neck Strengthening", "Strengthening", "Press your head gently into your hand in each direction (forward, backward, sides). Do not allow neck to move.", "10 reps / 5s hold", 5, com.example.neckguard.R.raw.vid_isometrics),

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
fun ExercisesScreen(viewModel: MainViewModel, initialExercise: String? = null, onSettingsClick: () -> Unit) {
    val state by viewModel.exercisesState.collectAsState()
    var selectedExercise by remember { mutableStateOf<Exercise?>(null) }
    
    // Auto-open if deeplinked
    LaunchedEffect(initialExercise) {
        if (initialExercise != null) {
            val matched = ExerciseData.exercises.find { it.title == initialExercise }
            if (matched != null) {
                selectedExercise = matched
            }
        }
    }
    val groupedExercises = ExerciseData.exercises.groupBy { it.category }
    val categories = groupedExercises.keys.toList()
    val activeCategory = state.activeCategory
    val expandedExerciseId = state.expandedExerciseId
    
    // Fake state for demo
    val doneIds = state.doneIds

    // ── Swipeable categories (BUG-03) ────────────────────────────────────
    // The category tabs and the list are driven by one pager so the user can
    // either tap a tab OR swipe the list left/right to change category. Both
    // stay in sync with viewModel.activeCategory (the single source of truth).
    val activeIndex = categories.indexOf(activeCategory).coerceAtLeast(0)
    val pagerState = androidx.compose.foundation.pager.rememberPagerState(
        initialPage = activeIndex,
        pageCount = { categories.size }
    )
    val tabListState = androidx.compose.foundation.lazy.rememberLazyListState()

    // Tab tap (activeCategory changes) → animate pager + keep active tab visible.
    LaunchedEffect(activeCategory) {
        val idx = categories.indexOf(activeCategory)
        if (idx >= 0) {
            if (idx != pagerState.currentPage) pagerState.animateScrollToPage(idx)
            tabListState.animateScrollToItem(idx)
        }
    }
    // Swipe settles on a new page → update the active category so the tab highlight follows.
    LaunchedEffect(pagerState.settledPage) {
        val cat = categories.getOrNull(pagerState.settledPage)
        if (cat != null && cat != activeCategory) viewModel.setExercisesCategory(cat)
    }

    Column(modifier = Modifier.fillMaxSize().background(FinalCream)) {
        // HDR
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(androidx.compose.ui.graphics.Brush.linearGradient(colors = listOf(FinalBark, FinalBarkSoft)))
                .statusBarsPadding()
                .padding(top = 16.dp, bottom = 20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NudgeUpBrandPill()
                    SettingsIconButton(onClick = onSettingsClick)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Your exercises", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = FinalWhite, modifier = Modifier.padding(horizontal = 22.dp))
                Spacer(modifier = Modifier.height(4.dp))
                Text("Evidence-based • assigned to your posture profile", fontSize = 15.sp, color = FinalWhite.copy(alpha=0.45f), modifier = Modifier.padding(horizontal = 22.dp))
                Spacer(modifier = Modifier.height(14.dp))
                
                // Tabs
                androidx.compose.foundation.lazy.LazyRow(
                    state = tabListState,
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
                            Text(cat, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if(isActive) FinalWhite else FinalWhite.copy(alpha=0.5f))
                        }
                    }
                }
            }
        }
        
        // List — one page per category; swipe horizontally to switch (BUG-03).
        androidx.compose.foundation.pager.HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
        val list = groupedExercises[categories[page]] ?: emptyList()
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start=16.dp, end=16.dp, top=16.dp, bottom=250.dp),
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
                            Text("${i+1}", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = FinalBark)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(ex.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = FinalBark)
                                if (isDone) {
                                    Spacer(modifier = Modifier.width(7.dp))
                                    Box(modifier = Modifier.background(FinalSage, RoundedCornerShape(20.dp)).padding(horizontal=7.dp, vertical=2.dp)) {
                                        Text("Done ✓", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinalWhite)
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(ex.reps, fontSize = 14.sp, color = FinalMuted)
                        }
                        Text(if(isExpanded) "▲" else "▼", fontSize = 14.sp, color = FinalMuted)
                    }
                    
                    if (isExpanded) {
                        Column(modifier = Modifier.padding(start=16.dp, end=16.dp, bottom=14.dp)) {
                            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(FinalMist))
                            Spacer(modifier = Modifier.height(14.dp))
                            
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.background(FinalEarthPale, RoundedCornerShape(10.dp)).padding(8.dp, 12.dp)) {
                                Text("🔁", fontSize = 17.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(ex.reps, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FinalBarkSoft)
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Steps simulation
                            val steps = ex.description.split(". ").filter { it.isNotBlank() }
                            steps.forEachIndexed { si, stepText ->
                                Row(modifier = Modifier.padding(bottom = 9.dp)) {
                                    Box(modifier = Modifier.size(20.dp).background(FinalSagePale, CircleShape), contentAlignment = Alignment.Center) {
                                        Text("${si+1}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = FinalMoss)
                                    }
                                    Spacer(modifier = Modifier.width(9.dp))
                                    Text(stepText + (if(!stepText.endsWith(".")) "." else ""), fontSize = 15.sp, color = FinalBarkSoft, lineHeight = 18.sp)
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
                                    Text("✓ Completed", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FinalMoss)
                                }
                            } else {
                                androidx.compose.material3.Button(
                                    onClick = { selectedExercise = ex }, // Opens Bottom Sheet
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = FinalBark),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Start Exercise", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FinalCream)
                                }
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
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { selectedExercise = null },
            sheetState = sheetState,
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
            .verticalScroll(rememberScrollState())
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // --- 1. Video Section (Full width, Fade to background) ---
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // If it's a video, use 9:16 aspect ratio. If text-only (eye exercises), we can just give it a smaller fixed height.
                .then(if (exercise.videoResId != null) Modifier.aspectRatio(9f/16f) else Modifier.height(200.dp))
                .background(FinalMist), // subtle background if video takes a bit to load or text-only
            contentAlignment = Alignment.Center
        ) {
            if (exercise.videoResId != null) {
                androidx.compose.ui.viewinterop.AndroidView(
                    factory = { context ->
                        android.widget.VideoView(context).apply {
                            setVideoURI(android.net.Uri.parse("android.resource://${context.packageName}/${exercise.videoResId}"))
                            setOnPreparedListener { mp ->
                                mp.isLooping = true
                                mp.setVolume(0f, 0f)
                                start()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // No video (e.g. Eye Relief)
                Text(
                    "Follow the instructions below",
                    color = FinalMoss,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 19.sp
                )
            }

            // The Fade-to-Background Gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Transparent, FinalWhite)
                        )
                    )
            )

            // Float the Timer/Reps on top of the gradient
            Box(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)) {
                if (exercise.durationSecs > 0) {
                    Button(
                        onClick = { 
                            if (timeLeft == 0) timeLeft = exercise.durationSecs
                            timerRunning = !timerRunning 
                        },
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (timerRunning) Color(0xFFD4A373) else FinalMoss
                        ),
                        elevation = ButtonDefaults.buttonElevation(0.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = if (timeLeft == 0) "✓" else "${timeLeft}s",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = FinalWhite,
                            maxLines = 1
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(40.dp))
                            .background(FinalWhite.copy(alpha=0.9f))
                            .border(1.dp, FinalMist, RoundedCornerShape(40.dp))
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            exercise.reps,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp,
                            color = FinalBark
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- 2. Info Section (Padded) ---
        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = exercise.title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = FinalBark,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = exercise.category.uppercase(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                letterSpacing = 0.96.sp,
                color = FinalSage
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            // Instruction Box for clear visibility (especially for Eye exercises)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(FinalCream, RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Text(
                    text = exercise.description,
                    fontSize = 16.sp,
                    color = FinalBarkSoft,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- 3. Explicit completion controls ---
            Button(
                onClick = onMarkDone,
                modifier = Modifier.fillMaxWidth().height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FinalBark)
            ) {
                Text(
                    "I've completed this",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = FinalWhite
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.material3.TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Cancel",
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    color = FinalBarkSoft
                )
            }
        }
    }
}
