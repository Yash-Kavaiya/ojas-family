package com.ojashealing.family.ui.thought

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojashealing.family.data.social.Author
import com.ojashealing.family.data.social.Post
import com.ojashealing.family.data.social.Thought
import com.ojashealing.family.data.social.ThoughtRepository
import com.ojashealing.family.data.social.todayIst
import com.ojashealing.family.data.user.UserProfile
import com.ojashealing.family.domain.LocalDateLike
import com.ojashealing.family.domain.Permissions
import com.ojashealing.family.domain.Role
import com.ojashealing.family.domain.Viewer
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ThoughtUi(
    val days: List<Thought>,
    val selected: Thought?,
    val canLike: Boolean,
    val canComment: Boolean,
    val canSchedule: Boolean,
    val canAttachImage: Boolean = false
)

/** Newest first; today's thought when there is one, otherwise the newest available. */
fun buildThoughtUi(thoughts: List<Thought>, selectedId: String?, viewer: Viewer, todayId: String): ThoughtUi {
    val days = thoughts.sortedByDescending { it.id }
    val selected = days.firstOrNull { it.id == selectedId } ?: days.firstOrNull { it.id == todayId } ?: days.firstOrNull()
    return ThoughtUi(
        days = days,
        selected = selected,
        canLike = Permissions.canLike(viewer),
        canComment = Permissions.canComment(viewer),
        canSchedule = viewer.role == Role.Admin,
        canAttachImage = Permissions.canAttachImage(viewer)
    )
}

fun UserProfile.asAuthor() = Author(uid, displayName, photoUrl, viewer.role)

@OptIn(ExperimentalCoroutinesApi::class)
class ThoughtViewModel(
    private val repo: ThoughtRepository,
    private val profile: Flow<UserProfile?>,
    private val uploadPostImage: suspend (uid: String, photo: Uri) -> String = { _, _ -> error("uploads not available") },
    private val uploadThoughtImage: suspend (thoughtId: String, photo: Uri) -> String = { _, _ -> error("uploads not available") }
) : ViewModel() {

    val todayId: String = todayIst().iso()
    private val selectedId = MutableStateFlow<String?>(null)
    private val me = profile.filterNotNull()
    private val meNow = profile.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val ui: StateFlow<ThoughtUi?> = combine(repo.observeThoughts(todayIst()), selectedId, me) { thoughts, sel, p ->
        buildThoughtUi(thoughts, sel, p.viewer, todayId)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val selectedThought = combine(ui, me) { u, p -> u?.selected?.id to p }

    val liked: StateFlow<Boolean> = selectedThought.flatMapLatest { (id, p) -> if (id == null) flowOf(false) else repo.observeLiked(id, p.uid) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val comments: StateFlow<List<Post>> = selectedThought.flatMapLatest { (id, p) ->
        if (id == null) flowOf(emptyList()) else repo.observeComments(id, p.viewer, p.uid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun select(id: String) {
        selectedId.value = id
    }

    fun toggleLike() {
        val id = ui.value?.selected?.id ?: return
        viewModelScope.launch {
            val p = meNow.value ?: return@launch
            repo.setLiked(id, p.uid, !liked.value)
        }
    }

    private val _sending = MutableStateFlow(false)
    val sending: StateFlow<Boolean> = _sending

    fun comment(text: String, onDone: () -> Unit) = comment(text, null, emptyList(), onDone)

    /** Uploads the photo first (if any); a failed upload leaves the draft in place. */
    fun comment(text: String, photo: Uri?, mentions: List<String>, onDone: () -> Unit) {
        val id = ui.value?.selected?.id ?: return
        if (text.isBlank() || _sending.value) return
        viewModelScope.launch {
            val p = meNow.value ?: return@launch
            _sending.value = true
            runCatching { photo?.let { uploadPostImage(p.uid, it) }.orEmpty() }
                .onSuccess { url -> repo.addComment(id, p.asAuthor(), text, url, mentions).onSuccess { onDone() } }
            _sending.value = false
        }
    }

    fun schedule(date: LocalDateLike, title: String, text: String, onDone: () -> Unit) = schedule(date, title, text, null) { ok -> if (ok) onDone() }

    fun schedule(date: LocalDateLike, title: String, text: String, image: Uri?, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val url = image?.let { runCatching { uploadThoughtImage(date.iso(), it) }.getOrNull() ?: return@launch onDone(false) }
            repo.schedule(date, title, text, url).onSuccess { onDone(true) }.onFailure { onDone(false) }
        }
    }
}
