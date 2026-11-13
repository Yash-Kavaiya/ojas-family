package com.ojashealing.family.data.ai

import android.app.ActivityManager
import android.content.Context
import com.google.firebase.storage.FirebaseStorage
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceSession
import com.google.mediapipe.tasks.genai.llminference.PromptTemplates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

/** Where the phone's copy of the Gemma model stands. */
sealed interface ModelState {
    /** Not on this phone yet; [bytes] is the download size. */
    data class NotDownloaded(val bytes: Long) : ModelState
    data class Downloading(val done: Long, val total: Long) : ModelState {
        val fraction: Float get() = if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 0f
    }
    data object Ready : ModelState
    /** This phone can't run it (too little memory or space). */
    data class Unsupported(val reason: Reason) : ModelState
    data class Failed(val message: String?) : ModelState

    enum class Reason { Memory, Storage }
}

/**
 * Gemma 3 270M on the phone through MediaPipe LLM Inference. The model file (about 300 MB) is
 * downloaded once from Firebase Storage and kept in app storage; it is loaded on first use and
 * answers without the internet.
 */
class GemmaEngine(context: Context, private val storage: FirebaseStorage) {
    private val app = context.applicationContext
    private val dir = File(app.filesDir, "ai").apply { mkdirs() }
    private val _state = MutableStateFlow<ModelState>(ModelState.NotDownloaded(DEFAULT_BYTES))
    val state: StateFlow<ModelState> = _state
    private var engine: LlmInference? = null
    private var modelPath: String = DEFAULT_PATH
    private var modelBytes: Long = DEFAULT_BYTES
    private val lock = Mutex()

    private fun fileFor(path: String) = File(dir, path.substringAfterLast('/'))

    /** Checks what is on the phone for the model the server names. */
    fun configure(path: String, bytes: Long) {
        modelPath = path.ifBlank { DEFAULT_PATH }
        if (bytes > 0) modelBytes = bytes
        if (_state.value is ModelState.Downloading) return
        _state.value = when {
            !enoughMemory() -> ModelState.Unsupported(ModelState.Reason.Memory)
            isDownloaded() -> ModelState.Ready
            else -> ModelState.NotDownloaded(modelBytes)
        }
    }

    private fun isDownloaded(): Boolean = fileFor(modelPath).let { it.exists() && it.length() > MIN_VALID_BYTES }

    /** Phones with under about 2 GB of memory can't hold the model and the app together. */
    private fun enoughMemory(): Boolean {
        val am = app.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return true
        val info = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        return info.totalMem >= MIN_RAM_BYTES
    }

    /** Downloads the model once. Safe to call again: it does nothing while a download runs. */
    suspend fun download() {
        if (_state.value is ModelState.Downloading || isDownloaded()) {
            if (isDownloaded()) _state.value = ModelState.Ready
            return
        }
        if (dir.usableSpace < modelBytes + SPARE_BYTES) {
            _state.value = ModelState.Unsupported(ModelState.Reason.Storage)
            return
        }
        val target = fileFor(modelPath)
        val part = File(dir, target.name + ".part")
        _state.value = ModelState.Downloading(0, modelBytes)
        try {
            storage.reference.child(modelPath).getFile(part)
                .addOnProgressListener { s ->
                    val total = if (s.totalByteCount > 0) s.totalByteCount else modelBytes
                    _state.value = ModelState.Downloading(s.bytesTransferred, total)
                }
                .await()
            if (!part.renameTo(target)) throw IllegalStateException("Could not save the model")
            _state.value = ModelState.Ready
        } catch (t: Throwable) {
            part.delete()
            _state.value = ModelState.Failed(t.message)
            if (t is kotlinx.coroutines.CancellationException) throw t
        }
    }

    /** Frees the space the model uses. It downloads again the next time it's wanted. */
    suspend fun remove() = lock.withLock {
        engine?.close()
        engine = null
        dir.listFiles()?.forEach { it.delete() }
        _state.value = ModelState.NotDownloaded(modelBytes)
    }

    private suspend fun loaded(): LlmInference = lock.withLock {
        engine ?: withContext(Dispatchers.IO) {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(fileFor(modelPath).absolutePath)
                .setMaxTokens(MAX_TOKENS)
                .setPreferredBackend(LlmInference.Backend.CPU)
                .build()
            LlmInference.createFromOptions(app, options)
        }.also { engine = it }
    }

    /** How many tokens [text] takes, to keep a prompt within the model's window. */
    suspend fun tokens(text: String): Int = loaded().sizeInTokens(text)

    /**
     * Writes an answer for a prompt already in Gemma's chat format ([GemmaPrompt.build]). Emits the
     * text as it grows; cancelling stops the model.
     */
    fun generate(prompt: String): Flow<String> = callbackFlow {
        val llm = loaded()
        // The prompt is already in Gemma's format, so the session adds no wrappers of its own
        val templates = PromptTemplates.builder()
            .setUserPrefix("").setUserSuffix("").setModelPrefix("").setModelSuffix("").setSystemPrefix("").setSystemSuffix("")
            .build()
        val session = LlmInferenceSession.createFromOptions(
            llm,
            LlmInferenceSession.LlmInferenceSessionOptions.builder()
                .setTemperature(0.3f).setTopK(32).setTopP(0.9f).setPromptTemplates(templates).build(),
        )
        val text = StringBuilder()
        session.addQueryChunk(prompt)
        val future = session.generateResponseAsync { partial, done ->
            text.append(partial)
            trySend(text.toString())
            if (done) close()
        }
        future.addListener({
            runCatching { future.get() }.onFailure { close(it.cause ?: it) }
        }, Runnable::run)
        awaitClose {
            runCatching { session.cancelGenerateResponseAsync() }
            runCatching { session.close() }
        }
    }.flowOn(Dispatchers.Default)

    companion object {
        const val DEFAULT_PATH = "ai/models/gemma3-270m-it-q8.task"
        const val DEFAULT_BYTES = 303_950_933L
        /** Prompt and answer together; the 270M model's window is kept small for speed. */
        const val MAX_TOKENS = 1280
        /** The prompt is trimmed to this so the answer has room. */
        const val MAX_PROMPT_TOKENS = 900
        private const val MIN_RAM_BYTES = 2L * 1024 * 1024 * 1024 - 256L * 1024 * 1024
        private const val SPARE_BYTES = 100L * 1024 * 1024
        private const val MIN_VALID_BYTES = 50L * 1024 * 1024
    }
}
