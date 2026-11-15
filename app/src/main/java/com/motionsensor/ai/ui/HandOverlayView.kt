package com.motionsensor.ai.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.motionsensor.ai.tracking.HandData
import kotlin.math.max

/**
 * Draws the 21 hand landmarks and their connections over the camera preview.
 * Maps normalized coordinates using the same FILL_CENTER crop that PreviewView uses.
 */
class HandOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var mirrored: Boolean = false

    private var hands: List<HandData> = emptyList()
    private var frameWidth = 0
    private var frameHeight = 0

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = NEON_BLUE
        strokeWidth = 4f
        style = Paint.Style.STROKE
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = TEXT_PRIMARY
        style = Paint.Style.FILL
    }

    fun update(newHands: List<HandData>, width: Int, height: Int) {
        hands = newHands
        frameWidth = width
        frameHeight = height
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (hands.isEmpty() || frameWidth == 0 || frameHeight == 0) return

        val viewW = width.toFloat()
        val viewH = height.toFloat()
        val fw = frameWidth.toFloat()
        val fh = frameHeight.toFloat()

        val scale = max(viewW / fw, viewH / fh)
        val offsetX = (viewW - fw * scale) / 2f
        val offsetY = (viewH - fh * scale) / 2f

        fun mapX(x: Float): Float = offsetX + (if (mirrored) 1f - x else x) * fw * scale
        fun mapY(y: Float): Float = offsetY + y * fh * scale

        for (hand in hands) {
            val p = hand.points
            if (p.size < LANDMARK_COUNT) continue

            for ((a, b) in CONNECTIONS) {
                canvas.drawLine(mapX(p[a].x), mapY(p[a].y), mapX(p[b].x), mapY(p[b].y), linePaint)
            }
            for (point in p) {
                canvas.drawCircle(mapX(point.x), mapY(point.y), 6f, pointPaint)
            }
        }
    }

    private companion object {
        const val LANDMARK_COUNT = 21
        const val NEON_BLUE = 0xFF00E5FF.toInt()
        const val TEXT_PRIMARY = 0xFFE0F7FF.toInt()

        // MediaPipe hand skeleton connections.
        val CONNECTIONS = listOf(
            0 to 1, 1 to 2, 2 to 3, 3 to 4,
            0 to 5, 5 to 6, 6 to 7, 7 to 8,
            5 to 9, 9 to 10, 10 to 11, 11 to 12,
            9 to 13, 13 to 14, 14 to 15, 15 to 16,
            13 to 17, 0 to 17, 17 to 18, 18 to 19, 19 to 20
        )
    }
}
