package com.ojashealing.family.data.user

import com.google.firebase.firestore.DocumentSnapshot
import com.ojashealing.family.domain.MemberLevel
import com.ojashealing.family.domain.Role
import com.ojashealing.family.domain.UserLevel
import com.ojashealing.family.domain.Viewer

data class UserProfile(
    val uid: String,
    val displayName: String,
    val photoUrl: String,
    val dob: String, // "yyyy-MM-dd"; never shown to others, only the age derived from it
    val gender: String,
    val country: String,
    val state: String,
    val city: String,
    val language: String,
    val profileComplete: Boolean,
    val hideProfile: Boolean,
    val hideGoals: Boolean,
    val notificationPrefs: Map<String, Boolean>,
    val morningReminder: String,
    val nightReminder: String,
    val mokshaPoints: Int,
    val activeDays: Int,
    val viewer: Viewer
)

// The only profile fields a client sends; server-owned fields (points, levels, role) are never written here
data class ProfileEdit(
    val displayName: String,
    val dob: String,
    val gender: String,
    val country: String,
    val state: String,
    val city: String,
    val language: String
)

internal fun DocumentSnapshot.toUserProfile(): UserProfile {
    @Suppress("UNCHECKED_CAST")
    val prefs = (get("notificationPrefs") as? Map<String, Any?>).orEmpty()
        .mapNotNull { (k, v) -> (v as? Boolean)?.let { k to it } }.toMap()
    return UserProfile(
        uid = id,
        displayName = getString("displayName").orEmpty(),
        photoUrl = getString("photoUrl").orEmpty(),
        dob = getString("dob").orEmpty(),
        gender = getString("gender").orEmpty(),
        country = getString("country").orEmpty(),
        state = getString("state").orEmpty(),
        city = getString("city").orEmpty(),
        language = getString("language") ?: "en",
        profileComplete = getBoolean("profileComplete") ?: false,
        hideProfile = getBoolean("hideProfile") ?: false,
        hideGoals = getBoolean("hideGoals") ?: false,
        notificationPrefs = prefs,
        morningReminder = getString("morningReminder") ?: "06:00",
        nightReminder = getString("nightReminder") ?: "21:30",
        mokshaPoints = getLong("mokshaPoints")?.toInt() ?: 0,
        activeDays = getLong("activeDays")?.toInt() ?: 0,
        viewer = Viewer(
            userLevel = UserLevel.fromRank(getLong("userLevel")?.toInt() ?: 1),
            memberLevel = MemberLevel.fromRank(getLong("memberLevel")?.toInt() ?: 0),
            role = Role.fromId(getString("role"))
        )
    )
}
