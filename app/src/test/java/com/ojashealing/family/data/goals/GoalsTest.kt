package com.ojashealing.family.data.goals

import org.junit.Assert.assertEquals
import org.junit.Test

class GoalsTest {

    private fun g(id: String, order: Int, active: Boolean = true) =
        Goal(id, mapOf("en" to id), mapOf("en" to ""), GoalKind.Photo, 25, order, active)

    private val goals = listOf(g("b", 2), g("a", 1), g("c", 3), g("off", 0, active = false))

    @Test fun suggestsFirstOpenGoalByOrder() = assertEquals("a", suggestGoal(goals, emptyMap())?.id)

    @Test fun skipsApprovedAndSubmitted() {
        val progress = mapOf(
            "a" to GoalProgress("a", GoalStatus.Approved, "", 10),
            "b" to GoalProgress("b", GoalStatus.Submitted, "", 20)
        )
        assertEquals("c", suggestGoal(goals, progress)?.id)
    }

    @Test fun rejectedGoalIsSuggestedAgain() =
        assertEquals("a", suggestGoal(goals, mapOf("a" to GoalProgress("a", GoalStatus.Rejected, "", 5)))?.id)

    @Test fun achievedListedNewestFirst() {
        val progress = mapOf(
            "a" to GoalProgress("a", GoalStatus.Approved, "", 10),
            "c" to GoalProgress("c", GoalStatus.Approved, "", 30),
            "b" to GoalProgress("b", GoalStatus.Submitted, "", 20)
        )
        assertEquals(listOf("c", "a"), achieved(goals, progress).map { it.id })
    }

    @Test fun titleFallsBackToEnglish() = assertEquals("a", g("a", 1).titleIn("gu"))

    private fun sadhana(id: String, level: GoalLevel) =
        Goal(id, mapOf("en" to id), emptyMap(), GoalKind.Check, 20, 1, true, level, days = 7)

    @Test fun nextSadhanaLevelOpensAfterTwoGoalsOfTheLevelBefore() {
        val all = listOf(
            sadhana("e1", GoalLevel.Easy), sadhana("e2", GoalLevel.Easy), sadhana("e3", GoalLevel.Easy),
            sadhana("m1", GoalLevel.Medium), sadhana("h1", GoalLevel.Hard), g("photo", 1)
        )
        assertEquals(setOf(GoalLevel.Easy), unlockedLevels(all, emptySet()))
        // Photo activities do not count towards the sadhana levels
        assertEquals(setOf(GoalLevel.Easy), unlockedLevels(all, setOf("e1", "photo")))
        assertEquals(setOf(GoalLevel.Easy, GoalLevel.Medium), unlockedLevels(all, setOf("e1", "e3")))
        // Medium has a single goal, so that one opens Hard
        assertEquals(GoalLevel.entries.toSet(), unlockedLevels(all, setOf("e1", "e3", "m1")))
    }

    @Test fun dailyCountEndsOnceADayIsMissed() {
        val p = GoalProgress("e1", GoalStatus.Active, "", 0, streak = 4, lastDay = "2026-11-14")
        assertEquals(4, p.streakOn(today = "2026-11-14", yesterday = "2026-11-13"))
        assertEquals(4, p.streakOn(today = "2026-11-15", yesterday = "2026-11-14"))
        assertEquals(0, p.streakOn(today = "2026-11-16", yesterday = "2026-11-15"))
        assertEquals(true, p.tickedOn("2026-11-14"))
        assertEquals(false, p.tickedOn("2026-11-15"))
    }
}
