package com.ojashealing.family.domain

import com.ojashealing.family.data.user.UserProfile

/**
 * What makes a profile 100% complete. Same items and rules as the server's
 * firebase/functions/src/profilePoints.ts, which gives the points; tests keep the two in step.
 */
enum class ProfileItem(val id: String) {
    Photo("photo"), Name("name"), Dob("dob"), Gender("gender"), Location("location"), About("about"), Language("language");
}

/** The shortest "about me" that counts as written. */
const val ABOUT_MIN_CHARS = 10

data class ProfileCompletion(val done: Set<ProfileItem>) {
    val missing: List<ProfileItem> get() = ProfileItem.entries.filterNot { it in done }
    val percent: Int get() = (done.size * 100 + ProfileItem.entries.size / 2) / ProfileItem.entries.size
    val complete: Boolean get() = done.size == ProfileItem.entries.size
}

fun profileCompletion(p: UserProfile): ProfileCompletion {
    fun filled(s: String) = s.isNotBlank()
    val done = buildSet {
        if (filled(p.photoUrl)) add(ProfileItem.Photo)
        if (filled(p.displayName)) add(ProfileItem.Name)
        if (filled(p.dob)) add(ProfileItem.Dob)
        if (filled(p.gender)) add(ProfileItem.Gender)
        if (filled(p.country) && (filled(p.state) || filled(p.city))) add(ProfileItem.Location)
        if (p.about.trim().length >= ABOUT_MIN_CHARS) add(ProfileItem.About)
        if (filled(p.language)) add(ProfileItem.Language)
    }
    return ProfileCompletion(done)
}
