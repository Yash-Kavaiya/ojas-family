package com.ojashealing.family

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ojashealing.family.data.books.BookFileState
import com.ojashealing.family.data.books.BookLibrary
import com.ojashealing.family.data.books.BookPages
import com.ojashealing.family.data.books.BookSpeaker
import com.ojashealing.family.data.books.PageTextReader
import com.ojashealing.family.data.books.bookFileName
import com.ojashealing.family.data.books.sentencesOf
import com.ojashealing.family.domain.Book
import com.ojashealing.family.ui.books.BookReaderScreen
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** The book reader on a real phone: drawing pages, reading their words, downloading and the screen. */
@RunWith(AndroidJUnit4::class)
class BookReaderDeviceTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** A small A4 book with large printed sentences on each page. */
    private fun makePdf(file: File, pages: Int) {
        val doc = PdfDocument()
        val paint = Paint().apply { textSize = 28f; isAntiAlias = true }
        repeat(pages) { i ->
            val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, i + 1).create())
            page.canvas.drawText("Breathe in slowly.", 60f, 140f, paint)
            page.canvas.drawText("Feel the healing light.", 60f, 190f, paint)
            page.canvas.drawText("Page ${i + 1}", 60f, 780f, paint)
            doc.finishPage(page)
        }
        file.parentFile?.mkdirs()
        file.outputStream().use { doc.writeTo(it) }
        doc.close()
    }

    @Test fun pagesAreDrawnAndTheirWordsRead() = runBlocking {
        val file = File(context.cacheDir, "t/book.pdf").also { makePdf(it, 3) }
        val pages = BookPages.open(file)!!
        assertEquals(3, pages.pageCount)
        assertEquals(595f / 842f, pages.aspect(0), 0.01f)
        val bmp = pages.render(1, 1600)!!
        assertEquals(1600, bmp.width)
        val text = PageTextReader().read(bmp)
        assertTrue("read: $text", text.contains("healing", ignoreCase = true))
        val sentences = sentencesOf(text)
        assertTrue("sentences: $sentences", sentences.any { it.contains("Breathe", ignoreCase = true) })
        pages.close()
    }

    @Test fun notAPdfIsNotOpened() {
        val file = File(context.cacheDir, "t/not.pdf").apply { parentFile?.mkdirs(); writeText("<html>Sign in</html>") }
        assertEquals(null, BookPages.open(file))
    }

    @Test fun aPublicPdfDownloadsAndAWebPageIsRefused() {
        val library = BookLibrary(context, File(context.cacheDir, "lib-${System.nanoTime()}"))
        val pdf = Book(id = "d1", title = "Dummy", url = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf")
        val page = Book(id = "d2", title = "Page", url = "https://www.example.com/")
        val done = CountDownLatch(2)
        var pdfFile: File? = null
        var pageFile: File? = File("x")
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            library.download(pdf) { pdfFile = it; done.countDown() }
            library.download(page) { pageFile = it; done.countDown() }
        }
        assertTrue("downloads finished", done.await(60, TimeUnit.SECONDS))
        assertNotNull(pdfFile)
        assertEquals(BookFileState.Downloaded, library.state(pdf.url))
        assertNotNull(BookPages.open(pdfFile!!)?.also { it.close() })
        assertEquals(null, pageFile)
        assertEquals(BookFileState.Failed, library.states.value[page.url])
        library.remove(pdf.url)
        assertEquals(BookFileState.NotDownloaded, library.state(pdf.url))
    }

    @Test fun thePhoneVoiceReadsSentences() = runBlocking {
        var speaker: BookSpeaker? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync { speaker = BookSpeaker(context) }
        val s = speaker!!
        withTimeout(20_000) { while (s.ready.value == null) kotlinx.coroutines.delay(100) }
        if (s.ready.value == true) {
            val finished = CompletableDeferred<Unit>()
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                s.speak(listOf("Breathe in slowly.", "Feel the healing light."), 1.5f) { finished.complete(Unit) }
            }
            withTimeout(30_000) { finished.await() }
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { s.shutdown() }
    }

    /** A saved 4-page book open in the reader, offline (nothing is downloaded). */
    private fun openReader(bookId: String): Pair<BookLibrary, Book> {
        val url = "https://example.com/$bookId-${System.nanoTime()}.pdf"
        makePdf(File(File(context.filesDir, "books"), bookFileName(url)), 4)
        val library = BookLibrary(context)
        val book = Book(id = bookId, title = "Healing Light", url = url)
        library.setLastPage(book.id, 0)
        library.setBookmarks(book.id, emptySet())
        compose.setContent { BookReaderScreen(book, library, onBack = {}) }
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Page 1 of 4").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Healing Light").assertExists()
        return library to book
    }

    @Test fun bookmarksAreKeptAndRemoved() {
        val (library, book) = openReader("marks-book")
        compose.onNodeWithContentDescription("Bookmark this page").performClick()
        assertEquals(setOf(0), library.bookmarks(book.id))
        compose.onNodeWithContentDescription("Bookmarks").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Delete").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Delete").performClick()
        compose.waitForIdle()
        assertEquals(emptySet<Int>(), library.bookmarks(book.id))
        library.remove(book.url)
    }

    @Test fun theReaderOpensASavedBookAndChangesItsLook() {
        val (library, book) = openReader("look-book")
        val url = book.url

        // Page by page, two pages and night paper
        compose.onNodeWithContentDescription("Reading look").performClick()
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Page by page").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Page by page").performClick()
        compose.onNodeWithText("Night").performClick()
        compose.onNodeWithText("Two pages").performClick()
        compose.waitForIdle()
        assertEquals(com.ojashealing.family.data.books.ReaderLayout.TwoPages, library.layout)
        assertEquals(com.ojashealing.family.data.books.ReaderPaper.Night, library.paper)
        library.layout = com.ojashealing.family.data.books.ReaderLayout.Scroll
        library.paper = com.ojashealing.family.data.books.ReaderPaper.Day
        library.remove(url)
    }
}
