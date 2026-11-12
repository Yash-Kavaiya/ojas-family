package com.ojashealing.family.data.practice

import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.ojashealing.family.domain.LocalDateLike
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.TimeZone

enum class PracticeKind(val id: String) { Morning("morning"), Night("night") }

/** One IST day's practice. [any] is set when any guided practice was finished that day. */
data class DayLog(val morning: Boolean, val night: Boolean, val any: Boolean = morning || night)

/** The Practice tab's streak: days in a row with at least one practice. */
data class StreakSummary(
    val current: Int,
    val best: Int,
    val doneToday: Boolean,
    /** The last 7 IST days, oldest first: (yyyy-MM-dd, practised). The last one is today. */
    val week: List<Pair<String, Boolean>>,
)

/** Days a streak must reach for the 21-day goals; also the target the streak card shows. */
const val STREAK_GOAL_DAYS = 21

/** How many days back the app reads to count streaks. */
const val STREAK_WINDOW_DAYS = 60

/** Today's practice and the current streaks toward the 21-day goals. */
data class PracticeStatus(val morningToday: Boolean, val nightToday: Boolean, val morningStreak: Int, val bothStreak: Int)

private val ist = TimeZone.getTimeZone("Asia/Kolkata")

/** IST days ending on [today], newest first ([count] of them). */
fun lastDays(today: LocalDateLike, count: Int): List<String> {
    val cal = Calendar.getInstance(ist).apply { clear(); set(today.year, today.month - 1, today.day) }
    return (0 until count).map {
        val id = "%04d-%02d-%02d".format(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        cal.add(Calendar.DAY_OF_MONTH, -1)
        id
    }
}

/**
 * Same counting as the server: consecutive days ending today. A day not yet practised today does not
 * break a streak that ran up to yesterday, so the count shown is what today's practice would extend.
 */
fun practiceStatus(logs: Map<String, DayLog>, today: LocalDateLike): PracticeStatus {
    val days = lastDays(today, 40)
    fun streak(done: (DayLog) -> Boolean): Int {
        val start = if (logs[days[0]]?.let(done) == true) 0 else 1
        return days.drop(start).takeWhile { logs[it]?.let(done) == true }.size
    }
    val t = logs[days[0]]
    return PracticeStatus(
        morningToday = t?.morning == true,
        nightToday = t?.night == true,
        morningStreak = streak { it.morning },
        bothStreak = streak { it.morning && it.night }
    )
}

/**
 * Counts like the server: a day missed today doesn't break the run yet, because today can still
 * be practised. "Best" is the longest run inside the days read.
 */
fun streakSummary(logs: Map<String, DayLog>, today: LocalDateLike): StreakSummary {
    val days = lastDays(today, STREAK_WINDOW_DAYS)
    fun done(d: String) = logs[d]?.any == true
    val start = if (done(days[0])) 0 else 1
    val current = days.drop(start).takeWhile(::done).size
    var best = 0
    var run = 0
    days.forEach { d -> if (done(d)) { run++; best = maxOf(best, run) } else run = 0 }
    return StreakSummary(
        current = current,
        best = maxOf(best, current),
        doneToday = done(days[0]),
        week = days.take(7).reversed().map { it to done(it) },
    )
}

class PracticeRepository(private val db: FirebaseFirestore, private val functions: FirebaseFunctions) {

    fun observeLogs(uid: String, today: LocalDateLike): Flow<Map<String, DayLog>> = callbackFlow {
        val since = lastDays(today, STREAK_WINDOW_DAYS).last()
        val reg = db.collection("users/$uid/practiceLog")
            .whereGreaterThanOrEqualTo(FieldPath.documentId(), since)
            .limit(STREAK_WINDOW_DAYS.toLong())
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                trySend(snap.documents.associate { d ->
                    val morning = d.getBoolean("morning") == true
                    val night = d.getBoolean("night") == true
                    d.id to DayLog(morning, night, any = d.getBoolean("any") == true || morning || night)
                })
            }
        awaitClose { reg.remove() }
    }

    /** The server stamps the IST day and completes 21-day goals; returns the ids of goals just completed. */
    suspend fun log(kind: PracticeKind): Result<List<String>> = runCatching {
        val data = functions.getHttpsCallable("logPractice").call(mapOf("kind" to kind.id)).await().getData() as? Map<*, *>
        (data?.get("completed") as? List<*>).orEmpty().map { it.toString() }
    }
}
