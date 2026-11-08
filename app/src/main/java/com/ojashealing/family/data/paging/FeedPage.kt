package com.ojashealing.family.data.paging

/** First page and every older page. Listeners must not outgrow this. */
const val FEED_PAGE_SIZE = 20

/**
 * The next page to append. [added] never contains an id already in [shownIds]
 * and never contains more than [pageSize] items. [cursor] is the last incoming
 * id that was considered, or null when [incoming] is shorter than a full page.
 */
data class PageSlice<T>(val added: List<T>, val cursor: String?)

fun <T> nextPage(
    shownIds: Set<String>,
    incoming: List<T>,
    pageSize: Int,
    idOf: (T) -> String,
): PageSlice<T> {
    require(pageSize > 0)
    val seen = HashSet<String>(shownIds.size + pageSize)
    seen.addAll(shownIds)
    val added = ArrayList<T>(minOf(pageSize, incoming.size))
    var lastScanned: String? = null
    for (item in incoming) {
        if (added.size >= pageSize) break
        val id = idOf(item)
        lastScanned = id
        if (!seen.add(id)) continue
        added.add(item)
    }
    val cursor = if (incoming.size < pageSize) null else lastScanned
    return PageSlice(added, cursor)
}
