package com.ojashealing.family.ui.adaptive

import androidx.compose.runtime.saveable.Saver

/** A text draft plus the list position that must survive recreation. */
data class DraftScroll(val draft: String, val index: Int, val offset: Int) {
    fun saved(): ArrayList<Any> = arrayListOf(draft, index, offset)

    companion object {
        fun restore(saved: List<Any?>): DraftScroll = DraftScroll(
            draft = saved.getOrNull(0) as? String ?: "",
            index = saved.getOrNull(1) as? Int ?: 0,
            offset = saved.getOrNull(2) as? Int ?: 0,
        )
    }
}

val DraftScrollSaver: Saver<DraftScroll, ArrayList<Any>> = Saver(
    save = { it.saved() },
    restore = { DraftScroll.restore(it) },
)
