package com.ojashealing.family.domain

/**
 * What a viewer may do, per the design's §9 decisions. The server enforces the same rules in
 * Firestore rules and Cloud Functions; this copy only decides what the UI shows or locks.
 */
object Permissions {

    fun isStaff(v: Viewer): Boolean = v.role != Role.Member

    // A paid member starts with Second User Level rights, whatever their free level
    private fun effectiveFreeRank(v: Viewer): Int =
        if (v.memberLevel >= MemberLevel.M1) maxOf(v.userLevel.rank, UserLevel.L2.rank) else v.userLevel.rank

    // Member Level 2 gets Third User Level rights
    private fun hasLevel3Rights(v: Viewer): Boolean =
        effectiveFreeRank(v) >= UserLevel.L3.rank || v.memberLevel >= MemberLevel.M2

    fun canLike(v: Viewer): Boolean = isStaff(v) || effectiveFreeRank(v) >= UserLevel.L2.rank

    fun canComment(v: Viewer): Boolean = isStaff(v) || hasLevel3Rights(v)

    fun canAskQuestion(v: Viewer): Boolean = isStaff(v) || hasLevel3Rights(v)

    fun canReadUnderstanding(v: Viewer): Boolean = isStaff(v) || effectiveFreeRank(v) >= UserLevel.L2.rank

    fun canDoDailyPractices(v: Viewer): Boolean =
        isStaff(v) || v.userLevel >= UserLevel.L4 || v.memberLevel >= MemberLevel.M1

    fun canPostDailyActivity(v: Viewer): Boolean = isStaff(v) || v.memberLevel >= MemberLevel.M3

    // ---- Chat and calls by member level (owner 2026-11-07); packs still work for non-members ----

    /** Chat with files: Member Level 1, or a chat pack. */
    fun canChat(v: Viewer, chatPack: Boolean): Boolean = isStaff(v) || v.memberLevel >= MemberLevel.M1 || chatPack

    /** Audio calls: Member Level 2, or a call pack with calls left. */
    fun canAudioCall(v: Viewer, callPack: Boolean): Boolean = isStaff(v) || v.memberLevel >= MemberLevel.M2 || callPack

    /** Video calls: Member Level 3. */
    fun canVideoCall(v: Viewer): Boolean = isStaff(v) || v.memberLevel >= MemberLevel.M3

    /** Hiding your profile opens at User Level 3; staff can always. firestore.rules checks the same. */
    fun canHideProfile(v: Viewer): Boolean = v.role != Role.Member || v.userLevel.rank >= 3

    fun canViewOtherProfiles(v: Viewer): Boolean = isStaff(v) || v.memberLevel >= MemberLevel.M3

    fun canBuyWeeklyChat(v: Viewer): Boolean = isStaff(v) || effectiveFreeRank(v) >= UserLevel.L2.rank

    fun canBuyMonthlyChat(v: Viewer): Boolean = isStaff(v) || v.memberLevel >= MemberLevel.M2

    fun canBuyAnytimeCalls(v: Viewer): Boolean = isStaff(v) || v.memberLevel >= MemberLevel.M3

    // A one-week pack of 3–4 calls, open from Level 2 like the weekly chat (design §9)
    fun canBuyWeeklyCalls(v: Viewer): Boolean = canBuyWeeklyChat(v)

    // Daily Activities: every paid member reads, likes and replies; Member Level 3 posts
    fun canViewDailyActivities(v: Viewer): Boolean = isStaff(v) || v.memberLevel >= MemberLevel.M1

    // Pictures in comments, answers and questions: paid members, held for admin review (spec 1.1, 3)
    fun canAttachImage(v: Viewer): Boolean = isStaff(v) || v.memberLevel >= MemberLevel.M1

    // Swamiji and admins answer every chat and call; a senior member answers the ones assigned to them
    fun isChatStaff(v: Viewer): Boolean = isStaff(v)
}
