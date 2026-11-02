package com.ojashealing.family.ui.admin

import com.ojashealing.family.R
import com.ojashealing.family.data.content.ContentRepository
import com.ojashealing.family.domain.ContentItem
import com.ojashealing.family.domain.ContentSection
import com.ojashealing.family.domain.MemberLevel
import com.ojashealing.family.domain.Role
import com.ojashealing.family.domain.UserLevel
import com.ojashealing.family.domain.Viewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminContentViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    private class FakeRepo : ContentRepository {
        val added = mutableListOf<Pair<String, ContentSection>>()
        override fun observe(): Flow<List<ContentItem>> = flowOf(emptyList())
        override suspend fun addVideo(url: String, section: ContentSection): Result<Unit> { added += url to section; return Result.success(Unit) }
        override suspend fun importPlaylist(playlistId: String, section: ContentSection) = Result.success(7)
        override suspend fun setPublished(id: String, published: Boolean) = Result.success(Unit)
        override suspend fun delete(id: String) = Result.success(Unit)
    }

    @Test fun addVideoRejectsInvalidUrlWithoutCallingServer() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = AdminContentViewModel(repo)
        vm.onUrlChange("https://example.com/video")
        vm.addVideo()
        advanceUntilIdle()
        assertTrue(repo.added.isEmpty())
        assertEquals(R.string.admin_invalid_url, vm.state.value.message?.text)
    }

    @Test fun addVideoSendsUrlAndSectionAndClearsField() = runTest(dispatcher) {
        val repo = FakeRepo()
        val vm = AdminContentViewModel(repo)
        vm.onUrlChange("https://youtu.be/Pg54NjjSvzY")
        vm.onSectionChange(ContentSection.Yoga)
        vm.addVideo()
        advanceUntilIdle()
        assertEquals(listOf("https://youtu.be/Pg54NjjSvzY" to ContentSection.Yoga), repo.added)
        assertEquals("", vm.state.value.url)
    }

    @Test fun importReportsCreatedCount() = runTest(dispatcher) {
        val vm = AdminContentViewModel(FakeRepo())
        vm.import(ChannelPlaylists.first())
        advanceUntilIdle()
        assertEquals(R.string.admin_imported, vm.state.value.message?.text)
        assertEquals(7, vm.state.value.message?.count)
    }

    @Test fun adminEntryHiddenForNonAdmins() {
        assertFalse(showAdminEntry(Viewer(UserLevel.L4, MemberLevel.M3, Role.Swamiji)))
        assertTrue(showAdminEntry(Viewer(UserLevel.L1, MemberLevel.None, Role.Admin)))
    }
}
