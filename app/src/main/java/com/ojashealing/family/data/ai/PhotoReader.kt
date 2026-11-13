package com.ojashealing.family.data.ai

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await

/**
 * Reads a photo on the phone with ML Kit, so the picture itself never leaves the phone:
 * only the words in it (English and Hindi script) and a few labels for what it shows are sent.
 */
class PhotoReader(context: Context) {
    private val app = context.applicationContext
    private val latin by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val devanagari by lazy { TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()) }
    private val labeler by lazy { ImageLabeling.getClient(ImageLabelerOptions.Builder().setConfidenceThreshold(0.7f).build()) }

    suspend fun read(uri: Uri): PhotoNote = read(InputImage.fromFilePath(app, uri))

    suspend fun read(bitmap: Bitmap): PhotoNote = read(InputImage.fromBitmap(bitmap, 0))

    private suspend fun read(image: InputImage): PhotoNote = coroutineScope {
        val en = async { runCatching { latin.process(image).await().text }.getOrDefault("") }
        val hi = async { runCatching { devanagari.process(image).await().text }.getOrDefault("") }
        val labels = async {
            runCatching { labeler.process(image).await().sortedByDescending { it.confidence }.take(5).map { it.text } }.getOrDefault(emptyList())
        }
        // The Devanagari reader also reads Latin letters; keep whichever found more
        val a = en.await().trim()
        val b = hi.await().trim()
        PhotoNote(text = (if (b.length > a.length) b else a).take(800), labels = labels.await())
    }
}
