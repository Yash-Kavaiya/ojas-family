package com.ojashealing.family.data.practice

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.ojashealing.family.data.social.todayIst
import com.ojashealing.family.domain.ContentSection
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.tasks.await

/** The four guided practices that each play one video or audio in the Practice tab. */
val GuidedSections = listOf(ContentSection.BlueLight, ContentSection.QuickProtection, ContentSection.Morning, ContentSection.Night)

enum class MediaType { Video, Audio }

/** One guided practice's media, from config/practiceMedia.<section id>. */
data class PracticeMedia(val section: ContentSection, val url: String, val type: MediaType, val durationSec: Int, val title: String)

/** Points for finishing a guided practice; the server gives it once per practice per IST day. */
const val PRACTICE_POINTS = 1

/** Mirrors the server's MIN_WATCHED_SHARE: the share of the media that must really be played. */
const val MIN_WATCHED_SHARE = 0.9

/** Whether enough of the media was really played (not skipped) to count it as finished. */
fun watchedEnough(watchedMs: Long, durationMs: Long): Boolean = durationMs > 0 && watchedMs >= durationMs * MIN_WATCHED_SHARE

/** "29 min", or "45 sec" for very short media. */
fun minutesLabel(seconds: Int): Pair<Int, Boolean> = if (seconds >= 60) ((seconds + 30) / 60 to true) else (seconds to false)

/** "12:05" or "1:02:05". */
fun clockLabel(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, (s % 3600) / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** What the server said about a finished practice. */
data class PracticeResult(val awarded: Boolean, val completedGoals: List<String>)

class PracticeMediaRepository(private val db: FirebaseFirestore, private val functions: FirebaseFunctions) {

    /** The guided media an admin has set, by section; sections without media are left out. */
    fun observeMedia(): Flow<Map<ContentSection, PracticeMedia>> = callbackFlow {
        val reg = db.document("config/practiceMedia").addSnapshotListener { snap, _ ->
            val out = GuidedSections.mapNotNull { section ->
                val m = snap?.get(section.id) as? Map<*, *> ?: return@mapNotNull null
                val url = m["url"] as? String ?: return@mapNotNull null
                val secs = (m["durationSec"] as? Number)?.toInt() ?: 0
                if (url.isBlank() || secs <= 0) return@mapNotNull null
                section to PracticeMedia(
                    section, url,
                    if (m["kind"] == "audio") MediaType.Audio else MediaType.Video,
                    secs, (m["title"] as? String).orEmpty()
                )
            }.toMap()
            trySend(out)
        }
        awaitClose { reg.remove() }
    }

    /** Which guided practices the member already finished today (their point events say so). */
    fun observeDoneToday(uid: String): Flow<Set<ContentSection>> {
        val day = todayIst().iso()
        val flows = GuidedSections.map { section ->
            callbackFlow {
                val reg = db.document("users/$uid/pointEvents/practice:${section.id}:$day")
                    .addSnapshotListener { s, _ -> trySend(section to (s?.exists() == true)) }
                awaitClose { reg.remove() }
            }
        }
        return combine(flows) { pairs -> pairs.filter { it.second }.map { it.first }.toSet() }
    }

    suspend fun start(section: ContentSection): Result<Unit> = runCatching {
        functions.getHttpsCallable("startPractice").call(mapOf("section" to section.id)).await()
        Unit
    }

    suspend fun complete(section: ContentSection): Result<PracticeResult> = runCatching {
        val data = functions.getHttpsCallable("completePractice").call(mapOf("section" to section.id)).await().getData() as? Map<*, *>
        PracticeResult(
            awarded = data?.get("awarded") == true,
            completedGoals = (data?.get("completedGoals") as? List<*>).orEmpty().map { it.toString() }
        )
    }
}
