package com.ojashealing.family.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

/** One web address found in a piece of text: where it sits, and the address to open. */
data class FoundLink(val range: IntRange, val url: String)

// Common endings for addresses written without http or www, e.g. "ojashealing.com/book"
private const val TLDS = "com|in|org|net|io|co|app|me|info|edu|gov|ly|tv|be|gl|us|uk|link|page|site|online|store|shop|live|xyz|ai|dev"

private val LinkPattern = Regex(
    """(?i)\b(?:https?://[^\s<>"]+|www\.[^\s<>"]+|(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+(?:$TLDS)\b(?:/[^\s<>"]*)?)"""
)

/**
 * The web addresses in [text], as anyone would type them: with https://, with www., or just the
 * name like "ojashealing.com". Ending punctuation (a full stop, a comma, a closing bracket
 * without its opening one) stays outside the link. An email address is not a link.
 */
fun findLinks(text: String): List<FoundLink> = LinkPattern.findAll(text).mapNotNull { m ->
    val start = m.range.first
    // "name@ojas.com": the part after @ is an email, not a web address
    if (start > 0 && text[start - 1] == '@') return@mapNotNull null
    var shown = m.value
    while (shown.isNotEmpty()) {
        val last = shown.last()
        val unbalancedClose = last == ')' && shown.count { it == '(' } < shown.count { it == ')' }
        if (last in ".,;:!?'\"" || last == '’' || last == '”' || unbalancedClose) shown = shown.dropLast(1) else break
    }
    if (shown.isEmpty() || shown.endsWith("://")) return@mapNotNull null
    val url = if (shown.startsWith("http://", true) || shown.startsWith("https://", true)) shown else "https://$shown"
    FoundLink(start until start + shown.length, url)
}.toList()

/** [text] with its web addresses underlined in [linkColor]; tapping one opens it in its app or the browser. */
@Composable
fun linkified(text: String, linkColor: Color = MaterialTheme.colorScheme.primary): AnnotatedString {
    val uri = LocalUriHandler.current
    return remember(text, linkColor, uri) { buildLinked(AnnotatedString(text), linkColor) { runCatching { uri.openUri(it) } } }
}

/** The same for text that already has styles (an @mention, citation numbers); the styles are kept. */
@Composable
fun linkified(text: AnnotatedString, linkColor: Color = MaterialTheme.colorScheme.primary): AnnotatedString {
    val uri = LocalUriHandler.current
    return remember(text, linkColor, uri) { buildLinked(text, linkColor) { runCatching { uri.openUri(it) } } }
}

private fun buildLinked(raw: AnnotatedString, linkColor: Color, open: (String) -> Unit): AnnotatedString {
    // Every "@Name" is coloured too, so a mention stands out wherever the post is shown
    val mentions = com.ojashealing.family.data.social.typedMentionRanges(raw.text)
    val text = if (mentions.isEmpty()) raw else AnnotatedString.Builder(raw).apply {
        mentions.forEach { addStyle(SpanStyle(color = linkColor), it.first, it.last + 1) }
    }.toAnnotatedString()
    val links = findLinks(text.text)
    if (links.isEmpty()) return text
    val styles = TextLinkStyles(style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline))
    return AnnotatedString.Builder().apply {
        var at = 0
        links.forEach { link ->
            append(text.subSequence(at, link.range.first))
            // A phone with no app for the address does nothing rather than crash
            withLink(LinkAnnotation.Url(link.url, styles) { open(link.url) }) {
                append(text.subSequence(link.range.first, link.range.last + 1))
            }
            at = link.range.last + 1
        }
        append(text.subSequence(at, text.length))
    }.toAnnotatedString()
}
