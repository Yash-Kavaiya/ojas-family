package com.example.data.model

data class UnderstandingTopic(
    val id: String,
    val title: String,
    val summary: String,
    val fullExplanation: String,
    val practicalSteps: List<String>,
    val chakraImpact: String,
    val iconName: String
)

data class ChatMessage(
    val id: String,
    val senderName: String,
    val isFromSwamiji: Boolean,
    val text: String,
    val timestamp: String
)

data class PersonalCallBooking(
    val id: String,
    val planName: String, // "1-Week Guidance (3-4 Calls)" or "Monthly Unlimited Anytime Calls"
    val scheduledTime: String,
    val status: String, // "Upcoming", "Completed", "Confirmed"
    val mentorName: String = "Swami Anand Swaroop"
)
