package com.alhmeemzool.quickocr

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import java.util.concurrent.Executors

/** Short-lived camera/OCR worker. It is started only after the three-finger gesture. */
class ShakeDetectorService : Service(), LifecycleOwner {
    private lateinit var lifecycleRegistry: LifecycleRegistry
    private lateinit var overlayStatus: OverlayStatus
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var analysis: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var scanInProgress = false

    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override fun onCreate() {
        super.onCreate()
        lifecycleRegistry = LifecycleRegistry(this).also {
            it.currentState = Lifecycle.State.CREATED
            it.currentState = Lifecycle.State.STARTED
        }
        createChannel()
        startForeground(1001, notification())
        overlayStatus = OverlayStatus(this)
        overlayStatus.show()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_SCAN && !scanInProgress) {
            captureAndAnalyze()
        } else if (intent?.action != ACTION_SCAN) {
            stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    private fun captureAndAnalyze() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            finishScan(true)
            return
        }

        scanInProgress = true
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider

                analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setImageQueueDepth(1)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()

                analysis!!.setAnalyzer(cameraExecutor) { proxy ->
                    CameraAnalyzer.process(proxy) { number ->
                        if (!scanInProgress) return@process
                        if (number != null) {
                            ClipboardOutput.copy(this, number)
                            finishScan(false)
                        } else {
                            finishScan(true)
                        }
                    }
                }

                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, analysis)

                mainHandler.postDelayed({
                    if (scanInProgress) finishScan(true)
                }, SCAN_TIMEOUT_MS)
            } catch (_: Exception) {
                finishScan(true)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun finishScan(failed: Boolean) {
        if (!scanInProgress && !failed) return
        scanInProgress = false
        analysis?.clearAnalyzer()
        cameraProvider?.unbindAll()
        analysis = null
        cameraProvider = null
        mainHandler.removeCallbacksAndMessages(null)
        if (failed) vibrateFailure()
        if (::overlayStatus.isInitialized) overlayStatus.hide()
        stopSelf()
    }

    private fun vibrateFailure() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(80)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel("quickocr", "QuickOCR", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun notification(): Notification = NotificationCompat.Builder(this, "quickocr")
        .setContentTitle("QuickOCR")
        .setContentText("Scanning numbers…")
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .setOngoing(true)
        .build()

    override fun onDestroy() {
        if (::overlayStatus.isInitialized) overlayStatus.hide()
        analysis?.clearAnalyzer()
        cameraProvider?.unbindAll()
        mainHandler.removeCallbacksAndMessages(null)
        cameraExecutor.shutdownNow()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    companion object {
        const val ACTION_SCAN = "com.alhmeemzool.quickocr.action.SCAN"
        private const val SCAN_TIMEOUT_MS = 3000L
    }
}
