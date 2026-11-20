package com.ojashealing.family.domain

/**
 * What a viewer may do, per the design's §9 decisions. The server enforces the same rules in
 * Firestore rules and Cloud Functions; this copy only decides what the UI shows or locks.
 */
object Permissions {

    fun isStaff(v: Viewer): Boolean = v.role != Role.Member

    /** The total users and members on Home. */
    fun canSeeUserCounts(v: Viewer): Boolean = v.hasFullAccess()

    // ---- What each plan opens, and when: the table in PlanAccess.kt (owner, 2026-11-20) ----

    /** Paying now (Silver or Gold, or the older membership): goal photos and pictures in comments. */
    fun isPaidMember(v: Viewer): Boolean = isStaff(v) || v.memberLevel >= MemberLevel.M1 || v.activePlan() != Plan.User

    fun canLike(v: Viewer): Boolean = v.allows(AccessRules.Like)

    /** Replies on the Thought of the Day. */
    fun canComment(v: Viewer): Boolean = v.allows(AccessRules.ReplyThought)

    fun canReplyQa(v: Viewer): Boolean = v.allows(AccessRules.ReplyQa)

    fun canReplyAnnouncement(v: Viewer): Boolean = v.allows(AccessRules.ReplyAnnouncement)

    /** A question with words only (one a day); see [canAskWithMedia] and qaDailyLimit. */
    fun canAskQuestion(v: Viewer): Boolean = v.allows(AccessRules.AskText)

    fun canAskWithMedia(v: Viewer): Boolean = v.allows(AccessRules.AskMedia)

    /** Videos and books are open to everyone from the first day. */
    fun canReadUnderstanding(v: Viewer): Boolean = true

    /** Morning and Night practice. */
    fun canDoDailyPractices(v: Viewer): Boolean = v.allows(AccessRules.DailyPractice)

    fun canDoQuickProtection(v: Viewer): Boolean = v.allows(AccessRules.QuickProtection)

    fun canPostDailyActivity(v: Viewer): Boolean = v.allows(AccessRules.ActivityPost)

    // ---- Chat and calls by member level (owner 2026-11-07); packs still work for non-members ----

    /** Chat with files: Member Level 1, or a chat pack. */
    fun canChat(v: Viewer, chatPack: Boolean): Boolean = isStaff(v) || v.memberLevel >= MemberLevel.M1 || chatPack

    /** Audio calls: Member Level 2, or a call pack with calls left. */
    fun canAudioCall(v: Viewer, callPack: Boolean): Boolean = isStaff(v) || v.memberLevel >= MemberLevel.M2 || callPack

    /** Video calls: Member Level 3. */
    fun canVideoCall(v: Viewer): Boolean = isStaff(v) || v.memberLevel >= MemberLevel.M3

    /** Hiding your profile: Gold after a year. firestore.rules checks the same. */
    fun canHideProfile(v: Viewer): Boolean = v.allows(AccessRules.HideProfile)

    /** A photo for healing (the opening animation); an admin approves it. firestore.rules checks the same. */
    fun canUploadFacePhoto(v: Viewer): Boolean = v.allows(AccessRules.HealingPhoto)

    fun canViewOtherProfiles(v: Viewer): Boolean = isStaff(v) || v.memberLevel >= MemberLevel.M3

    // Booking a chat or call with the team (extra payment) is open to everyone from the first day (plan table)
    fun canBuyWeeklyChat(v: Viewer): Boolean = true

    fun canBuyMonthlyChat(v: Viewer): Boolean = isStaff(v) || v.memberLevel >= MemberLevel.M2

    fun canBuyAnytimeCalls(v: Viewer): Boolean = isStaff(v) || v.memberLevel >= MemberLevel.M3

    // A one-week pack of 3–4 calls, open from Level 2 like the weekly chat (design §9)
    fun canBuyWeeklyCalls(v: Viewer): Boolean = canBuyWeeklyChat(v)

    // Activity: reading and liking, then replying, then posting open by plan and time
    fun canViewDailyActivities(v: Viewer): Boolean = v.allows(AccessRules.ActivityRead)

    fun canReplyDailyActivity(v: Viewer): Boolean = v.allows(AccessRules.ActivityReply)

    // Pictures in comments and answers: paying members, held for admin review (spec 1.1, 3)
    fun canAttachImage(v: Viewer): Boolean = isPaidMember(v)

    // Swamiji and admins answer every chat and call; a senior member answers the ones assigned to them
    fun isChatStaff(v: Viewer): Boolean = isStaff(v)
}
