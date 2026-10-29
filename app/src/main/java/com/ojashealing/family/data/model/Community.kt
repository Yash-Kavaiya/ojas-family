package com.ojashealing.family.data.model

data class CommunityAnswer(
    val id: String,
    val questionId: String,
    val authorName: String,
    val authorRole: String, // "Swami Anand Swaroop", "Senior Mentor", "Community Sadhak"
    val isSwamijiAnswer: Boolean,
    val text: String,
    val date: String,
    val likesCount: Int = 0,
    val isLiked: Boolean = false
)

data class CommunityQuestion(
    val id: String,
    val title: String,
    val body: String,
    val authorName: String,
    val authorLevel: String,
    val date: String,
    val tags: List<String> = emptyList(),
    val answers: List<CommunityAnswer> = emptyList(),
    val likesCount: Int = 0,
    val isLiked: Boolean = false,
    val isSwamijiAnswered: Boolean = false,
    val imageUrl: String? = null
)

data class DailyActivityPost(
    val id: String,
    val authorName: String,
    val authorLevel: String, // e.g., "Paid Member Level 3"
    val authorAvatarUrl: String? = null,
    val title: String,
    val description: String,
    val category: String, // "Cooking", "Cleaning", "Exercise & Yoga", "Cloth Seva", "Tree Planting"
    val imageUrl: String? = null,
    val date: String,
    val likesCount: Int = 0,
    val isLiked: Boolean = false,
    val commentsCount: Int = 0
)

data class Announcement(
    val id: String,
    val title: String,
    val date: String,
    val tag: String, // "Live Satsang", "Retreat", "Energy Workshop", "Update"
    val body: String,
    val swamijiBlessing: String? = null
)
