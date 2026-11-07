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
}
