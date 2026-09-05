package com.alhmeemzool.quickocr

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/** Detects the system-level three-finger swipe-down gesture.
 * No screen content is read or stored.
 */
class ThreeFingerGestureService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onGesture(gestureId: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            gestureId == GESTURE_3_FINGER_SWIPE_DOWN
        ) {
            startOcrScan()
            return true
        }
        return super.onGesture(gestureId)
    }

    private fun startOcrScan() {
        val intent = android.content.Intent(this, ShakeDetectorService::class.java).apply {
            action = ShakeDetectorService.ACTION_SCAN
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
