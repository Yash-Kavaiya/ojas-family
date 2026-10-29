package com.example

import com.example.data.repository.OjasRepository
import org.junit.Assert.*
import org.junit.Test

class MokshaLikePointsTest {

    private fun points(repo: OjasRepository) = repo.userProfile.value.mokshaPoints

    @Test
    fun unlikeAndReLike_todaysThought_earnsOnlyOnePoint() {
        val repo = OjasRepository()
        val today = repo.thoughts.value.first { it.dayOffset == 0 && !it.isLiked }
        val before = points(repo)

        repo.toggleLikeThought(today.id) // like
        repo.toggleLikeThought(today.id) // unlike
        repo.toggleLikeThought(today.id) // like again

        assertEquals(before + 1, points(repo))
        assertTrue(repo.thoughts.value.first { it.id == today.id }.isLiked)
    }

    @Test
    fun likingOlderThought_earnsNoPoint() {
        val repo = OjasRepository()
        val older = repo.thoughts.value.first { it.dayOffset > 0 && !it.isLiked }
        val before = points(repo)

        repo.toggleLikeThought(older.id)

        assertEquals(before, points(repo))
    }

    @Test
    fun likingYesterdaysQuestion_earnsNoPoint() {
        val repo = OjasRepository()
        val question = repo.questions.value.first { it.date == "Yesterday" }
        val before = points(repo)

        repo.toggleLikeQuestion(question.id)

        assertEquals(before, points(repo))
    }

    @Test
    fun likingTodaysDailyActivity_earnsOnePointAndSmallAnimation() {
        val repo = OjasRepository()
        val post = repo.dailyActivities.value.first { it.date.endsWith("hours ago") && !it.isLiked }
        val before = points(repo)

        repo.toggleLikeDailyActivity(post.id)
        repo.toggleLikeDailyActivity(post.id)
        repo.toggleLikeDailyActivity(post.id)

        assertEquals(before + 1, points(repo))
        assertFalse(repo.rewardEvent.value!!.isMilestone)
    }

    @Test
    fun dailyLoginReward_isMilestone() {
        val repo = OjasRepository()

        repo.checkDailyLoginReward("2099-01-01")

        assertTrue(repo.rewardEvent.value!!.isMilestone)
    }
}
