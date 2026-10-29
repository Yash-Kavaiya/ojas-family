package com.ojashealing.family.data.model

data class ThoughtComment(
    val id: String,
    val authorName: String,
    val authorLevel: String,
    val text: String,
    val date: String,
    val imageUrl: String? = null,
    val linkUrl: String? = null,
    val isApproved: Boolean = true
)

data class ThoughtOfDay(
    val id: String,
    val dateLabel: String, // e.g. "Today - 29 Oct", "Yesterday - 28 Oct"
    val dayOffset: Int, // 0 = today, 1 = yesterday, up to 7
    val title: String,
    val quote: String,
    val swamijiCommentary: String,
    val chakraAlignment: String, // e.g., "Anahata (Heart Chakra) - Green Energy"
    val mantra: String,
    val imageUrl: String? = null,
    val likesCount: Int = 0,
    val isLiked: Boolean = false,
    val comments: List<ThoughtComment> = emptyList(),
    val isScheduled: Boolean = false
)
