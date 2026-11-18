package com.ojashealing.family.ui.content

import com.ojashealing.family.data.practice.DownloadState
import com.ojashealing.family.data.practice.MediaType
import com.ojashealing.family.data.practice.PracticeMedia
import com.ojashealing.family.domain.ContentSection
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

class PracticeSessionDownloadTest {
    private val video = PracticeMedia(ContentSection.BlueLight, "https://x/o/practice%2Fblue_light.mp4?alt=media", MediaType.Video, 600, "")
    private val file = File("blue.mp4")

    @Test fun waitsWhileDownloadingAndNeverStreams() {
        val s = DownloadState.Downloading(40, waitingForWifi = false)
        assertEquals(SessionSource.Wait(s), sessionSource(video, s, null))
        // Even if the state says ready, there is nothing to play until the file is really there
        assertEquals(SessionSource.Wait(DownloadState.Downloading(0, true)), sessionSource(video, DownloadState.Ready, null))
    }

    @Test fun playsTheFileOnceItIsThere() {
        assertEquals(SessionSource.PlayFile(file), sessionSource(video, DownloadState.Ready, file))
    }

    @Test fun youTubeIsNotAffected() {
        val yt = PracticeMedia(ContentSection.QuickProtection, "KPRvRrJgmVk", MediaType.YouTube, 600, "")
        assertEquals(SessionSource.YouTube, sessionSource(yt, DownloadState.NotNeeded, null))
    }
}
