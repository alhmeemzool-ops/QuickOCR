package com.alhmeemzool.quickocr

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
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
import kotlin.math.sqrt

class ShakeDetectorService : Service(), SensorEventListener, LifecycleOwner {
    private lateinit var sensorManager: SensorManager
    private lateinit var lifecycleRegistry: LifecycleRegistry
    private lateinit var overlayStatus: OverlayStatus
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastTrigger = 0L
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

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            // Normal rate is enough for shake detection and avoids the unnecessary
            // power cost of SENSOR_DELAY_GAME.
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val g = sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH
        val now = System.currentTimeMillis()

        if (!scanInProgress && g >= 2.7f && now - lastTrigger >= 1500L) {
            lastTrigger = now
            captureAndAnalyze()
        }
    }

    private fun captureAndAnalyze() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            fail()
            return
        }

        scanInProgress = true
        val providerFuture = ProcessCameraProvider.getInstance(this)

        providerFuture.addListener({
            try {
                val provider = providerFuture.get()
                cameraProvider = provider
                analysis?.clearAnalyzer()

                analysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setImageQueueDepth(1)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()

                analysis!!.setAnalyzer(cameraExecutor) { proxy ->
                    CameraAnalyzer.process(proxy) { number ->
                        if (!scanInProgress) return@process
                        finishScan()
                        if (number != null) ClipboardOutput.copy(this, number) else fail()
                    }
                }

                provider.unbindAll()
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, analysis)

                // Hard limit: camera/OCR work is never allowed to stay active
                // beyond 3 seconds after a shake.
                mainHandler.postDelayed({
                    if (scanInProgress) {
                        finishScan()
                        fail()
                    }
                }, SCAN_TIMEOUT_MS)
            } catch (_: Exception) {
                finishScan()
                fail()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun finishScan() {
        analysis?.clearAnalyzer()
        cameraProvider?.unbindAll()
        mainHandler.removeCallbacksAndMessages(null)
        scanInProgress = false
    }

    private fun fail() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= 26) {
            vibrator.vibrate(VibrationEffect.createOneShot(80, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(80)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel("quickocr", "QuickOCR", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun notification(): Notification = NotificationCompat.Builder(this, "quickocr")
        .setContentTitle("QuickOCR active")
        .setContentText("Shake to scan numbers")
        .setSmallIcon(android.R.drawable.ic_menu_camera)
        .setOngoing(true)
        .build()

    override fun onDestroy() {
        runCatching { overlayStatus.hide() }
        sensorManager.unregisterListener(this)
        analysis?.clearAnalyzer()
        cameraProvider?.unbindAll()
        mainHandler.removeCallbacksAndMessages(null)
        cameraExecutor.shutdownNow()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    companion object {
        private const val SCAN_TIMEOUT_MS = 3000L
    }
}
