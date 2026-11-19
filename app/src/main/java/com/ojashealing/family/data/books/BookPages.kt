package com.ojashealing.family.data.books

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.LruCache
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/**
 * The pages of a downloaded PDF, drawn by Android's own PDF renderer. The renderer handles one
 * page at a time, so every use goes through one thread; drawn pages are kept for quick scrolling.
 */
class BookPages private constructor(private val pfd: ParcelFileDescriptor, private val renderer: PdfRenderer) : Closeable {
    private val thread = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val lock = Mutex()
    private val sizes = arrayOfNulls<Pair<Int, Int>>(renderer.pageCount)
    @Volatile private var closed = false

    // About 48 MB of drawn pages
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    val pageCount: Int = renderer.pageCount

    /** Width / height of [page], read once. */
    suspend fun aspect(page: Int): Float {
        sizes.getOrNull(page)?.let { return it.first.toFloat() / it.second }
        return onThread {
            renderer.openPage(page).use { p -> (p.width to p.height).also { sizes[page] = it } }
        }?.let { it.first.toFloat() / it.second } ?: (1f / 1.414f)
    }

    /** [page] drawn [widthPx] wide on white paper, or null once the book is closed. */
    suspend fun render(page: Int, widthPx: Int): Bitmap? {
        val w = widthPx.coerceIn(200, 2400)
        val key = "$page@$w"
        cache.get(key)?.let { return it }
        return onThread {
            renderer.openPage(page).use { p ->
                sizes[page] = p.width to p.height
                val h = (w.toLong() * p.height / p.width).toInt().coerceIn(1, 4000)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }?.also { cache.put(key, it) }
    }

    private suspend fun <T> onThread(block: () -> T): T? = withContext(thread) {
        lock.withLock { if (closed) null else runCatching(block).getOrNull() }
    }

    override fun close() {
        closed = true
        cache.evictAll()
        // Closed on the renderer's own thread, after any page being drawn
        thread.executor.execute {
            runCatching { renderer.close() }
            runCatching { pfd.close() }
        }
        thread.close()
    }

    companion object {
        /** Opens [file], or null when it is not a PDF the phone can read (e.g. password-protected). */
        fun open(file: File): BookPages? = runCatching {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try { BookPages(pfd, PdfRenderer(pfd)) } catch (t: Throwable) { pfd.close(); throw t }
        }.getOrNull()
    }
}

/** Splits page text into sentences to speak one after another (each well under the speech limit). */
fun sentencesOf(text: String): List<String> {
    // Lines on a page break mid-sentence; a blank line is a paragraph
    val flowing = text.replace(Regex("""-\n(?=\p{L})"""), "")
        .replace(Regex("""\n\s*\n+"""), " ")
        .replace('\n', ' ')
        .replace(Regex("""[ \t]+"""), " ").trim()
    if (flowing.isEmpty()) return emptyList()
    // A paragraph ends a sentence too; lines with no letters (page numbers) are not read
    val parts = Regex("""[^.!?।॥ ]+[.!?।॥]*""").findAll(flowing).map { it.value.trim() }
        .filter { s -> s.any { it.isLetter() } }.toList()
    return parts.flatMap { s -> if (s.length <= 1000) listOf(s) else s.chunked(1000) }
}

/** The voice language for [text]: Hindi for Devanagari script, Gujarati for Gujarati, else Indian English. */
fun speechLocaleFor(text: String): Locale {
    var deva = 0; var guj = 0; var latin = 0
    text.forEach { c ->
        when (c) {
            in 'ऀ'..'ॿ' -> deva++
            in '઀'..'૿' -> guj++
            in 'A'..'Z', in 'a'..'z' -> latin++
        }
    }
    return when {
        guj > deva && guj > latin -> Locale.forLanguageTag("gu-IN")
        deva > latin -> Locale.forLanguageTag("hi-IN")
        else -> Locale.forLanguageTag("en-IN")
    }
}

/** Reads the words on a drawn page with ML Kit on the phone (works offline; English and Hindi script). */
class PageTextReader {
    private val latin by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val devanagari by lazy { TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()) }

    suspend fun read(bitmap: Bitmap): String = coroutineScope {
        val image = InputImage.fromBitmap(bitmap, 0)
        val en = async { runCatching { latin.process(image).await().text }.getOrDefault("") }
        val hi = async { runCatching { devanagari.process(image).await().text }.getOrDefault("") }
        // The Devanagari reader also reads Latin letters; keep whichever found more
        val a = en.await().trim()
        val b = hi.await().trim()
        val deva = b.count { it in 'ऀ'..'ॿ' }
        if (deva > 20 || b.length > a.length) b else a
    }

    fun close() {
        runCatching { latin.close() }
        runCatching { devanagari.close() }
    }
}

/**
 * The phone's text-to-speech voice reading sentences in turn. [onFinished] runs (on the main
 * thread) when the last sentence of a [speak] call has been read, not when [stop] cut it short.
 */
class BookSpeaker(context: Context) {
    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private val _ready = MutableStateFlow<Boolean?>(null)
    /** Null while the voice starts, false when the phone has no voice. */
    val ready: StateFlow<Boolean?> = _ready.asStateFlow()
    private val _sentence = MutableStateFlow("")
    /** The sentence being read now. */
    val sentence: StateFlow<String> = _sentence.asStateFlow()

    private var batch = 0
    private var parts: List<String> = emptyList()
    private var onFinished: (() -> Unit)? = null

    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        _ready.value = status == TextToSpeech.SUCCESS
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String) {
                val (b, i) = parse(id) ?: return
                main.post { if (b == batch) _sentence.value = parts.getOrElse(i) { "" } }
            }
            override fun onDone(id: String) {
                val (b, i) = parse(id) ?: return
                main.post { if (b == batch && i == parts.lastIndex) onFinished?.also { onFinished = null }?.invoke() }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String) = onDone(id)
            override fun onError(id: String, errorCode: Int) = onDone(id)
        })
    }

    private fun parse(id: String): Pair<Int, Int>? {
        val (b, i) = id.split(':').takeIf { it.size == 2 } ?: return null
        return (b.toIntOrNull() ?: return null) to (i.toIntOrNull() ?: return null)
    }

    /** Reads [sentences] at [rate] (1 = normal) in the voice for their language. */
    fun speak(sentences: List<String>, rate: Float, onFinished: () -> Unit) {
        stop()
        if (sentences.isEmpty()) { onFinished(); return }
        val locale = speechLocaleFor(sentences.joinToString(" ").take(2000))
        val have = runCatching { tts.isLanguageAvailable(locale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        runCatching { tts.language = if (have >= TextToSpeech.LANG_AVAILABLE) locale else Locale.getDefault() }
        tts.setSpeechRate(rate)
        batch++
        parts = sentences
        this.onFinished = onFinished
        sentences.forEachIndexed { i, s -> tts.speak(s, TextToSpeech.QUEUE_ADD, null, "$batch:$i") }
    }

    fun stop() {
        batch++
        onFinished = null
        _sentence.value = ""
        runCatching { tts.stop() }
    }

    fun shutdown() {
        stop()
        runCatching { tts.shutdown() }
    }
}
