package com.alhmeemzool.quickocr

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Build
import android.view.accessibility.AccessibilityEvent

/** Detects only the system three-finger swipe-down gesture. */
class ThreeFingerGestureService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onGesture(gestureId: Int): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            gestureId == GESTURE_3_FINGER_SWIPE_DOWN
        ) {
            startActivity(Intent(this, ScanActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
            })
            return true
        }
        return super.onGesture(gestureId)
    }
}
