package com.motionsensor.ai.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Owns the CameraX session: a live preview for the user and a
 * low-resolution RGBA analysis stream for MediaPipe.
 */
class CameraManager(
    private val context: Context,
    private val frameListener: FrameListener
) {

    fun interface FrameListener {
        /** Called on the analysis thread with an upright (rotated) bitmap. */
        fun onFrame(bitmap: Bitmap, timestampMs: Long)
    }

    private var provider: ProcessCameraProvider? = null
    private var analysisExecutor: ExecutorService? = null

    var lensFacing: Int = CameraSelector.LENS_FACING_FRONT
        private set

    val isFrontCamera: Boolean
        get() = lensFacing == CameraSelector.LENS_FACING_FRONT

    fun start(owner: LifecycleOwner, previewView: PreviewView, onError: (Exception) -> Unit) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            try {
                val cameraProvider = future.get()
                provider = cameraProvider
                bind(cameraProvider, owner, previewView)
            } catch (e: Exception) {
                Log.e(TAG, "Camera start failed", e)
                onError(e)
            }
        }, ContextCompat.getMainExecutor(context))
    }

    private fun bind(
        cameraProvider: ProcessCameraProvider,
        owner: LifecycleOwner,
        previewView: PreviewView
    ) {
        cameraProvider.unbindAll()

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val executor = Executors.newSingleThreadExecutor()
        analysisExecutor = executor

        // 640x480 keeps MediaPipe fast on Snapdragon 665 class devices.
        // KEEP_ONLY_LATEST drops stale frames instead of building a queue.
        val analysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        analysis.setAnalyzer(executor) { proxy -> analyze(proxy) }

        val selector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()

        cameraProvider.bindToLifecycle(owner, selector, preview, analysis)
    }

    private fun analyze(proxy: ImageProxy) {
        try {
            val raw = proxy.toBitmap()
            val upright = rotate(raw, proxy.imageInfo.rotationDegrees)
            // imageInfo.timestamp is in nanoseconds; MediaPipe wants ms.
            frameListener.onFrame(upright, proxy.imageInfo.timestamp / 1_000_000L)
        } catch (e: Exception) {
            Log.w(TAG, "Frame conversion failed", e)
        } finally {
            proxy.close()
        }
    }

    private fun rotate(source: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return source
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    fun stop() {
        provider?.unbindAll()
        provider = null
        analysisExecutor?.shutdownNow()
        analysisExecutor = null
    }

    private companion object {
        const val TAG = "CameraManager"
    }
}
