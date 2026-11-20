package com.ojashealing.family.domain

// Test members for the plan table: free for some days, or on Silver or Gold for some days
private const val DAY_MS = 86_400_000L
private fun ago(days: Int) = System.currentTimeMillis() - days * DAY_MS

fun freeFor(days: Int, role: Role = Role.Member) =
    Viewer(UserLevel.L1, MemberLevel.None, role, joinedAtMs = ago(days))

fun onPlanFor(plan: Plan, days: Int) =
    Viewer(UserLevel.L1, MemberLevel.M1, Role.Member, plan, joinedAtMs = ago(days), planSinceMs = ago(days), planUntilMs = ago(-30))
