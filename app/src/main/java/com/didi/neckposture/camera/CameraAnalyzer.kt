package com.didi.neckposture.camera

import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.didi.neckposture.domain.HeadPoseSample
import com.didi.neckposture.pose.HeadPoseEstimator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class CameraAnalyzer(
    private val estimator: HeadPoseEstimator,
    private val onSample: (HeadPoseSample?) -> Unit,
) : ImageAnalysis.Analyzer {

    private val scope = CoroutineScope(Dispatchers.Default)
    private val busy = AtomicBoolean(false)

    override fun analyze(image: ImageProxy) {
        if (!busy.compareAndSet(false, true)) {
            image.close()
            return
        }

        scope.launch {
            try {
                val sample = runCatching {
                    estimator.estimate(image, SystemClock.elapsedRealtime())
                }.getOrNull()
                onSample(sample)
            } finally {
                image.close()
                busy.set(false)
            }
        }
    }
}
