package com.alhmeemzool.quickocr

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat

/** Visible, short-lived camera screen started by the three-finger gesture. */
class ScanActivity : ComponentActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private var provider: ProcessCameraProvider? = null
    private var analysis: ImageAnalysis? = null
    private var finished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            finish()
            return
        }

        val root = FrameLayout(this)
        val preview = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
        root.addView(preview, FrameLayout.LayoutParams(-1, -1))

        val label = TextView(this).apply {
            text = "وجّه الكاميرا إلى الرقم"
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(24, 14, 24, 14)
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundColor(0x99000000.toInt())
        }
        root.addView(label, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        setContentView(root)

        startCamera(preview)
        handler.postDelayed({ finishScan() }, 3000L)
    }

    private fun startCamera(preview: PreviewView) {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            if (finished) return@addListener
            try {
                provider = future.get()
                val cameraPreview = Preview.Builder().build().also {
                    it.setSurfaceProvider(preview.surfaceProvider)
                }
                analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setImageQueueDepth(1)
                    .build()

                analysis?.setAnalyzer(ContextCompat.getMainExecutor(this)) { image ->
                    CameraAnalyzer.process(image) { number ->
                        if (finished) return@process
                        if (number != null) {
                            ClipboardOutput.copy(this, number)
                            finishScan()
                        }
                    }
                }

                provider?.unbindAll()
                provider?.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, cameraPreview, analysis)
            } catch (_: Exception) {
                finishScan()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun finishScan() {
        if (finished) return
        finished = true
        handler.removeCallbacksAndMessages(null)
        analysis?.clearAnalyzer()
        provider?.unbindAll()
        analysis = null
        provider = null
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        analysis?.clearAnalyzer()
        provider?.unbindAll()
        super.onDestroy()
    }
}
