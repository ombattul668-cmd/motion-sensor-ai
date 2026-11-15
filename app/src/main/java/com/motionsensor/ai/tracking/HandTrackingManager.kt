package com.motionsensor.ai.tracking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

/**
 * Wraps MediaPipe Hand Landmarker in LIVE_STREAM mode.
 * detectAsync() is non-blocking; results arrive on MediaPipe's thread via [Listener].
 */
class HandTrackingManager(
    context: Context,
    private val listener: Listener
) {

    interface Listener {
        fun onHandResult(result: HandTrackingResult)
        fun onTrackingError(message: String)
    }

    private val lock = Any()
    private var landmarker: HandLandmarker? = null
    private var lastTimestampMs = -1L

    val isReady: Boolean
        get() = synchronized(lock) { landmarker != null }

    init {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath(MODEL_ASSET)
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(1) // one hand for stage 1; raise later if performance allows
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result, input -> handleResult(result, input) }
                .setErrorListener { e ->
                    Log.e(TAG, "MediaPipe error", e)
                    listener.onTrackingError("Tracking error: ${e.message}")
                }
                .build()

            landmarker = HandLandmarker.createFromOptions(context, options)
        } catch (e: Exception) {
            Log.e(TAG, "Model load failed", e)
            listener.onTrackingError(
                "Model not found. Put $MODEL_ASSET in app/src/main/assets/ (see README)."
            )
        }
    }

    /** Submits one upright frame. Frames with non-increasing timestamps are dropped. */
    fun detectAsync(bitmap: Bitmap, timestampMs: Long) {
        synchronized(lock) {
            val lm = landmarker ?: return
            if (timestampMs <= lastTimestampMs) return
            lastTimestampMs = timestampMs
            try {
                lm.detectAsync(BitmapImageBuilder(bitmap).build(), timestampMs)
            } catch (e: Exception) {
                Log.w(TAG, "detectAsync failed", e)
            }
        }
    }

    private fun handleResult(result: HandLandmarkerResult, input: MPImage) {
        val hands = result.landmarks().mapIndexed { index, landmarks ->
            val category = result.handedness().getOrNull(index)?.firstOrNull()
            HandData(
                points = landmarks.map { PointF(it.x(), it.y()) },
                handedness = category?.categoryName() ?: "Unknown",
                score = category?.score() ?: 0f
            )
        }
        listener.onHandResult(HandTrackingResult(hands, input.width, input.height))
    }

    fun close() {
        synchronized(lock) {
            landmarker?.close()
            landmarker = null
        }
    }

    private companion object {
        const val TAG = "HandTrackingManager"
        const val MODEL_ASSET = "hand_landmarker.task"
    }
}
