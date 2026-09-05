package com.alhmeemzool.quickocr

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

object ClipboardOutput {
    fun copy(context: Context, number: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("QuickOCR", number))
    }
}
