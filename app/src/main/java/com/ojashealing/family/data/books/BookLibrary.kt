package com.ojashealing.family.data.books

import android.content.Context
import com.ojashealing.family.data.practice.saveDownload
import com.ojashealing.family.domain.Book
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** The phone's copy of a book: a hash of its address, so a new upload gets a new file. */
fun bookFileName(url: String): String =
    MessageDigest.getInstance("SHA-256").digest(url.trim().toByteArray())
        .joinToString("") { "%02x".format(it) }.take(32) + ".pdf"

private val DriveFile = Regex("""drive\.google\.com/file/d/([A-Za-z0-9_-]+)""")
private val DriveOpen = Regex("""drive\.google\.com/(?:open|uc)\?(?:.*&)?id=([A-Za-z0-9_-]+)""")

/**
 * The address that gives the PDF itself rather than a page about it: a Google Drive share link
 * becomes its download link, a Dropbox link asks for the file. Others are kept as they are.
 */
fun directPdfUrl(url: String): String {
    val u = url.trim()
    (DriveFile.find(u) ?: DriveOpen.find(u))?.let {
        return "https://drive.google.com/uc?export=download&confirm=t&id=${it.groupValues[1]}"
    }
    if (u.contains("dropbox.com/", ignoreCase = true)) {
        return when {
            u.contains("dl=0") -> u.replace("dl=0", "dl=1")
            u.contains("dl=1") -> u
            u.contains('?') -> "$u&dl=1"
            else -> "$u?dl=1"
        }
    }
    return u
}

/** True when [head] (the first bytes of a file) is a PDF; a sign-in or "file too big" page is not. */
fun looksLikePdf(head: ByteArray): Boolean {
    // "%PDF" may come after a few junk bytes in some files
    val text = String(head, Charsets.ISO_8859_1)
    return text.indexOf("%PDF") in 0..1023
}

/** Where one book stands on the phone. */
sealed interface BookFileState {
    data object NotDownloaded : BookFileState
    data class Downloading(val percent: Int) : BookFileState
    data object Downloaded : BookFileState
    data object Failed : BookFileState
}

/**
 * Books kept on the phone (app-private storage) so they open offline, with where each reader
 * stopped, bookmarks and the reading look. Downloads run while the app is open.
 */
class BookLibrary(context: Context, private val dir: File = File(context.applicationContext.filesDir, "books")) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("book_library", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    private val jobs = mutableMapOf<String, Job>()

    private val _states = MutableStateFlow<Map<String, BookFileState>>(emptyMap())
    /** By book address; a missing address means not on the phone. */
    val states: StateFlow<Map<String, BookFileState>> = _states.asStateFlow()

    init {
        dir.mkdirs()
        // Leftovers of a download the app was closed during
        dir.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
        val saved = dir.list()?.toSet().orEmpty()
        _states.value = prefs.getStringSet(KEY_URLS, emptySet()).orEmpty()
            .filter { bookFileName(it) in saved }
            .associateWith { BookFileState.Downloaded }
    }

    fun file(url: String): File? = File(dir, bookFileName(url)).takeIf { it.isFile && it.length() > 0 }

    fun state(url: String): BookFileState = _states.value[url] ?: if (file(url) != null) BookFileState.Downloaded else BookFileState.NotDownloaded

    /** Starts (or joins) the download of [book]; [onDone] runs on the main thread with the file or null. */
    @Synchronized
    fun download(book: Book, onDone: (File?) -> Unit = {}) {
        file(book.url)?.let { onDone(it); return }
        val url = book.url
        if (jobs[url]?.isActive == true) {
            jobs[url]?.invokeOnCompletion { scope.launch(Dispatchers.Main) { onDone(file(url)) } }
            return
        }
        setState(url, BookFileState.Downloading(0))
        jobs[url] = scope.launch {
            val ok = runCatching { fetch(url) }.isSuccess
            if (ok) {
                rememberUrl(url, true)
                setState(url, BookFileState.Downloaded)
            } else setState(url, BookFileState.Failed)
            withContext(Dispatchers.Main) { onDone(file(url)) }
        }
    }

    private fun fetch(url: String) {
        val target = File(dir, bookFileName(url))
        val request = Request.Builder().url(directPdfUrl(url)).header("User-Agent", "OjasFamily/1.0 (Android)").build()
        client.newCall(request).execute().use { res ->
            if (!res.isSuccessful) throw IOException("HTTP ${res.code}")
            val body = res.body ?: throw IOException("empty")
            val input = java.io.BufferedInputStream(body.byteStream(), 64 * 1024)
            input.mark(1100)
            val head = ByteArray(1024)
            val n = input.read(head)
            input.reset()
            if (n <= 0 || !looksLikePdf(head.copyOf(n))) throw IOException("not a PDF")
            saveDownload(input, body.contentLength(), target) { pct -> setState(url, BookFileState.Downloading(pct)) }
        }
    }

    /** Removes the phone's copy of [url]; where the reader stopped is kept. */
    @Synchronized
    fun remove(url: String) {
        jobs.remove(url)?.cancel()
        File(dir, bookFileName(url)).delete()
        rememberUrl(url, false)
        _states.update { it - url }
    }

    /** Space the downloaded books take, in bytes. */
    fun sizeOf(url: String): Long = file(url)?.length() ?: 0L

    /**
     * Drops the copies of books the admin removed or replaced with a new file. An empty list
     * means the shelf is not loaded, so nothing is removed.
     */
    fun keepOnly(urls: Collection<String>) {
        if (urls.isEmpty()) return
        val wanted = urls.map { bookFileName(it) }.toSet()
        scope.launch {
            val stale = _states.value.keys.filter { bookFileName(it) !in wanted && _states.value[it] == BookFileState.Downloaded }
            stale.forEach { remove(it) }
        }
    }

    private fun setState(url: String, s: BookFileState) = _states.update { it + (url to s) }

    @Synchronized
    private fun rememberUrl(url: String, keep: Boolean) {
        val now = prefs.getStringSet(KEY_URLS, emptySet()).orEmpty().toMutableSet()
        if (keep) now += url else now -= url
        prefs.edit().putStringSet(KEY_URLS, now).apply()
    }

    // ---- reading place, bookmarks and look ----

    fun lastPage(bookId: String): Int = prefs.getInt("page_$bookId", 0)
    fun setLastPage(bookId: String, page: Int) = prefs.edit().putInt("page_$bookId", page).apply()
    fun pageCount(bookId: String): Int = prefs.getInt("pages_$bookId", 0)
    fun setPageCount(bookId: String, count: Int) = prefs.edit().putInt("pages_$bookId", count).apply()

    fun bookmarks(bookId: String): Set<Int> =
        prefs.getStringSet("marks_$bookId", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSortedSet()

    fun setBookmarks(bookId: String, pages: Set<Int>) =
        prefs.edit().putStringSet("marks_$bookId", pages.map { it.toString() }.toSet()).apply()

    var layout: ReaderLayout
        get() = ReaderLayout.entries.firstOrNull { it.name == prefs.getString("layout", null) } ?: ReaderLayout.Scroll
        set(v) = prefs.edit().putString("layout", v.name).apply()

    var paper: ReaderPaper
        get() = ReaderPaper.entries.firstOrNull { it.name == prefs.getString("paper", null) } ?: ReaderPaper.Day
        set(v) = prefs.edit().putString("paper", v.name).apply()

    var speechRate: Float
        get() = prefs.getFloat("rate", 1f)
        set(v) = prefs.edit().putFloat("rate", v).apply()

    var shelfAsList: Boolean
        get() = prefs.getBoolean("shelf_list", true)
        set(v) = prefs.edit().putBoolean("shelf_list", v).apply()

    private companion object {
        const val KEY_URLS = "downloaded"
    }
}

/** How pages are laid out in the reader. */
enum class ReaderLayout { Scroll, Pages, TwoPages }

/** The page colour: as printed, warm paper, or light text on dark for night reading. */
enum class ReaderPaper { Day, Sepia, Night }

/** Pages shown together in the two-page layout: (0), (1, 2), (3, 4)... so the cover stands alone. */
fun spreads(pageCount: Int): List<List<Int>> {
    if (pageCount <= 0) return emptyList()
    val out = mutableListOf(listOf(0))
    var i = 1
    while (i < pageCount) {
        out += if (i + 1 < pageCount) listOf(i, i + 1) else listOf(i)
        i += 2
    }
    return out
}

/** The spread that holds [page]. */
fun spreadOf(page: Int): Int = if (page <= 0) 0 else (page + 1) / 2
