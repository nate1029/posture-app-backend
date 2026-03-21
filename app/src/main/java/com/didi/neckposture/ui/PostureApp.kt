package com.didi.neckposture.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable

@Composable
fun PostureApp() {
    MaterialTheme {
        Surface {
            PostureSessionScreen()
        }
    }
}
