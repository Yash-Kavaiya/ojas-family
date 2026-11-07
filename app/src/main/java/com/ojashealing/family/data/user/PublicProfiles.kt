package com.ojashealing.family.data.user

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.ojashealing.family.domain.LocalDateLike
import com.ojashealing.family.domain.Role
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** What others may see of a member (publicProfiles/{uid}, kept by a Cloud Function; no date of birth). */
data class PublicProfile(
    val uid: String,
    val displayName: String,
    val photoUrl: String,
    val birthMonth: String, // "yyyy-MM"
    val gender: String,
    val country: String,
    val state: String,
    val city: String,
    val mokshaPoints: Int,
    val userLevel: Int,
    val memberLevel: Int,
    val role: Role,
    val hidden: Boolean,
    val achievedGoals: List<String>,
    val about: String = "",
    val village: String = ""
)

/** Age from a birth month: whole years, counting the birthday as reached at the start of its month. */
fun ageFromBirthMonth(birthMonth: String, today: LocalDateLike): Int? {
    val parts = birthMonth.split("-").mapNotNull { it.toIntOrNull() }
    if (parts.size < 2) return null
    val (y, m) = parts
    return (today.year - y - if (today.month < m) 1 else 0).takeIf { it in 0..130 }
}

private fun DocumentSnapshot.toPublicProfile(): PublicProfile {
    @Suppress("UNCHECKED_CAST")
    val goals = (get("achievedGoals") as? List<Any?>).orEmpty().mapNotNull { it as? String }
    return PublicProfile(
        uid = id,
        displayName = getString("displayName").orEmpty(),
        photoUrl = getString("photoUrl").orEmpty(),
        birthMonth = getString("birthMonth").orEmpty(),
        gender = getString("gender").orEmpty(),
        country = getString("country").orEmpty(),
        state = getString("state").orEmpty(),
        city = getString("city").orEmpty(),
        mokshaPoints = getLong("mokshaPoints")?.toInt() ?: 0,
        userLevel = getLong("userLevel")?.toInt() ?: 1,
        memberLevel = getLong("memberLevel")?.toInt() ?: 0,
        role = Role.fromId(getString("role")),
        hidden = getBoolean("hidden") == true,
        achievedGoals = goals,
        about = getString("about").orEmpty(),
        village = getString("village").orEmpty()
    )
}

/** A row in the admin's member list (admins may read every profile). */
data class MemberRow(val uid: String, val name: String, val photoUrl: String, val role: Role, val userLevel: Int, val memberLevel: Int, val points: Int)

class PublicProfileRepository(private val db: FirebaseFirestore) {

    /** Emits null when the profile is hidden from this viewer or does not exist. */
    fun observe(uid: String): Flow<PublicProfile?> = callbackFlow {
        val reg = db.document("publicProfiles/$uid").addSnapshotListener { s, e ->
            trySend(if (e != null) null else s?.takeIf { it.exists() }?.toPublicProfile())
        }
        awaitClose { reg.remove() }
    }

    /** Admin: members whose name starts with [prefix] (case as typed), or the newest when blank. */
    suspend fun searchMembers(prefix: String): Result<List<MemberRow>> = runCatching {
        val base = db.collection("users")
        val q = if (prefix.isBlank()) base.orderBy("createdAt", Query.Direction.DESCENDING).limit(50)
        else base.orderBy("displayName").startAt(prefix.trim()).endAt(prefix.trim() + "").limit(50)
        q.get().await().documents.map { d ->
            MemberRow(
                d.id, d.getString("displayName").orEmpty(), d.getString("photoUrl").orEmpty(), Role.fromId(d.getString("role")),
                d.getLong("userLevel")?.toInt() ?: 1, d.getLong("memberLevel")?.toInt() ?: 0, d.getLong("mokshaPoints")?.toInt() ?: 0
            )
        }
    }
}
