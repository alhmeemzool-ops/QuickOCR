package com.alhmeemzool.quickocr

import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

object CameraAnalyzer {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val busy = AtomicBoolean(false)

    fun process(proxy: ImageProxy, callback: (String?) -> Unit) {
        if (!busy.compareAndSet(false, true)) { proxy.close(); return }
        val mediaImage = proxy.image
        if (mediaImage == null) { busy.set(false); proxy.close(); callback(null); return }
        val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                val number = Regex("[0-9٠-٩]{6,30}")
                    .findAll(result.text)
                    .map { normalize(it.value) }
                    .maxByOrNull { it.length }
                callback(number)
            }
            .addOnFailureListener { callback(null) }
            .addOnCompleteListener {
                busy.set(false)
                proxy.close()
            }
    }

    private fun normalize(value: String): String = buildString(value.length) {
        value.forEach { c ->
            append(if (c in '٠'..'٩') ('0'.code + (c.code - '٠'.code)).toChar() else c)
        }
    }
}
