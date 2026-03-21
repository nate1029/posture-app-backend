package com.didi.neckposture.feedback

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import com.didi.neckposture.domain.PostureState

class FeedbackController(context: Context) {
    private val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    private val toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80)
    private var lastFeedbackMs: Long = 0L

    fun triggerIfNeeded(
        state: PostureState,
        hapticEnabled: Boolean,
        soundEnabled: Boolean,
        cooldownMs: Long = 25_000L,
    ) {
        if (state != PostureState.BAD && state != PostureState.WARNING) return
        val now = SystemClock.elapsedRealtime()
        if ((now - lastFeedbackMs) < cooldownMs) return
        lastFeedbackMs = now

        if (hapticEnabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(250L, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(250L)
            }
        }
        if (soundEnabled) {
            toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP2, 200)
        }
    }
}
