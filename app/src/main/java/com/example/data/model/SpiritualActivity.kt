package com.example.data.model

enum class ActivityAccessLevel {
    OPEN_FOR_ALL,
    LEVEL_3_OR_PAID,
    LEVEL_4_OR_PAID
}

data class SpiritualActivityItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val category: String, // "General", "Morning Sadhana", "Night Sadhana", "Yoga & Pranayama", "Sacred Sounds"
    val durationMinutes: Int,
    val accessLevel: ActivityAccessLevel,
    val chakraFocus: String,
    val hasBoxBreathing: Boolean = false,
    val allowsReminderNotification: Boolean = false,
    val reminderTime: String? = null,
    val isCompletedToday: Boolean = false,
    val videoAudioType: String = "Guided Audio & Visual Meditation"
)
