package com.ojashealing.family.ui.admin

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.FirebaseNetworkException
import com.ojashealing.family.R
import com.ojashealing.family.data.content.ContentRepository
import com.ojashealing.family.domain.ContentItem
import com.ojashealing.family.domain.ContentSection
import com.ojashealing.family.domain.Role
import com.ojashealing.family.domain.Viewer
import com.ojashealing.family.domain.youtubeIdFrom
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** An Ojas Healing channel playlist and the app section its videos belong to. */
data class ChannelPlaylist(val id: String, val name: String, val section: ContentSection)

// Playlists of youtube.com/@OjasHealing mapped to the design's sections (§7.3)
val ChannelPlaylists = listOf(
    ChannelPlaylist("PLKlWeums7nbXyVnUELcYYiO6I01Cj9sop", "प्रोटेक्शन विधि · Protection Meditation", ContentSection.QuickProtection),
    ChannelPlaylist("PLKlWeums7nbU7E8PuE3UWYE0z2TYHU_Kq", "All about Meditation", ContentSection.BlueLight),
    ChannelPlaylist("PLKlWeums7nbWqjJKim5JnK9t4rHaFWIqO", "महर्षि पतंजलि के योग सूत्र", ContentSection.Yoga),
    ChannelPlaylist("PLKlWeums7nbUpitKaBuZ2v77tk3Tb7kRU", "आध्यात्मिक ज्ञान कोष · Spiritual Encyclopedia", ContentSection.Understanding),
    ChannelPlaylist("PLKlWeums7nbXDvI2wc6bSD6a1_V54-8QY", "Body Chakras and Kundalini", ContentSection.Understanding),
    ChannelPlaylist("PLKlWeums7nbXCDZ2eXFMbkzWk3o7DPXyw", "Chakras Shorts", ContentSection.Understanding),
    ChannelPlaylist("PLKlWeums7nbUH7Vco70Ywtd008gAuGAIc", "QA by Anand Swaroop", ContentSection.Understanding),
    ChannelPlaylist("PLKlWeums7nbUh_zDR0_dPVFEOK0amA7xc", "शिव सूत्र · Shiva Sutras", ContentSection.Other),
    ChannelPlaylist("PLKlWeums7nbXjKQ-bycZgNAVHNwANtmm-", "विज्ञान भैरव तंत्र · Vigyan Bhairav Tantra", ContentSection.Other),
    ChannelPlaylist("PLKlWeums7nbUqA-GU2SSOGC1Az2ytRwUj", "Self Healing Course", ContentSection.Other)
)

fun showAdminEntry(viewer: Viewer): Boolean = viewer.role == Role.Admin

data class AdminMessage(@StringRes val text: Int, val count: Int? = null)

data class AdminContentUiState(
    val url: String = "",
    val section: ContentSection = ContentSection.QuickProtection,
    val busy: Boolean = false,
    val importing: String? = null,
    val message: AdminMessage? = null
)

class AdminContentViewModel(private val repo: ContentRepository) : ViewModel() {

    private val _state = MutableStateFlow(AdminContentUiState())
    val state: StateFlow<AdminContentUiState> = _state.asStateFlow()

    val items: StateFlow<List<ContentItem>> = repo.observe().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onUrlChange(value: String) = _state.update { it.copy(url = value, message = null) }
    fun onSectionChange(section: ContentSection) = _state.update { it.copy(section = section) }
    fun dismissMessage() = _state.update { it.copy(message = null) }

    fun addVideo() {
        val s = _state.value
        // Checked here too, so a typo never costs a server round trip
        if (youtubeIdFrom(s.url) == null) {
            _state.update { it.copy(message = AdminMessage(R.string.admin_invalid_url)) }
            return
        }
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            repo.addVideo(s.url.trim(), s.section)
                .onSuccess { _state.update { it.copy(busy = false, url = "", message = AdminMessage(R.string.admin_added)) } }
                .onFailure { e -> _state.update { it.copy(busy = false, message = AdminMessage(errorFor(e))) } }
        }
    }

    fun import(playlist: ChannelPlaylist) {
        if (_state.value.importing != null) return
        _state.update { it.copy(importing = playlist.id, message = null) }
        viewModelScope.launch {
            repo.importPlaylist(playlist.id, playlist.section)
                .onSuccess { n -> _state.update { it.copy(importing = null, message = AdminMessage(R.string.admin_imported, n)) } }
                .onFailure { e -> _state.update { it.copy(importing = null, message = AdminMessage(errorFor(e))) } }
        }
    }

    fun setPublished(id: String, published: Boolean) {
        viewModelScope.launch { repo.setPublished(id, published) }
    }

    fun delete(id: String) {
        viewModelScope.launch { repo.delete(id) }
    }

    @StringRes
    private fun errorFor(e: Throwable): Int = if (e is FirebaseNetworkException) R.string.error_offline else R.string.error_generic
}
