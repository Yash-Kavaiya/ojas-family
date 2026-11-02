package com.ojashealing.family.ui.content

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojashealing.family.R
import com.ojashealing.family.domain.Access
import com.ojashealing.family.domain.ContentItem
import com.ojashealing.family.domain.ContentSection
import com.ojashealing.family.domain.Permissions
import com.ojashealing.family.domain.Viewer
import com.ojashealing.family.domain.accessFor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SectionUi(val section: ContentSection, val items: List<Pair<ContentItem, Access>>)

@StringRes
fun ContentSection.label(): Int = when (this) {
    ContentSection.BlueLight -> R.string.section_blue_light
    ContentSection.QuickProtection -> R.string.section_quick_protection
    ContentSection.Morning -> R.string.section_morning
    ContentSection.Night -> R.string.section_night
    ContentSection.Yoga -> R.string.section_yoga
    ContentSection.Other -> R.string.section_other
    ContentSection.Understanding -> R.string.section_understanding
}

// Admins also see unpublished videos so they can switch them back on
private fun visible(items: List<ContentItem>, viewer: Viewer) =
    if (Permissions.isStaff(viewer)) items else items.filter { it.published }

/** Practice tab: sections 2.1 to 2.6 in spec order, empty ones left out. */
fun buildPractice(items: List<ContentItem>, viewer: Viewer): List<SectionUi> {
    val bySection = visible(items, viewer).groupBy { it.section }
    return ContentSection.entries
        .filter { it != ContentSection.Understanding }
        .mapNotNull { section ->
            bySection[section]?.takeIf { it.isNotEmpty() }
                ?.let { list -> SectionUi(section, list.map { it to accessFor(it, viewer) }) }
        }
}

fun buildUnderstanding(items: List<ContentItem>, viewer: Viewer): List<Pair<ContentItem, Access>> =
    visible(items, viewer).filter { it.section == ContentSection.Understanding }.map { it to accessFor(it, viewer) }

class ContentViewModel(content: Flow<List<ContentItem>>, viewer: Flow<Viewer?>) : ViewModel() {

    private val inputs = combine(content, viewer.filterNotNull()) { items, v -> items to v }

    // Null until the first snapshot arrives, so screens can show a loader instead of an empty state
    val practice: StateFlow<List<SectionUi>?> = inputs.map { (items, v) -> buildPractice(items, v) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val understanding: StateFlow<List<Pair<ContentItem, Access>>?> = inputs.map { (items, v) -> buildUnderstanding(items, v) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
