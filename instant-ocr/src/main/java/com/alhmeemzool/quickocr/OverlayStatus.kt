package com.alhmeemzool.quickocr

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/** Small, non-intrusive green status pill shown only while QuickOCR is active. */
class OverlayStatus(private val context: Context) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: TextView? = null

    fun show() {
        if (view != null || !android.provider.Settings.canDrawOverlays(context)) return
        val label = TextView(context).apply {
            text = "● OCR"
            setTextColor(Color.WHITE)
            textSize = 10f
            gravity = Gravity.CENTER
            setPadding(10, 4, 10, 4)
            background = GradientDrawable().apply {
                setColor(Color.rgb(35, 160, 80))
                cornerRadius = 40f
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (android.os.Build.VERSION.SDK_INT >= 26)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            android.graphics.PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 12
            y = 48
        }
        view = label
        windowManager.addView(label, params)
    }

    fun hide() {
        view?.let {
            runCatching { windowManager.removeView(it) }
            view = null
        }
    }
}
