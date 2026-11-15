package com.motionsensor.ai

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.motionsensor.ai.camera.CameraManager
import com.motionsensor.ai.databinding.ActivityMainBinding
import com.motionsensor.ai.tracking.HandTrackingManager
import com.motionsensor.ai.tracking.HandTrackingResult

class MainActivity : AppCompatActivity(), CameraManager.FrameListener, HandTrackingManager.Listener {

    private lateinit var binding: ActivityMainBinding
    private var cameraManager: CameraManager? = null
    private var handTracking: HandTrackingManager? = null
    private var isRunning = false

    // Simple FPS counter for the on-screen readout.
    private var fpsWindowStart = 0L
    private var fpsFrames = 0

    private val cameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) startTracking()
            else showStatus(getString(R.string.status_permission_denied))
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.startButton.setOnClickListener {
            if (isRunning) stopTracking() else requestCameraAndStart()
        }
        showStatus(getString(R.string.status_standby))
    }

    private fun requestCameraAndStart() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) startTracking() else cameraPermission.launch(Manifest.permission.CAMERA)
    }

    private fun startTracking() {
        if (isRunning) return

        val tracking = HandTrackingManager(this, this)
        if (!tracking.isReady) {
            // The error reason has already been posted by the listener.
            tracking.close()
            return
        }
        handTracking = tracking

        val camera = CameraManager(this, this)
        cameraManager = camera
        binding.handOverlay.mirrored = camera.isFrontCamera

        camera.start(this, binding.previewView) { error ->
            runOnUiThread {
                showStatus(getString(R.string.status_camera_error, error.message ?: "unknown"))
                stopTracking()
            }
        }

        isRunning = true
        fpsWindowStart = SystemClock.elapsedRealtime()
        fpsFrames = 0
        binding.startButton.text = getString(R.string.stop)
        showStatus(getString(R.string.status_scanning))
    }

    private fun stopTracking() {
        isRunning = false
        cameraManager?.stop()
        cameraManager = null
        handTracking?.close()
        handTracking = null

        binding.handOverlay.update(emptyList(), 0, 0)
        binding.handStatusText.text = getString(R.string.hand_not_detected)
        binding.fpsText.text = ""
        binding.startButton.text = getString(R.string.start)
        showStatus(getString(R.string.status_standby))
    }

    // Called on the camera analysis thread.
    override fun onFrame(bitmap: Bitmap, timestampMs: Long) {
        handTracking?.detectAsync(bitmap, timestampMs)
    }

    // Called on the MediaPipe result thread.
    override fun onHandResult(result: HandTrackingResult) {
        runOnUiThread {
            if (!isRunning) return@runOnUiThread
            binding.handOverlay.update(result.hands, result.frameWidth, result.frameHeight)

            val first = result.hands.firstOrNull()
            binding.handStatusText.text = if (first == null) {
                getString(R.string.hand_not_detected)
            } else {
                getString(
                    R.string.hand_detected,
                    result.hands.size,
                    first.handedness,
                    (first.score * 100).toInt()
                )
            }
            updateFps()
        }
    }

    override fun onTrackingError(message: String) {
        runOnUiThread { showStatus(message) }
    }

    private fun updateFps() {
        fpsFrames++
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - fpsWindowStart
        if (elapsed >= 1000L) {
            val fps = fpsFrames * 1000L / elapsed
            binding.fpsText.text = getString(R.string.fps_format, fps)
            fpsWindowStart = now
            fpsFrames = 0
        }
    }

    private fun showStatus(text: String) {
        binding.statusText.text = text
    }

    override fun onPause() {
        super.onPause()
        // Android 10+ does not allow camera use from a background activity,
        // so stop cleanly when the user leaves this screen.
        if (isRunning) {
            stopTracking()
            showStatus(getString(R.string.status_paused))
        }
    }

    override fun onDestroy() {
        if (isRunning) stopTracking()
        super.onDestroy()
    }
}
