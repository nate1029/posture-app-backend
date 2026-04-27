import sys

path_ex = r'c:\Users\Naiteek\Downloads\postureapp\didi project\NeckGuardApp\app\src\main\java\com\example\neckguard\ui\ExercisesScreen.kt'

missing_code = """
@Composable
fun ExerciseDetailContent(exercise: Exercise, onClose: () -> Unit) {
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
    }
}
"""

with open(path_ex, 'a', encoding='utf-8') as f:
    f.write(missing_code)
print('Appended ExerciseDetailContent!')
