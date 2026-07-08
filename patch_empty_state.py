"""
Fix: Show posture summary card even when no data — with empty state message.
"""
import sys, io
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding='utf-8')

path = r'c:\Users\Naiteek\Downloads\postureapp\didi project\NeckGuardApp\app\src\main\java\com\example\neckguard\MainActivity.kt'
with open(path, 'r', encoding='utf-8-sig') as f:
    txt = f.read()

old = '''    val totalMs = dashState.totalMonitoredMs
    val healthyMs = dashState.healthyMs
    val slouchedMs = dashState.slouchedMs

    // Don't show if no data yet
    if (totalMs <= 0L) return

    val healthyPct = ((healthyMs.toFloat() / totalMs) * 100).toInt().coerceIn(0, 100)
    val slouchedPct = 100 - healthyPct'''

new = '''    val totalMs = dashState.totalMonitoredMs
    val healthyMs = dashState.healthyMs
    val slouchedMs = dashState.slouchedMs

    val hasData = totalMs > 0L
    val healthyPct = if (hasData) ((healthyMs.toFloat() / totalMs) * 100).toInt().coerceIn(0, 100) else 0
    val slouchedPct = if (hasData) 100 - healthyPct else 0'''

if old in txt:
    txt = txt.replace(old, new, 1)
    print("PATCH 1: removed early return")
else:
    print("PATCH 1 NOT FOUND")

# Now add an empty state inside the card body — wrap the stats section
old_bar = '''        Spacer(modifier = Modifier.height(14.dp))

        // Progress bar
        val animatedHealthy by'''

new_bar = '''        Spacer(modifier = Modifier.height(14.dp))

        if (!hasData) {
            // Empty state
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("\\uD83D\\uDCCA", fontSize = 28.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Monitoring is active! Your posture breakdown will appear here shortly.",
                    fontSize = 13.sp,
                    color = FinalMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            return
        }

        // Progress bar
        val animatedHealthy by'''

if old_bar in txt:
    txt = txt.replace(old_bar, new_bar, 1)
    print("PATCH 2: added empty state UI")
else:
    print("PATCH 2 NOT FOUND")

with open(path, 'w', encoding='utf-8-sig') as f:
    f.write(txt)
print(f"Done. File: {len(txt)} chars")
