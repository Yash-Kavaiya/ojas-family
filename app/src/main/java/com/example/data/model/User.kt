package com.example.data.model

enum class UserLevel(val title: String, val minDays: Int, val minPoints: Int) {
    LEVEL_1("First Level (Seeker)", 0, 0),
    LEVEL_2("Second Level (Devotee)", 7, 7),
    LEVEL_3("Third Level (Sadhak)", 90, 500),
    LEVEL_4("Fourth Level (Ojas Master)", 365, 100000);

    fun canLike(): Boolean = this >= LEVEL_2
    fun canComment(): Boolean = this >= LEVEL_3
    fun canAskQuestion(): Boolean = this >= LEVEL_3
    fun canAccessDailyPractices(): Boolean = this >= LEVEL_4
    fun canAccessUnderstanding(): Boolean = this >= LEVEL_2
}

enum class PaidMemberLevel(val title: String, val monthlyFeeInr: Int) {
    NONE("Free Member", 0),
    LEVEL_1("Paid Member Level 1", 100),
    LEVEL_2("Paid Member Level 2 (3+ Mos)", 100),
    LEVEL_3("Paid Member Level 3 (6+ Mos)", 100);

    fun canDoDailyPractices(): Boolean = this != NONE
    fun canPostDailyActivities(): Boolean = this >= LEVEL_3
    fun canViewOtherProfiles(): Boolean = this >= LEVEL_3
    fun canBookChat(): Boolean = this != NONE
    fun canBookCalls(): Boolean = this >= LEVEL_2
    fun hasAnytimeCalls(): Boolean = this >= LEVEL_3
}

data class NotificationSettings(
    val allNotifications: Boolean = true,
    val announcements: Boolean = true,
    val thoughtOfDay: Boolean = true,
    val communityPosts: Boolean = true,
    val communityInteractions: Boolean = true,
    val communityMentions: Boolean = true,
    val dailyActivitiesPosts: Boolean = true,
    val dailyActivitiesReplies: Boolean = true,
    val dailyActivitiesMentions: Boolean = true,
    val morningPracticeTime: String = "06:00 AM",
    val nightPracticeTime: String = "09:30 PM"
)

data class UserProfile(
    val id: String = "user_001",
    val name: String = "Yash Anand",
    val photoUrl: String = "",
    val dob: String = "1997-04-18",
    val gender: String = "Spiritual Seeker",
    val country: String = "India",
    val state: String = "Gujarat",
    val city: String = "Ahmedabad",
    val mokshaPoints: Int = 18,
    val daysActive: Int = 14,
    val userLevel: UserLevel = UserLevel.LEVEL_2,
    val paidLevel: PaidMemberLevel = PaidMemberLevel.LEVEL_1,
    val isAdmin: Boolean = false,
    val isProfileHidden: Boolean = false,
    val areGoalsHidden: Boolean = false,
    val streakDays: Int = 14,
    val lastDailyLoginRewardDate: String = "",
    val notifications: NotificationSettings = NotificationSettings()
) {
    val age: Int
        get() {
            return try {
                val year = dob.split("-").firstOrNull()?.toIntOrNull() ?: 1997
                2026 - year
            } catch (e: Exception) {
                28
            }
        }

    fun canDoDailyPractices(): Boolean =
        userLevel.canAccessDailyPractices() || paidLevel.canDoDailyPractices()

    fun canPostDailyActivities(): Boolean =
        paidLevel.canPostDailyActivities() || isAdmin

    fun canViewOtherProfiles(): Boolean =
        paidLevel.canViewOtherProfiles() || isAdmin

    fun canLikePosts(): Boolean =
        userLevel.canLike() || paidLevel != PaidMemberLevel.NONE || isAdmin

    fun canCommentPosts(): Boolean =
        userLevel.canComment() || paidLevel >= PaidMemberLevel.LEVEL_2 || isAdmin

    fun canAskQuestions(): Boolean =
        userLevel.canAskQuestion() || paidLevel >= PaidMemberLevel.LEVEL_2 || isAdmin

    fun canAccessUnderstanding(): Boolean =
        userLevel.canAccessUnderstanding() || paidLevel != PaidMemberLevel.NONE || isAdmin
}
