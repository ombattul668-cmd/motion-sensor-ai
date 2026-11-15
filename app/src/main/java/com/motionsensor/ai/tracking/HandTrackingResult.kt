package com.motionsensor.ai.tracking

import android.graphics.PointF

/** One detected hand. Points are normalized (0..1) in upright camera-frame coordinates. */
data class HandData(
    val points: List<PointF>,
    val handedness: String,
    val score: Float
)

data class HandTrackingResult(
    val hands: List<HandData>,
    val frameWidth: Int,
    val frameHeight: Int
)
