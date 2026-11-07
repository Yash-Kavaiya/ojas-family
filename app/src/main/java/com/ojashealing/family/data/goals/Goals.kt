package com.ojashealing.family.data.goals

import android.content.ContentResolver
import android.net.Uri
import com.ojashealing.family.data.shrunkJpeg
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.mapLatest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.tasks.await

enum class GoalKind(val id: String) {
    Photo("photo"), MorningPractice21("practice_morning_21"), BothPractices21("practice_both_21");

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id }
    }
}

/** An admin-defined goal; [title] and [description] are keyed by language ("en", "hi", "gu"). */
data class Goal(
    val id: String,
    val title: Map<String, String>,
    val description: Map<String, String>,
    val kind: GoalKind,
    val reward: Int,
    val order: Int,
    val active: Boolean
) {
    fun titleIn(lang: String) = title[lang] ?: title["en"].orEmpty()
    fun descriptionIn(lang: String) = description[lang] ?: description["en"].orEmpty()
}

enum class GoalStatus { Submitted, Approved, Rejected }

data class GoalProgress(val goalId: String, val status: GoalStatus, val photoUrl: String, val updatedAt: Long)

/** A member's photo waiting for an admin. */
data class GoalSubmission(
    val uid: String,
    val goalId: String,
    val photoUrl: String,
    val note: String,
    val memberName: String = "",
    val memberPhotoUrl: String = ""
)

/** The next goal to work on: first active goal by order that is neither approved nor waiting for review. */
fun suggestGoal(goals: List<Goal>, progress: Map<String, GoalProgress>): Goal? =
    goals.filter { it.active }
        .sortedBy { it.order }
        .firstOrNull { progress[it.id]?.status.let { s -> s != GoalStatus.Approved && s != GoalStatus.Submitted } }

fun achieved(goals: List<Goal>, progress: Map<String, GoalProgress>): List<Goal> =
    goals.filter { progress[it.id]?.status == GoalStatus.Approved }
        .sortedByDescending { progress[it.id]?.updatedAt ?: 0 }

@Suppress("UNCHECKED_CAST")
private fun DocumentSnapshot.stringMap(field: String) = (get(field) as? Map<String, Any?>).orEmpty().mapValues { it.value?.toString().orEmpty() }

@OptIn(ExperimentalCoroutinesApi::class)
class GoalRepository(
    private val db: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val resolver: ContentResolver
) {

    fun observeGoals(): Flow<List<Goal>> = callbackFlow {
        val reg = db.collection("goals").addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            trySend(snap.documents.mapNotNull { d ->
                val kind = GoalKind.fromId(d.getString("kind")) ?: return@mapNotNull null
                Goal(d.id, d.stringMap("title"), d.stringMap("description"), kind,
                    d.getLong("reward")?.toInt() ?: 0, d.getLong("order")?.toInt() ?: 0, d.getBoolean("active") ?: true)
            })
        }
        awaitClose { reg.remove() }
    }

    fun observeProgress(uid: String): Flow<Map<String, GoalProgress>> = callbackFlow {
        val reg = db.collection("users/$uid/goalProgress").addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            trySend(snap.documents.associate { d ->
                val status = when (d.getString("status")) {
                    "approved" -> GoalStatus.Approved
                    "rejected" -> GoalStatus.Rejected
                    else -> GoalStatus.Submitted
                }
                val at = (d.getTimestamp("reviewedAt") ?: d.getTimestamp("submittedAt"))?.toDate()?.time ?: 0
                d.id to GoalProgress(d.id, status, d.getString("photoUrl").orEmpty(), at)
            })
        }
        awaitClose { reg.remove() }
    }

    /** Uploads the photo first; the submission is only written if the upload succeeded. */
    suspend fun submit(uid: String, goalId: String, photo: Uri, note: String): Result<Unit> = runCatching {
        val ref = storage.reference.child("users/$uid/goals/$goalId.jpg")
        ref.putBytes(resolver.shrunkJpeg(photo, 1600), StorageMetadata.Builder().setContentType("image/jpeg").build()).await()
        val url = ref.downloadUrl.await().toString()
        db.document("users/$uid/goalProgress/$goalId").set(
            mapOf("status" to "submitted", "photoUrl" to url, "note" to note.take(500), "submittedAt" to FieldValue.serverTimestamp())
        ).await()
        Unit
    }

    fun observeSubmissions(): Flow<List<GoalSubmission>> = callbackFlow {
        val reg = db.collectionGroup("goalProgress").whereEqualTo("status", "submitted").addSnapshotListener { snap, _ ->
            if (snap == null) return@addSnapshotListener
            trySend(snap.documents.map { d ->
                GoalSubmission(uid = d.reference.parent.parent?.id.orEmpty(), goalId = d.id,
                    photoUrl = d.getString("photoUrl").orEmpty(), note = d.getString("note").orEmpty())
            })
        }
        awaitClose { reg.remove() }
    }.mapLatest { subs -> subs.map { it.withMember(memberOf(it.uid)) } }

    // Admins may read any profile, so the review card can say who sent the photo; looked up once per member
    private val members = ConcurrentHashMap<String, Pair<String, String>>()

    private suspend fun memberOf(uid: String): Pair<String, String>? = members[uid] ?: runCatching {
        val d = db.document("users/$uid").get().await()
        (d.getString("displayName").orEmpty() to d.getString("photoUrl").orEmpty()).also { members[uid] = it }
    }.getOrNull()

    private fun GoalSubmission.withMember(m: Pair<String, String>?) =
        if (m == null) this else copy(memberName = m.first, memberPhotoUrl = m.second)

    suspend fun review(uid: String, goalId: String, approve: Boolean): Result<Unit> = runCatching {
        db.document("users/$uid/goalProgress/$goalId")
            .update(mapOf("status" to if (approve) "approved" else "rejected", "reviewedAt" to FieldValue.serverTimestamp())).await()
        Unit
    }
}
