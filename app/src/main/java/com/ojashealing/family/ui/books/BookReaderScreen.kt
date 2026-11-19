package com.ojashealing.family.ui.books

import android.graphics.Bitmap
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.ZoomIn
import androidx.compose.material.icons.outlined.ZoomOut
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ojashealing.family.R
import com.ojashealing.family.data.books.BookFileState
import com.ojashealing.family.data.books.BookLibrary
import com.ojashealing.family.data.books.BookPages
import com.ojashealing.family.data.books.BookSpeaker
import com.ojashealing.family.data.books.PageTextReader
import com.ojashealing.family.data.books.ReaderLayout
import com.ojashealing.family.data.books.ReaderPaper
import com.ojashealing.family.data.books.sentencesOf
import com.ojashealing.family.data.books.spreadOf
import com.ojashealing.family.data.books.spreads
import com.ojashealing.family.domain.Book
import com.ojashealing.family.ui.theme.Spacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The look of the paper behind the words. */
private fun paperBackground(p: ReaderPaper) = when (p) {
    ReaderPaper.Day -> Color(0xFFE9E7EE)
    ReaderPaper.Sepia -> Color(0xFFE3D5B8)
    ReaderPaper.Night -> Color(0xFF101114)
}

private fun paperFilter(p: ReaderPaper): ColorFilter? = when (p) {
    ReaderPaper.Day -> null
    // White paper turns warm cream; black words stay dark brown
    ReaderPaper.Sepia -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        0.95f, 0f, 0f, 0f, 0f,
        0f, 0.88f, 0f, 0f, 0f,
        0f, 0f, 0.72f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )))
    // Light words on a dark page, softer than pure white on black
    ReaderPaper.Night -> ColorFilter.colorMatrix(ColorMatrix(floatArrayOf(
        -0.82f, 0f, 0f, 0f, 225f,
        0f, -0.82f, 0f, 0f, 222f,
        0f, 0f, -0.82f, 0f, 214f,
        0f, 0f, 0f, 1f, 0f,
    )))
}

/**
 * A book read inside the app. The PDF is saved on the phone the first time, so afterwards it
 * opens without internet. Pages scroll, turn one at a time or show two side by side, on day,
 * sepia or night paper; the phone's voice can read them aloud.
 */
@Composable
fun BookReaderScreen(book: Book, library: BookLibrary, onBack: () -> Unit) {
    val states by library.states.collectAsState()
    val state = states[book.url] ?: library.state(book.url)
    var pages by remember(book.url) { mutableStateOf<BookPages?>(null) }
    var broken by remember(book.url) { mutableStateOf(false) }

    // Downloads (or finds) the book, then opens it
    LaunchedEffect(book.url, state) {
        if (pages != null || broken) return@LaunchedEffect
        val file = library.file(book.url)
        if (file == null) {
            if (state !is BookFileState.Downloading && state != BookFileState.Failed) library.download(book)
            return@LaunchedEffect
        }
        val opened = withContext(Dispatchers.IO) { BookPages.open(file) }
        if (opened == null) broken = true else pages = opened
    }
    DisposableEffect(book.url) { onDispose { pages?.close() } }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        val p = pages
        when {
            p != null -> Reader(book, p, library, onBack)
            else -> Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                    Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(
                    Modifier.fillMaxSize().padding(Spacing.xl),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    when {
                        broken -> {
                            Text(stringResource(R.string.books_open_failed), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                            TextButton(onClick = { library.remove(book.url); broken = false }) { Text(stringResource(R.string.action_retry)) }
                        }
                        state == BookFileState.Failed -> {
                            Text(stringResource(R.string.books_download_failed), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                            Button(onClick = { library.download(book) }, modifier = Modifier.padding(top = Spacing.m)) { Text(stringResource(R.string.action_retry)) }
                        }
                        else -> {
                            val pct = (state as? BookFileState.Downloading)?.percent ?: 0
                            Text(stringResource(R.string.books_downloading, pct), style = MaterialTheme.typography.titleMedium)
                            LinearProgressIndicator(
                                progress = { pct / 100f },
                                modifier = Modifier.padding(vertical = Spacing.m).widthIn(max = 280.dp).fillMaxWidth(),
                            )
                            Text(
                                stringResource(R.string.books_saved_offline), style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun Reader(book: Book, pages: BookPages, library: BookLibrary, onBack: () -> Unit) {
    val count = pages.pageCount
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var layout by remember { mutableStateOf(library.layout) }
    var paper by remember { mutableStateOf(library.paper) }
    var rate by remember { mutableFloatStateOf(library.speechRate) }
    var page by remember { mutableIntStateOf(library.lastPage(book.id).coerceIn(0, (count - 1).coerceAtLeast(0))) }
    var marks by remember { mutableStateOf(library.bookmarks(book.id)) }
    var chrome by remember { mutableStateOf(true) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var lookSheet by remember { mutableStateOf(false) }
    var marksSheet by remember { mutableStateOf(false) }
    var goTo by remember { mutableStateOf(false) }
    // A jump asked by the slider, the bookmarks or the reading voice
    var jump by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(Unit) { library.setPageCount(book.id, count) }
    LaunchedEffect(page) { library.setLastPage(book.id, page) }

    // The screen stays on while a book is open
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }

    // ---- reading aloud ----
    val speaker = remember { BookSpeaker(context) }
    val textReader = remember { PageTextReader() }
    val texts = remember { mutableStateMapOf<Int, String>() }
    var reading by remember { mutableStateOf(false) }
    var readingPage by remember { mutableIntStateOf(-1) }
    var readJob by remember { mutableStateOf<Job?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val voiceReady by speaker.ready.collectAsState()
    val sentence by speaker.sentence.collectAsState()
    DisposableEffect(Unit) { onDispose { speaker.shutdown(); textReader.close() } }
    val noWordsText = stringResource(R.string.books_no_words)

    fun stopReading() {
        reading = false
        readJob?.cancel()
        speaker.stop()
        readingPage = -1
    }

    fun readFrom(start: Int) {
        readJob?.cancel()
        speaker.stop()
        reading = true
        notice = null
        readJob = scope.launch {
            var at = start
            var emptyRun = 0
            while (reading && at < count) {
                readingPage = at
                if (at != page) jump = at
                val text = texts[at] ?: run {
                    val bmp: Bitmap = pages.render(at, 1600) ?: return@launch
                    textReader.read(bmp).also { texts[at] = it }
                }
                val sentences = sentencesOf(text)
                if (sentences.isEmpty()) {
                    // A picture page: go on to the next, but stop after a long run of them
                    notice = noWordsText.format(at + 1)
                    if (++emptyRun >= 5) break
                    at++
                    continue
                }
                emptyRun = 0
                notice = null
                val finished = kotlinx.coroutines.CompletableDeferred<Unit>()
                speaker.speak(sentences, rate) { finished.complete(Unit) }
                finished.await()
                at++
            }
            reading = false
            readingPage = -1
        }
    }

    val bg = paperBackground(paper)
    val filter = paperFilter(paper)
    Box(Modifier.fillMaxSize().background(bg)) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val boxWidth = maxWidth
            val widthPx = with(LocalDensity.current) { boxWidth.roundToPx() }
            key(layout) {
                when (layout) {
                    ReaderLayout.Scroll -> {
                        val list = rememberLazyListState(initialFirstVisibleItemIndex = page)
                        val shown by remember { derivedStateOf { list.firstVisibleItemIndex } }
                        LaunchedEffect(shown) { page = shown }
                        LaunchedEffect(jump) { jump?.let { list.animateScrollToItem(it); page = it; jump = null } }
                        val h = rememberScrollState()
                        Box(
                            Modifier.fillMaxSize()
                                .pinchZoom { z -> zoom = (zoom * z).coerceIn(1f, 3f) }
                                .horizontalScroll(h, enabled = zoom > 1f)
                        ) {
                            LazyColumn(
                                state = list,
                                modifier = Modifier.width(boxWidth * zoom).fillMaxSize(),
                                contentPadding = PaddingValues(top = 72.dp, bottom = 140.dp, start = 6.dp, end = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                items(count, key = { it }) { i ->
                                    PageImage(
                                        pages, i, (widthPx * zoom).toInt(), filter,
                                        Modifier.fillMaxWidth().clickable(
                                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                                        ) { chrome = !chrome },
                                    )
                                }
                            }
                        }
                    }
                    ReaderLayout.Pages -> {
                        val pager = rememberPagerState(initialPage = page) { count }
                        LaunchedEffect(pager.currentPage) { page = pager.currentPage }
                        LaunchedEffect(jump) { jump?.let { pager.animateScrollToPage(it); page = it; jump = null } }
                        var pageZoom by remember { mutableFloatStateOf(1f) }
                        HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = pageZoom <= 1.01f, key = { it }) { i ->
                            // Each page is a whole sheet of paper that lifts at the spine and turns over the next
                            Box(Modifier.fillMaxSize().pageTurn(pager, i, bg)) {
                                ZoomablePage(onTap = { chrome = !chrome }, onZoom = { pageZoom = it }) {
                                    PageImage(pages, i, widthPx, filter, Modifier.fillMaxWidth().padding(horizontal = 6.dp))
                                }
                            }
                        }
                    }
                    ReaderLayout.TwoPages -> {
                        val groups = remember(count) { spreads(count) }
                        val pager = rememberPagerState(initialPage = spreadOf(page).coerceAtMost(groups.lastIndex.coerceAtLeast(0))) { groups.size }
                        LaunchedEffect(pager.currentPage) { page = groups.getOrNull(pager.currentPage)?.first() ?: 0 }
                        LaunchedEffect(jump) { jump?.let { pager.animateScrollToPage(spreadOf(it)); page = it; jump = null } }
                        var pageZoom by remember { mutableFloatStateOf(1f) }
                        HorizontalPager(pager, Modifier.fillMaxSize(), userScrollEnabled = pageZoom <= 1.01f) { g ->
                            // An open book: the right page turns over the spine and its back lands as the next left page.
                            // The cover lies on the right, a last odd page on the left, as in a real book.
                            val group = groups[g]
                            val left = if (group.size == 2) group[0] else group.single().takeIf { g > 0 }
                            val right = if (group.size == 2) group[1] else group.single().takeIf { g == 0 }
                            Box(Modifier.fillMaxSize().spreadStack(pager, g)) {
                                ZoomablePage(onTap = { chrome = !chrome }, onZoom = { pageZoom = it }) {
                                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.weight(1f).leafTurn(pager, g, leftPage = true), contentAlignment = Alignment.CenterEnd) {
                                            if (left != null) PageImage(pages, left, widthPx / 2, filter, Modifier.fillMaxWidth().spineShade(leftPage = true))
                                        }
                                        Box(Modifier.weight(1f).leafTurn(pager, g, leftPage = false), contentAlignment = Alignment.CenterStart) {
                                            if (right != null) PageImage(pages, right, widthPx / 2, filter, Modifier.fillMaxWidth().spineShade(leftPage = false))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---- top bar ----
        AnimatedVisibility(chrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), shadowElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().statusBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                    Column(Modifier.weight(1f)) {
                        Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(R.string.books_page_of, page + 1, count), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    val marked = page in marks
                    IconButton(onClick = {
                        marks = if (marked) marks - page else marks + page
                        library.setBookmarks(book.id, marks)
                    }) {
                        Icon(
                            if (marked) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                            stringResource(if (marked) R.string.books_bookmark_remove else R.string.books_bookmark_add),
                            tint = if (marked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { marksSheet = true }) { Icon(Icons.Outlined.Bookmarks, stringResource(R.string.books_bookmarks)) }
                    IconButton(onClick = { if (reading) stopReading() else readFrom(page) }) {
                        Icon(
                            if (reading) Icons.Outlined.Stop else Icons.Outlined.RecordVoiceOver,
                            stringResource(if (reading) R.string.books_read_stop else R.string.books_read_aloud),
                            tint = if (reading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { lookSheet = true }) { Icon(Icons.Outlined.Tune, stringResource(R.string.books_look)) }
                }
            }
        }

        // ---- bottom: read-aloud bar and page slider ----
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()) {
            if (reading || notice != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(16.dp), shadowElevation = 4.dp,
                    modifier = Modifier.padding(horizontal = Spacing.m, vertical = Spacing.xs).fillMaxWidth(),
                ) {
                    Column(Modifier.padding(horizontal = Spacing.m, vertical = Spacing.s)) {
                        val line = when {
                            voiceReady == false -> stringResource(R.string.books_no_voice)
                            sentence.isNotEmpty() -> sentence
                            notice != null -> notice.orEmpty()
                            readingPage >= 0 -> stringResource(R.string.books_reading_page, readingPage + 1)
                            else -> ""
                        }
                        Text(line, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { val at = (readingPage.takeIf { it >= 0 } ?: page) - 1; if (at >= 0) readFrom(at) }) {
                                Icon(Icons.Outlined.SkipPrevious, stringResource(R.string.books_prev_page))
                            }
                            IconButton(onClick = { if (reading) stopReading() else readFrom(page) }) {
                                Icon(if (reading) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, stringResource(if (reading) R.string.books_read_pause else R.string.books_read_aloud))
                            }
                            IconButton(onClick = { val at = (readingPage.takeIf { it >= 0 } ?: page) + 1; if (at < count) readFrom(at) }) {
                                Icon(Icons.Outlined.SkipNext, stringResource(R.string.books_next_page))
                            }
                            Box(Modifier.weight(1f))
                            val speeds = listOf(0.75f, 1f, 1.25f, 1.5f)
                            TextButton(onClick = {
                                rate = speeds[(speeds.indexOf(rate).coerceAtLeast(0) + 1) % speeds.size]
                                library.speechRate = rate
                                if (reading) readFrom(readingPage.takeIf { it >= 0 } ?: page)
                            }) { Text(stringResource(R.string.books_speed_value, rate.toString().removeSuffix(".0"))) }
                            if (!reading) IconButton(onClick = { notice = null }) { Icon(Icons.Outlined.Stop, stringResource(R.string.action_close)) }
                        }
                    }
                }
            }
            AnimatedVisibility(chrome && count > 1, enter = fadeIn(), exit = fadeOut()) {
                Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f), shadowElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
                        if (layout == ReaderLayout.Scroll) {
                            IconButton(onClick = { zoom = (zoom - 0.5f).coerceAtLeast(1f) }, enabled = zoom > 1f) { Icon(Icons.Outlined.ZoomOut, stringResource(R.string.books_zoom_out)) }
                        }
                        var drag by remember { mutableStateOf<Float?>(null) }
                        Slider(
                            value = drag ?: page.toFloat(),
                            onValueChange = { drag = it },
                            onValueChangeFinished = { drag?.let { jump = it.toInt() }; drag = null },
                            valueRange = 0f..(count - 1).toFloat(),
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { goTo = true }) { Text("${(drag?.toInt() ?: page) + 1}/$count") }
                        if (layout == ReaderLayout.Scroll) {
                            IconButton(onClick = { zoom = (zoom + 0.5f).coerceAtMost(3f) }, enabled = zoom < 3f) { Icon(Icons.Outlined.ZoomIn, stringResource(R.string.books_zoom_in)) }
                        }
                    }
                }
            }
        }
    }

    if (lookSheet) ModalBottomSheet(onDismissRequest = { lookSheet = false }) {
        Column(Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Text(stringResource(R.string.books_look), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.books_layout), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                listOf(
                    ReaderLayout.Scroll to R.string.books_layout_scroll,
                    ReaderLayout.Pages to R.string.books_layout_pages,
                    ReaderLayout.TwoPages to R.string.books_layout_two,
                ).forEach { (l, label) ->
                    FilterChip(selected = layout == l, onClick = { layout = l; library.layout = l; zoom = 1f }, label = { Text(stringResource(label)) })
                }
            }
            Text(stringResource(R.string.books_paper), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                listOf(
                    ReaderPaper.Day to R.string.books_paper_day,
                    ReaderPaper.Sepia to R.string.books_paper_sepia,
                    ReaderPaper.Night to R.string.books_paper_night,
                ).forEach { (pp, label) ->
                    FilterChip(selected = paper == pp, onClick = { paper = pp; library.paper = pp }, label = { Text(stringResource(label)) })
                }
            }
            Text(stringResource(R.string.books_speed), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                listOf(0.75f, 1f, 1.25f, 1.5f).forEach { s ->
                    FilterChip(selected = rate == s, onClick = { rate = s; library.speechRate = s }, label = { Text(stringResource(R.string.books_speed_value, s.toString().removeSuffix(".0"))) })
                }
            }
            Text(stringResource(R.string.books_read_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    if (marksSheet) ModalBottomSheet(onDismissRequest = { marksSheet = false }) {
        Column(Modifier.padding(horizontal = Spacing.l).padding(bottom = Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(stringResource(R.string.books_bookmarks), style = MaterialTheme.typography.titleLarge)
            if (marks.isEmpty()) Text(stringResource(R.string.books_bookmarks_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            marks.sorted().forEach { m ->
                Row(
                    Modifier.fillMaxWidth().clip12().clickable { jump = m; marksSheet = false }.padding(vertical = Spacing.s, horizontal = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Bookmark, null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(R.string.books_page_of, m + 1, count), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Spacing.m).weight(1f))
                    TextButton(onClick = { marks = marks - m; library.setBookmarks(book.id, marks) }) { Text(stringResource(R.string.action_delete)) }
                }
            }
        }
    }

    if (goTo) {
        var typed by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { goTo = false },
            title = { Text(stringResource(R.string.books_go_to_page)) },
            text = {
                OutlinedTextField(
                    typed, { typed = it.filter(Char::isDigit).take(5) },
                    label = { Text("1 – $count") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    typed.toIntOrNull()?.let { jump = (it - 1).coerceIn(0, count - 1) }
                    goTo = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { goTo = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

private fun Modifier.clip12() = this.clip(RoundedCornerShape(12.dp))

/** Two fingers change the zoom; one finger is left to scroll the pages. */
private fun Modifier.pinchZoom(onZoom: (Float) -> Unit) = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.count { it.pressed } >= 2) {
                val z = event.calculateZoom()
                if (z != 1f) {
                    onZoom(z)
                    event.changes.forEach { it.consume() }
                }
            }
        } while (event.changes.any { it.pressed })
    }
}

// ---- turning pages like paper ----

/** How far [page] is from resting in view: 0 at rest, below 0 while it is turned away, above 0 while it waits to be uncovered. */
private fun PagerState.turn(page: Int): Float = ((page - currentPage) - currentPageOffsetFraction).coerceIn(-1f, 1f)

// Seen from a little way off, so the lifted edge grows toward the reader without bending too far
private const val PageCamera = 14f

/**
 * One page as a sheet in a pile: the page being turned lifts at the spine (its left edge), swings
 * toward the reader and darkens as it leaves the light; the next page lies still beneath it, in its
 * shadow, until it is uncovered. Works the same for swipes, buttons and read aloud.
 */
@Composable
private fun Modifier.pageTurn(pager: PagerState, page: Int, paper: Color): Modifier {
    // The page being turned (forward or back) is drawn over the one beneath; read only when that changes
    val onTop by remember(pager, page) { derivedStateOf { pager.turn(page) < 0f } }
    return this
        .zIndex(if (onTop) 1f else 0f)
        .graphicsLayer {
            val t = pager.turn(page)
            // The pager slides pages sideways; hold every page in place so they stack like paper
            translationX = -t * size.width
            cameraDistance = PageCamera * density
            if (t < 0f) {
                transformOrigin = TransformOrigin(0f, 0.5f)
                rotationY = PageTurnSign * 90f * t
                alpha = if (t <= -0.999f) 0f else 1f
            } else {
                rotationY = 0f
                alpha = 1f
            }
        }
        .background(paper)
        .drawWithContent {
            drawContent()
            val t = pager.turn(page)
            if (t < 0f) {
                val k = -t
                // A fold of shadow at the spine, and the free edge turning away from the light
                drawRect(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.30f * k), 0.12f to Color.Transparent,
                        0.6f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.55f * k),
                    )
                )
            } else if (t > 0f) {
                // Beneath the lifted sheet: in its shadow, deepest on the side it still covers
                drawRect(Color.Black.copy(alpha = 0.22f * t))
                drawRect(Brush.horizontalGradient(0.3f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.30f * t)))
            }
        }
}

// Which way rotationY lifts a page toward the reader rather than into the screen
private const val PageTurnSign = 1f

/** Two pages side by side: hold each spread in place and draw the one whose sheet is in the air on top. */
@Composable
private fun Modifier.spreadStack(pager: PagerState, spread: Int): Modifier {
    // Until the sheet passes upright it belongs to the spread being left; after, to the one arriving
    val onTop by remember(pager, spread) { derivedStateOf { kotlin.math.abs(pager.turn(spread)) < 0.5f } }
    return this
        .zIndex(if (onTop) 1f else 0f)
        .graphicsLayer { translationX = -pager.turn(spread) * size.width }
}

/**
 * One half of a spread. Turning forward, the right page rises from the spine to upright; past that
 * the back of the same sheet (the next left page) comes down onto the left. Turning back is the same
 * in reverse. The halves lying still are shaded while the sheet hangs over them.
 */
private fun Modifier.leafTurn(pager: PagerState, spread: Int, leftPage: Boolean): Modifier = this
    .graphicsLayer {
        val t = pager.turn(spread)
        cameraDistance = PageCamera * density
        if (if (leftPage) t > 0f else t < 0f) {
            transformOrigin = TransformOrigin(if (leftPage) 1f else 0f, 0.5f)
            rotationY = PageTurnSign * 180f * t
            alpha = if (kotlin.math.abs(t) >= 0.5f) 0f else 1f
        } else {
            rotationY = 0f
            alpha = 1f
        }
    }
    .drawWithContent {
        drawContent()
        val t = pager.turn(spread)
        if (t == 0f) return@drawWithContent
        val k = kotlin.math.abs(t)
        if (if (leftPage) t > 0f else t < 0f) {
            // Darker the more the sheet stands up off the book (upright at k = 0.5), most at the binding
            val a = 0.32f * (k * 2f).coerceAtMost(1f)
            drawRect(
                if (leftPage) Brush.horizontalGradient(0f to Color.Black.copy(alpha = a * 0.6f), 1f to Color.Black.copy(alpha = a))
                else Brush.horizontalGradient(0f to Color.Black.copy(alpha = a), 1f to Color.Black.copy(alpha = a * 0.6f))
            )
        } else {
            // A lying page in the shadow the standing sheet casts, deepest at the binding
            val over = if (k < 0.5f) k * 2f else (1f - k) * 2f
            val shade = Color.Black.copy(alpha = 0.28f * over)
            drawRect(
                if (leftPage) Brush.horizontalGradient(0.45f to Color.Transparent, 1f to shade)
                else Brush.horizontalGradient(0f to shade, 0.55f to Color.Transparent)
            )
        }
    }

/** The soft dip of shadow where a page curves into the binding. */
private fun Modifier.spineShade(leftPage: Boolean): Modifier = drawWithContent {
    drawContent()
    val shade = Color.Black.copy(alpha = 0.18f)
    drawRect(
        if (leftPage) Brush.horizontalGradient(0.88f to Color.Transparent, 1f to shade)
        else Brush.horizontalGradient(0f to shade, 0.12f to Color.Transparent)
    )
}

/** One page (or spread) that zooms with two fingers or a double tap, and pans while zoomed. */
@Composable
private fun ZoomablePage(onTap: () -> Unit, onZoom: (Float) -> Unit, content: @Composable () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    Box(
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = {
                        scale = if (scale > 1f) 1f else 2f
                        if (scale == 1f) offset = androidx.compose.ui.geometry.Offset.Zero
                        onZoom(scale)
                    },
                )
            }
            .then(
                // While not zoomed, one-finger swipes go to the pager to turn the page
                Modifier.pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val fingers = event.changes.count { it.pressed }
                            if (fingers >= 2 || scale > 1.01f) {
                                val z = event.calculateZoom()
                                val pan = event.changes.first().let { it.position - it.previousPosition }
                                if (z == 1f && pan == androidx.compose.ui.geometry.Offset.Zero) continue
                                scale = (scale * z).coerceIn(1f, 4f)
                                offset = if (scale <= 1.01f) androidx.compose.ui.geometry.Offset.Zero else {
                                    val maxX = size.width * (scale - 1) / 2
                                    val maxY = size.height * (scale - 1) / 2
                                    androidx.compose.ui.geometry.Offset(
                                        (offset.x + pan.x).coerceIn(-maxX, maxX),
                                        (offset.y + pan.y).coerceIn(-maxY, maxY),
                                    )
                                }
                                onZoom(scale)
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
            )
            .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** A drawn page, with a plain sheet of the right shape while it is being drawn. */
@Composable
private fun PageImage(pages: BookPages, index: Int, widthPx: Int, filter: ColorFilter?, modifier: Modifier = Modifier) {
    val sheet = if (filter == null) Color.White else Color.Gray.copy(alpha = 0.25f)
    val aspect by produceState(1f / 1.414f, index) { value = pages.aspect(index) }
    val bitmap by produceState<Bitmap?>(null, index, widthPx) { value = pages.render(index, widthPx) }
    Box(modifier.aspectRatio(aspect.coerceIn(0.2f, 5f)).background(sheet), contentAlignment = Alignment.Center) {
        val b = bitmap
        if (b != null) Image(
            b.asImageBitmap(), contentDescription = stringResource(R.string.books_page_n, index + 1),
            contentScale = ContentScale.Fit, colorFilter = filter, modifier = Modifier.fillMaxSize(),
        ) else Text("${index + 1}", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
    }
}
