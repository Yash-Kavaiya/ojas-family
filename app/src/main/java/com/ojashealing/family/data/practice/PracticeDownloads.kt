package com.ojashealing.family.data.practice

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ojashealing.family.domain.ContentSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** The phone's copy of a practice file: a hash of its URL (a new upload gets a new name) plus its type. */
fun practiceFileName(url: String): String {
    val hash = MessageDigest.getInstance("SHA-256").digest(url.toByteArray())
        .joinToString("") { "%02x".format(it) }.take(32)
    val path = url.substringBefore('?')
    val ext = listOf("mp4", "m4a", "mp3").firstOrNull { path.endsWith(".$it", ignoreCase = true) } ?: "mp4"
    return "$hash.$ext"
}

/** What a sync has to do: practices still to download, and file names no practice uses any more. */
data class SyncPlan(val download: List<PracticeMedia>, val delete: List<String>)

private val PracticeMedia.isFile get() = type != MediaType.YouTube

/**
 * Compares the admin's practice media with the files on the phone. An empty [media] means the
 * config is not loaded (or could not be read), so nothing is deleted. A ".part" file is an
 * unfinished download: it does not count as downloaded.
 */
fun planSync(media: Map<ContentSection, PracticeMedia>, existing: List<String>): SyncPlan {
    if (media.isEmpty()) return SyncPlan(emptyList(), emptyList())
    val files = media.values.filter { it.isFile }
    val wanted = files.map { practiceFileName(it.url) }.toSet()
    val download = files.filter { practiceFileName(it.url) !in existing }
    val delete = existing.filter { it.removeSuffix(".part") !in wanted }
    return SyncPlan(download, delete)
}

/** Whether a practice can be played yet. */
sealed interface DownloadState {
    data object Ready : DownloadState
    /** [waitingForWifi] while the download has not started, e.g. there is no Wi-Fi. */
    data class Downloading(val percent: Int, val waitingForWifi: Boolean) : DownloadState
    /** YouTube practices stream; there is nothing to download. */
    data object NotNeeded : DownloadState
}

/**
 * Keeps the guided practice files on the phone (app-private storage), so they are downloaded once
 * and never streamed. Downloads run in [PracticeDownloadWorker], on Wi-Fi unless the member asks.
 */
open class PracticeDownloads(
    context: Context,
    private val dir: File = File(context.applicationContext.filesDir, DIR),
) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val held = mutableSetOf<String>()

    /** The finished file for [media], or null while it is not downloaded (or for YouTube). */
    open fun localFile(media: PracticeMedia): File? =
        if (!media.isFile) null else File(dir, practiceFileName(media.url)).takeIf { it.isFile }

    /** A session playing [file] keeps it from being deleted until [release]. */
    @Synchronized fun hold(file: File) { held += file.name }
    @Synchronized fun release(file: File) { held -= file.name }

    /** Deletes files no practice uses and starts downloading the missing ones (Wi-Fi only). */
    @Synchronized
    fun sync(media: Map<ContentSection, PracticeMedia>) {
        if (media.isEmpty()) return
        dir.mkdirs()
        val plan = planSync(media, dir.list()?.toList().orEmpty())
        plan.delete.filter { it.removeSuffix(".part") !in held }.forEach { File(dir, it).delete() }
        prefs.edit().putStringSet(KEY_URLS, media.values.filter { it.isFile }.map { it.url }.toSet()).apply()
        if (plan.download.isNotEmpty()) enqueue(WORK, NetworkType.UNMETERED, ExistingWorkPolicy.KEEP)
    }

    /**
     * The member chose to download [media] now, over mobile data too. Only that practice is fetched
     * this way; the others still wait for Wi-Fi.
     */
    open fun downloadNowOnMobileData(media: PracticeMedia) =
        enqueue(WORK_NOW, NetworkType.CONNECTED, ExistingWorkPolicy.REPLACE, only = media.url)

    private fun enqueue(name: String, network: NetworkType, policy: ExistingWorkPolicy, only: String? = null) {
        val request = OneTimeWorkRequestBuilder<PracticeDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(network).setRequiresStorageNotLow(true).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
            .apply { if (only != null) setInputData(workDataOf(KEY_ONLY to only)) }
            .build()
        WorkManager.getInstance(app).enqueueUniqueWork(name, policy, request)
    }

    private fun workInfos() = WorkManager.getInstance(app).getWorkInfosByTagFlow(TAG)

    /** [DownloadState] of one practice, updated as the download moves along. */
    open fun state(media: PracticeMedia): Flow<DownloadState> {
        if (!media.isFile) return kotlinx.coroutines.flow.flowOf(DownloadState.NotNeeded)
        return workInfos().map { infos ->
            if (localFile(media) != null) return@map DownloadState.Ready
            val running = infos.filter { it.state == WorkInfo.State.RUNNING }
            if (running.isEmpty()) DownloadState.Downloading(0, waitingForWifi = true)
            else {
                val mine = running.firstOrNull { it.progress.getString(KEY_URL) == media.url }
                DownloadState.Downloading(mine?.progress?.getInt(KEY_PERCENT, 0) ?: 0, waitingForWifi = false)
            }
        }.distinctUntilChanged()
    }

    /** Emits whenever a download job moves along, and once at the start. */
    open fun changes(): Flow<Unit> = workInfos().map { }

    /** Practice URLs the last sync asked for. */
    internal fun wantedUrls(): List<String> = prefs.getStringSet(KEY_URLS, emptySet()).orEmpty().sorted()

    internal fun folder(): File = dir

    companion object {
        const val DIR = "practice"
        const val WORK = "practice-downloads"
        const val WORK_NOW = "practice-download-now"
        private const val TAG = "practice-download"
        const val KEY_ONLY = "only"
        private const val PREFS = "practice_downloads"
        private const val KEY_URLS = "urls"
        const val KEY_URL = "url"
        const val KEY_PERCENT = "percent"
    }
}


/** The wanted URLs still to download; with [only] (mobile data), just that one if still wanted. */
fun urlsToFetch(wanted: List<String>, only: String?, have: (String) -> Boolean): List<String> =
    (if (only != null) wanted.filter { it == only } else wanted).filterNot(have)

/**
 * Runs [fetch] for every URL; one that fails (HTTP error, bad URL, cut-off connection) does not
 * stop the others. Returns the URLs that failed.
 */
suspend fun fetchEach(urls: List<String>, fetch: suspend (String) -> Unit): List<String> {
    val failed = mutableListOf<String>()
    for (url in urls) {
        try {
            fetch(url)
        } catch (e: IOException) {
            failed += url
        } catch (e: IllegalArgumentException) {
            failed += url
        }
    }
    return failed
}

/**
 * Writes [input] to "<target>.part" and renames it to [target] only when all [total] bytes (when
 * known) arrived. On any failure the ".part" is deleted, so no partial video is left taking space.
 * [onProgress] gets whole percents and may throw to stop.
 */
fun saveDownload(input: java.io.InputStream, total: Long, target: File, onProgress: (Int) -> Unit) {
    val part = File(target.parentFile, target.name + ".part")
    try {
        var done = 0L
        var shown = 0
        input.use { inp ->
            part.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = inp.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    val pct = if (total > 0) (done * 100 / total).toInt() else 0
                    if (pct > shown) {
                        shown = pct
                        onProgress(pct)
                    }
                }
            }
        }
        if (total > 0 && done != total) throw IOException("cut off at $done of $total")
        if (!part.renameTo(target)) throw IOException("could not save ${target.name}")
    } catch (t: Throwable) {
        part.delete()
        throw t
    }
}

/**
 * Downloads the wanted practice files that are not on the phone yet, one at a time (or only the
 * one in [PracticeDownloads.KEY_ONLY]). A file the admin replaced meanwhile is skipped. If any
 * file failed, the job is retried later with backoff; the files that did arrive are kept.
 */
class PracticeDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val downloads = PracticeDownloads(context)
    private val client = OkHttpClient.Builder().readTimeout(30, TimeUnit.SECONDS).build()

    override suspend fun doWork(): Result {
        val dir = downloads.folder().apply { mkdirs() }
        val only = inputData.getString(PracticeDownloads.KEY_ONLY)
        val urls = urlsToFetch(downloads.wantedUrls(), only) { File(dir, practiceFileName(it)).isFile }
        val failed = fetchEach(urls) { url ->
            if (url in downloads.wantedUrls()) fetch(url, File(dir, practiceFileName(url)))
        }
        return if (failed.isEmpty()) Result.success() else Result.retry()
    }

    private suspend fun fetch(url: String, target: File) {
        setProgress(workDataOf(PracticeDownloads.KEY_URL to url, PracticeDownloads.KEY_PERCENT to 0))
        withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
                val body = r.body ?: throw IOException("empty body")
                saveDownload(body.byteStream(), body.contentLength(), target) { pct ->
                    // A stopped job (e.g. storage got low) ends here, and its .part is deleted
                    if (isStopped) throw IOException("stopped")
                    setProgressAsync(workDataOf(PracticeDownloads.KEY_URL to url, PracticeDownloads.KEY_PERCENT to pct))
                }
            }
        }
    }
}
