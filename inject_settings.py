import sys

main_path = r'c:\Users\Naiteek\Downloads\postureapp\didi project\NeckGuardApp\app\src\main\java\com\example\neckguard\MainActivity.kt'
with open(main_path, 'r', encoding='utf-8') as f:
    text = f.read()

# Update RewardsTab signature
old_rewards_sig = 'fun RewardsTab(viewModel: MainViewModel) {'
new_rewards_sig = 'fun RewardsTab(viewModel: MainViewModel, onSettingsClick: () -> Unit) {'
text = text.replace(old_rewards_sig, new_rewards_sig)

# Update RewardsTab call
old_rewards_call = 'RewardsTab(viewModel)'
new_rewards_call = 'RewardsTab(viewModel, onSettingsClick = { currentTab = "Settings" })'
text = text.replace(old_rewards_call, new_rewards_call)

# Update icon
old_person_box = 'Box(modifier = Modifier.size(36.dp).background(FinalEarthPale, CircleShape).border(2.dp, FinalEarthPale, CircleShape), contentAlignment = Alignment.Center) { Text("👤", fontSize = 15.sp) }'
new_person_box = 'Box(modifier = Modifier.size(36.dp).background(FinalEarthPale, CircleShape).border(2.dp, FinalEarthPale, CircleShape).clickable { onSettingsClick() }, contentAlignment = Alignment.Center) { Text("⚙", fontSize = 15.sp) }'
text = text.replace(old_person_box, new_person_box)


settings_placeholder = 'Text("Settings coming soon", color = FinalBark)'
settings_real = """                            SettingsComponent(
                                selectedInterval = selectedInterval,
                                onIntervalChange = { ms -> selectedInterval = ms; prefs.edit().putLong("IntervalPreferenceMs", ms).apply() },
                                hasCameraPerm = hasCameraPerm,
                                hasNotifPerm = hasNotifPerm,
                                hasActivityPerm = hasActivityPerm,
                                isIgnoringBattery = isIgnoringBatteryOptimizations,
                                onFixPerm = { perm -> multiplePermissionsLauncher.launch(arrayOf(perm)) },
                                onFixBattery = { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply { data = android.net.Uri.parse("package:${context.packageName}") }) },
                                onLogout = {
                                    val serviceIntent = android.content.Intent(context, com.example.neckguard.service.NeckGuardService::class.java)
                                    context.stopService(serviceIntent)
                                    viewModel.setAppActive(false)
                                    viewModel.logout()
                                }
                            )"""
text = text.replace(settings_placeholder, settings_real)

settings_cmp = """
@Composable
fun SettingsComponent(
    selectedInterval: Long, onIntervalChange: (Long) -> Unit,
    hasCameraPerm: Boolean, hasNotifPerm: Boolean, hasActivityPerm: Boolean, isIgnoringBattery: Boolean,
    onFixPerm: (String) -> Unit, onFixBattery: () -> Unit, onLogout: () -> Unit
) {
    Column {
        androidx.compose.material3.Text("App Settings", fontSize = 24.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = FinalBark)
        Spacer(modifier = Modifier.height(24.dp))
        
        Column(modifier = Modifier.fillMaxWidth().background(FinalWhite, RoundedCornerShape(14.dp)).border(1.dp, FinalMist, RoundedCornerShape(14.dp)).padding(16.dp)) {
            androidx.compose.material3.Text("Check Interval", fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalBark)
            androidx.compose.material3.Text("Sets how long you must be reading before posture is evaluated.", fontSize = 12.sp, color = FinalMuted)
            Spacer(modifier = Modifier.height(12.dp))
            val options = listOf(15_000L to "15 Seconds (Testing)", 15 * 60 * 1000L to "15 Minutes", 30 * 60 * 1000L to "30 Minutes")
            options.forEach { (ms, label) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(selected = (selectedInterval == ms), onClick = { onIntervalChange(ms) }, colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = FinalMoss))
                    androidx.compose.material3.Text(label, fontSize = 14.sp, color = FinalBark)
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.fillMaxWidth().background(FinalSagePale, RoundedCornerShape(14.dp)).border(1.dp, FinalSageLight, RoundedCornerShape(14.dp)).padding(16.dp)) {
            androidx.compose.material3.Text("System Requirements", fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalMoss)
            Spacer(modifier = Modifier.height(12.dp))
            if (!hasNotifPerm) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { androidx.compose.material3.Text("Notifications", color = FinalBark); androidx.compose.material3.Button(onClick = { if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) onFixPerm(android.Manifest.permission.POST_NOTIFICATIONS) }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = FinalMoss)) { androidx.compose.material3.Text("Fix") } }; Spacer(modifier = Modifier.height(8.dp)) }
            if (!hasCameraPerm) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { androidx.compose.material3.Text("Camera Sensor", color = FinalBark); androidx.compose.material3.Button(onClick = { onFixPerm(android.Manifest.permission.CAMERA) }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = FinalMoss)) { androidx.compose.material3.Text("Fix") } }; Spacer(modifier = Modifier.height(8.dp)) }
            if (!hasActivityPerm) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { androidx.compose.material3.Text("Activity Sensor", color = FinalBark); androidx.compose.material3.Button(onClick = { if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) onFixPerm(android.Manifest.permission.ACTIVITY_RECOGNITION) }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = FinalMoss)) { androidx.compose.material3.Text("Fix") } }; Spacer(modifier = Modifier.height(8.dp)) }
            if (!isIgnoringBattery) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { androidx.compose.material3.Text("Background Power", color = FinalBark); androidx.compose.material3.Button(onClick = { onFixBattery() }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = FinalMoss)) { androidx.compose.material3.Text("Fix") } } }
            if (hasCameraPerm && hasNotifPerm && hasActivityPerm && isIgnoringBattery) { androidx.compose.material3.Text("All systems nominal. Ready to protect your neck!", fontSize = 12.sp, color = FinalMoss) }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Column(modifier = Modifier.fillMaxWidth().background(FinalCoral.copy(alpha=0.1f), RoundedCornerShape(14.dp)).border(1.dp, FinalCoral.copy(alpha=0.3f), RoundedCornerShape(14.dp)).padding(16.dp)) {
            androidx.compose.material3.Text("Account", fontSize = 16.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = FinalCoral)
            androidx.compose.material3.Text("Sign out of the application and reset local preferences.", fontSize = 12.sp, color = FinalBark)
            Spacer(modifier = Modifier.height(12.dp))
            androidx.compose.material3.Button(onClick = onLogout, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = FinalCoral)) {
                androidx.compose.material3.Text("Log Out", color = FinalWhite)
            }
        }
    }
}
"""
if 'fun SettingsComponent' not in text:
    text += settings_cmp

with open(main_path, 'w', encoding='utf-8') as f:
    f.write(text)

print("Injected settings")
