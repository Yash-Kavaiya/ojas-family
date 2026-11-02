package com.ojashealing.family.ui.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ojashealing.family.data.social.Announcement
import com.ojashealing.family.data.social.CommunityRepository
import com.ojashealing.family.data.social.Post
import com.ojashealing.family.data.social.Question
import com.ojashealing.family.data.user.UserProfile
import com.ojashealing.family.domain.Permissions
import com.ojashealing.family.domain.Role
import com.ojashealing.family.domain.Viewer
import com.ojashealing.family.ui.thought.asAuthor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CommunityPermissions(val canAsk: Boolean, val canLike: Boolean, val canAnswer: Boolean, val canAnnounce: Boolean)

fun communityPermissions(viewer: Viewer) = CommunityPermissions(
    canAsk = Permissions.canAskQuestion(viewer),
    canLike = Permissions.canLike(viewer),
    canAnswer = Permissions.canComment(viewer),
    canAnnounce = viewer.role == Role.Admin
)

fun visibleQuestions(questions: List<Question>, blocked: Set<String>): List<Question> =
    questions.filter { it.author.uid !in blocked }

// Swamiji's and senior members' answers lead, then everyone else oldest first
fun orderAnswers(answers: List<Post>, blocked: Set<String>): List<Post> =
    answers.filter { it.author.uid !in blocked }
        .sortedWith(compareBy<Post>({ if (it.author.role == Role.Member) 1 else 0 }, { it.createdAt }))

@OptIn(ExperimentalCoroutinesApi::class)
class CommunityViewModel(private val repo: CommunityRepository, profile: Flow<UserProfile?>) : ViewModel() {

    private val me = profile.filterNotNull()
    private val meNow = profile.stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private val blocked = me.flatMapLatest { repo.observeBlocked(it.uid) }

    val permissions: StateFlow<CommunityPermissions?> = me.map { communityPermissions(it.viewer) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val questions: StateFlow<List<Question>?> = me.flatMapLatest { p ->
        combine(repo.observeQuestions(p.viewer, p.uid), blocked) { q, b -> visibleQuestions(q, b) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val announcements: StateFlow<List<Announcement>?> = repo.observeAnnouncements()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun answers(questionId: String): Flow<List<Post>> = me.flatMapLatest { p ->
        combine(repo.observeAnswers(questionId, p.viewer, p.uid), blocked) { a, b -> orderAnswers(a, b) }
    }

    fun liked(questionId: String): Flow<Boolean> = me.flatMapLatest { repo.observeLiked(questionId, it.uid) }

    private fun withMe(block: suspend (UserProfile) -> Unit) {
        val p = meNow.value ?: return
        viewModelScope.launch { block(p) }
    }

    fun setLiked(questionId: String, liked: Boolean) = withMe { repo.setLiked(questionId, it.uid, liked) }
    fun ask(title: String, body: String, onDone: () -> Unit) = withMe { p -> repo.ask(p.asAuthor(), title, body).onSuccess { onDone() } }
    fun answer(questionId: String, text: String, onDone: () -> Unit) = withMe { p -> repo.answer(questionId, p.asAuthor(), text).onSuccess { onDone() } }
    fun announce(title: String, body: String, onDone: () -> Unit) = withMe { repo.postAnnouncement(title, body).onSuccess { onDone() } }
    fun report(path: String, reason: String, onDone: () -> Unit) = withMe { p -> repo.report(p.uid, path, reason).onSuccess { onDone() } }
    fun block(otherUid: String) = withMe { p -> repo.block(p.uid, otherUid) }
}
