package com.ojashealing.family.data.social

// @mentions: finding them in text, the name being typed after "@", and putting a picked name in.

private fun Char.isWordChar() = isLetterOrDigit() || this == '_'

/**
 * Where each of [names] is mentioned in [text], as the range of "@Name". A name counts only as a
 * whole word ("@Ram" is not found in "@Ramesh"), and longer names win, so "@Ramesh Patel" is not
 * also "@Ramesh". An "@" right after a letter (an e-mail address) is not a mention. In text order.
 */
private fun mentionsIn(text: String, names: List<String>): List<Pair<IntRange, String>> {
    val found = mutableListOf<Pair<IntRange, String>>()
    for (name in names.filter { it.isNotBlank() }.distinct().sortedByDescending { it.length }) {
        val needle = "@$name"
        var from = 0
        while (true) {
            val at = text.indexOf(needle, from)
            if (at < 0) break
            val end = at + needle.length - 1
            from = at + 1
            if (at > 0 && text[at - 1].isWordChar()) continue
            if (end + 1 < text.length && text[end + 1].isWordChar()) continue
            if (found.any { (r, _) -> at <= r.last && end >= r.first }) continue
            found += (at..end) to name
        }
    }
    return found.sortedBy { it.first.first }
}

/** People named with @ in [text], out of those offered by the mention picker (at most 10, as the rules allow). */
fun mentionedUids(text: String, candidates: List<Author>): List<String> {
    val byName = candidates.filter { it.name.isNotBlank() }.groupBy { it.name }
    return mentionsIn(text, byName.keys.toList())
        .flatMap { (_, name) -> byName[name].orEmpty().map { it.uid } }
        .distinct().take(10)
}

/** The ranges of "@Name" for each known name in [text], to colour them. */
fun mentionRanges(text: String, names: List<String>): List<IntRange> = mentionsIn(text, names).map { it.first }

/**
 * The part of a name typed after "@" just before [cursor] ("" right after the "@"), or null when the
 * member is not typing a mention: no "@", a space after it, or an "@" inside a word (an e-mail).
 */
fun mentionQuery(text: String, cursor: Int): String? {
    if (cursor < 1 || cursor > text.length) return null
    val before = text.substring(0, cursor)
    val at = before.lastIndexOf('@')
    if (at < 0) return null
    if (at > 0 && before[at - 1].isWordChar()) return null
    val typed = before.substring(at + 1)
    return typed.takeIf { t -> t.none { it.isWhitespace() || it == '@' } && t.length <= 30 }
}

/**
 * Puts "@[name] " at [cursor], replacing the "@..." being typed there if any. Returns the new text
 * and the cursor after it. No second space is added if one already follows.
 */
fun insertMention(text: String, cursor: Int, name: String): Pair<String, Int> {
    val c = cursor.coerceIn(0, text.length)
    val start = mentionQuery(text, c)?.let { c - it.length - 1 } ?: c
    val after = text.substring(c)
    val needsSpace = after.firstOrNull()?.isWhitespace() != true
    val head = text.substring(0, start)
    val lead = if (head.isNotEmpty() && !head.last().isWhitespace()) " " else ""
    val inserted = lead + "@" + name + if (needsSpace) " " else ""
    val newText = head + inserted + after
    val newCursor = head.length + inserted.length + if (needsSpace) 0 else 1
    return newText to newCursor
}

/** The people whose name starts with (or has a word starting with) [query], ignoring case. */
fun mentionMatches(query: String, people: List<Author>): List<Author> =
    if (query.isEmpty()) people
    else people.filter { a -> a.name.split(' ').any { it.startsWith(query, ignoreCase = true) } || a.name.startsWith(query, ignoreCase = true) }

// "@" at a word start, a name word, and a second word if it starts with a capital ("@Ramesh Patel")
private val typedMention = Regex("""(?<![\p{L}\p{N}_])@[\p{L}\p{N}_]+(?: \p{Lu}[\p{L}]*)?""")

/** Where "@Name" is written in [text], for colouring posts whose people are not known here. */
fun typedMentionRanges(text: String): List<IntRange> = typedMention.findAll(text).map { it.range }.toList()
