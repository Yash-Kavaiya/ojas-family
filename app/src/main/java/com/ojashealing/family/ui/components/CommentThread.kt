package com.ojashealing.family.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ojashealing.family.R
import com.ojashealing.family.data.social.Post
import com.ojashealing.family.data.social.groupReplies
import com.ojashealing.family.ui.theme.Spacing
import com.ojashealing.family.ui.thought.PostRow

/** Replies shown under a comment before "View more replies". */
private const val REPLIES_SHOWN = 2

/** Indent of a reply under its comment: the comment's avatar (32) plus its gap. */
private val ReplyIndent = 42.dp

/** Which threads the reader opened fully, kept across rotation and leaving the app. */
@Composable
fun rememberOpenThreads(): MutableState<Set<String>> = rememberSaveable(
    saver = listSaver(save = { it.value.toList() }, restore = { mutableStateOf(it.toSet()) })
) { mutableStateOf(emptySet()) }

/**
 * Comments as one-level threads for a LazyColumn: each top comment, then its replies indented
 * under it. [onReply] is null when the viewer may not write; [trailing] adds the report menu.
 */
fun LazyListScope.commentThreads(
    posts: List<Post>,
    openThreads: MutableState<Set<String>>,
    onReply: ((Post) -> Unit)?,
    trailing: (@Composable (Post) -> Unit)? = null,
    /** Document path of a comment, for its likes; null leaves likes off. */
    pathOf: ((Post) -> String)? = null,
    canLike: Boolean = false
) {
    groupReplies(posts).forEach { group ->
        val top = group.comment
        item(key = top.id, contentType = "comment") {
            PostRow(top, trailing = trailing?.let { t -> { t(top) } }, onReply = onReply?.let { { it(top) } }, likePath = pathOf?.invoke(top), canLike = canLike)
        }
        val open = top.id in openThreads.value
        val shown = if (open) group.replies else group.replies.take(REPLIES_SHOWN)
        shown.forEach { reply ->
            item(key = reply.id, contentType = "reply") {
                PostRow(
                    reply,
                    trailing = trailing?.let { t -> { t(reply) } },
                    onReply = onReply?.let { { it(reply) } },
                    avatarSize = 24,
                    indent = ReplyIndent,
                    // "@name" only when answering someone other than the comment's author
                    mentionName = reply.replyToName.takeIf { it.isNotEmpty() && it != top.author.name }.orEmpty(),
                    likePath = pathOf?.invoke(reply),
                    canLike = canLike
                )
            }
        }
        val hidden = group.replies.size - shown.size
        if (hidden > 0) {
            item(key = "${top.id}-more", contentType = "more") {
                Text(
                    pluralStringResource(R.plurals.view_more_replies, hidden, hidden),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = Spacing.l + ReplyIndent)
                        .clickable(role = Role.Button) { openThreads.value = openThreads.value + top.id }
                        .padding(vertical = Spacing.s, horizontal = Spacing.xs)
                )
            }
        }
    }
}
