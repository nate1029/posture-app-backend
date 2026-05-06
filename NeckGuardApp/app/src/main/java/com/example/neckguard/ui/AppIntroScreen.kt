package com.example.neckguard.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.neckguard.R

@Composable
fun AppIntroScreen(onFinish: () -> Unit) {
    var step by remember { mutableStateOf(0) }

    val scrollState = androidx.compose.foundation.rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(FinalCream)
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))

        if (step == 0) {
            Text(
                "Hey!\nI'm your posture\nbuddy \uD83D\uDC4B",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = FinalBark,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp
            )
            Spacer(modifier = Modifier.height(32.dp))
            Image(
                painter = painterResource(id = R.drawable.mascot_welcome),
                contentDescription = "Welcome Mascot",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "I'll help you fix your phone\nposture, reduce pain, and build\nhealthy habits—without stress.",
                fontSize = 15.sp,
                color = FinalBarkSoft,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
        } else {
            Text(
                "What is\nText Neck?",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = FinalMoss,
                textAlign = TextAlign.Center,
                lineHeight = 36.sp
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Looking down at your phone\nfor too long can cause stress\non your neck and spine.",
                fontSize = 15.sp,
                color = FinalBarkSoft,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
            Spacer(modifier = Modifier.height(32.dp))
            Image(
                painter = painterResource(id = R.drawable.mascot_sad),
                contentDescription = "Sad Mascot",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(250.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            
            // Symptoms List
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                SymptomRow("⚡", "Neck pain")
                SymptomRow("\uD83E\uDD2F", "Headaches")
                SymptomRow("\uD83D\uDCAA", "Rounded shoulders")
                SymptomRow("\uD83D\uDD0B", "Low energy")
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        
        // Pager Indicators
        Row(horizontalArrangement = Arrangement.Center) {
            Box(modifier = Modifier.size(8.dp).background(if (step == 0) FinalMoss else FinalMist, androidx.compose.foundation.shape.CircleShape))
            Spacer(modifier = Modifier.width(8.dp))
            Box(modifier = Modifier.size(8.dp).background(if (step == 1) FinalMoss else FinalMist, androidx.compose.foundation.shape.CircleShape))
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (step == 0) step = 1 else onFinish()
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = FinalMoss),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(if (step == 0) "Next" else "Got it", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = FinalWhite)
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun SymptomRow(icon: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.size(40.dp).border(1.dp, FinalMist, androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(icon, fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = FinalBark)
    }
}
