package com.example.neckguard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.neckguard.ui.theme.*
import com.example.neckguard.FinalCoral
import com.example.neckguard.FinalBark
import com.example.neckguard.FinalBarkSoft
import com.example.neckguard.FinalMoss
import com.example.neckguard.FinalCream
import com.example.neckguard.FinalMist
import com.example.neckguard.FinalWhite
import com.example.neckguard.FinalMuted

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FinalCream)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.statusBarsPadding())
        
        // Header
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onBack() }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = FinalMuted)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back", style = MaterialTheme.typography.bodyLarge, color = FinalMuted)
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            Text("About NudgeUp", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = FinalBark)
            Spacer(modifier = Modifier.height(24.dp))
            
            // 1. Meet Your Buddy
            AboutSection("Meet Your Buddy \uD83D\uDC7E") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.example.neckguard.R.drawable.mascot_sad),
                        contentDescription = "Slouch Monster",
                        modifier = Modifier.size(80.dp).clip(RoundedCornerShape(8.dp))
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        "Say hi to the Slouch Monster! It represents those sneaky bad habits trying to ruin your posture. NudgeUp is here to help you defeat it.",
                        fontSize = 15.sp, color = FinalBarkSoft, lineHeight = 22.sp
                    )
                }
            }
            
            // 2. What is Text Neck?
            AboutSection("What is Text Neck? \uD83D\uDCF1") {
                BulletPoint("Looking down too long puts up to 27 kg (60 lbs) of force on your spine.")
                BulletPoint("Rounded shoulders compress your chest and affect your breathing.")
                BulletPoint("Bad screen habits drain your energy and tank your focus.")
            }
            
            // 3. How NudgeUp Helps You
            AboutSection("How NudgeUp Helps You ✨") {
                Row(modifier = Modifier.fillMaxWidth()) {
                    FeatureChip(Modifier.weight(1f), "Real-time\nDetection", "🔍")
                    Spacer(modifier = Modifier.width(12.dp))
                    FeatureChip(Modifier.weight(1f), "Smart\nReminders", "🔔")
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    FeatureChip(Modifier.weight(1f), "Progress\nTracking", "📈")
                    Spacer(modifier = Modifier.width(12.dp))
                    FeatureChip(Modifier.weight(1f), "Habit\nBuilding", "🧱")
                }
            }
            
            // 4. Good Posture Simply
            AboutSection("Good Posture Simply \uD83E\uDDD8") {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f).background(FinalCoral.copy(alpha=0.1f), RoundedCornerShape(12.dp)).padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = FinalCoral, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Bad Habits", fontWeight = FontWeight.Bold, color = FinalCoral, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("• Phone in lap\n• Hunching over\n• Chin tucked down", fontSize = 13.sp, color = FinalBark, lineHeight = 20.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f).background(FinalMoss.copy(alpha=0.15f), RoundedCornerShape(12.dp)).padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = FinalMoss, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Good Habits", fontWeight = FontWeight.Bold, color = FinalMoss, fontSize = 14.sp)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("• Phone at eye level\n• Shoulders back\n• Spine neutral", fontSize = 13.sp, color = FinalBark, lineHeight = 20.sp)
                    }
                }
            }
            
            // 5. Why It All Matters
            AboutSection("Why It All Matters \uD83C\uDF0D") {
                Text(
                    "Text neck affects over 1 billion people worldwide. Most don't even notice the damage until the pain starts.\n\nYou are already ahead just by being here and taking control of your health. Keep it up!",
                    fontSize = 15.sp, color = FinalBarkSoft, lineHeight = 22.sp
                )
            }
            
            Spacer(modifier = Modifier.height(60.dp).navigationBarsPadding())
        }
    }
}

@Composable
fun AboutSection(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
            .background(FinalWhite, RoundedCornerShape(16.dp))
            .border(1.dp, FinalMist, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = FinalBark)
        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
fun BulletPoint(text: String) {
    Row(modifier = Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .background(FinalMoss, CircleShape)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text, fontSize = 15.sp, color = FinalBarkSoft, lineHeight = 22.sp)
    }
}

@Composable
fun FeatureChip(modifier: Modifier = Modifier, text: String, emoji: String) {
    Row(
        modifier = modifier
            .background(FinalMist, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(10.dp))
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = FinalBark, lineHeight = 18.sp)
    }
}
