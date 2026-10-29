package com.example.data.model

data class Goal(
    val id: String,
    val title: String,
    val description: String,
    val category: String,
    val mokshaReward: Int,
    val isAchieved: Boolean = false,
    val progressDays: Int = 0,
    val targetDays: Int = 1,
    val proofImageUrl: String? = null,
    val proofNote: String? = null,
    val achievedDate: String? = null,
    val iconName: String = "flag"
)
