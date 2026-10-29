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

    fun canViewOtherProfiles(v: Viewer): Boolean = isStaff(v) || v.memberLevel >= MemberLevel.M3

    fun canBuyWeeklyChat(v: Viewer): Boolean = isStaff(v) || effectiveFreeRank(v) >= UserLevel.L2.rank

    fun canBuyMonthlyChat(v: Viewer): Boolean = isStaff(v) || v.memberLevel >= MemberLevel.M2

    fun canBuyAnytimeCalls(v: Viewer): Boolean = isStaff(v) || v.memberLevel >= MemberLevel.M3
}
