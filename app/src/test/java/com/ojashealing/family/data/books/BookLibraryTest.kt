package com.ojashealing.family.data.books

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookLibraryTest {

    @Test fun `a Google Drive share link becomes its download link`() {
        val want = "https://drive.google.com/uc?export=download&confirm=t&id=1AbC_d-9"
        assertEquals(want, directPdfUrl("https://drive.google.com/file/d/1AbC_d-9/view?usp=sharing"))
        assertEquals(want, directPdfUrl("https://drive.google.com/open?id=1AbC_d-9"))
        assertEquals(want, directPdfUrl("https://drive.google.com/uc?export=download&id=1AbC_d-9"))
    }

    @Test fun `a Dropbox link asks for the file`() {
        assertEquals("https://www.dropbox.com/s/x/b.pdf?dl=1", directPdfUrl("https://www.dropbox.com/s/x/b.pdf?dl=0"))
        assertEquals("https://www.dropbox.com/s/x/b.pdf?dl=1", directPdfUrl("https://www.dropbox.com/s/x/b.pdf"))
    }

    @Test fun `an uploaded file and other links are kept as they are`() {
        val storage = "https://firebasestorage.googleapis.com/v0/b/x/o/books%2Fpdfs%2Fa.pdf?alt=media&token=t"
        assertEquals(storage, directPdfUrl(storage))
        assertEquals("https://ojashealing.com/book.pdf", directPdfUrl(" https://ojashealing.com/book.pdf "))
    }

    @Test fun `only a PDF counts, not a sign-in page`() {
        assertTrue(looksLikePdf("%PDF-1.7\n...".toByteArray()))
        assertTrue(looksLikePdf(byteArrayOf(0, 0) + "%PDF-1.4".toByteArray()))
        assertFalse(looksLikePdf("<!DOCTYPE html><html>Sign in</html>".toByteArray()))
        assertFalse(looksLikePdf(ByteArray(0)))
    }

    @Test fun `a new upload gets a new file name`() {
        val a = bookFileName("https://x/a.pdf?token=1")
        assertTrue(a.endsWith(".pdf"))
        assertEquals(a, bookFileName("https://x/a.pdf?token=1"))
        assertNotEquals(a, bookFileName("https://x/a.pdf?token=2"))
    }

    @Test fun `two pages side by side keep the cover alone`() {
        assertEquals(emptyList<List<Int>>(), spreads(0))
        assertEquals(listOf(listOf(0)), spreads(1))
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3)), spreads(4))
        assertEquals(listOf(listOf(0), listOf(1, 2), listOf(3, 4)), spreads(5))
        assertEquals(0, spreadOf(0))
        assertEquals(1, spreadOf(1))
        assertEquals(1, spreadOf(2))
        assertEquals(2, spreadOf(3))
    }

    @Test fun `page text is read sentence by sentence`() {
        // A blank line ends a sentence too; the page number "12" is not read
        val text = "Breathe in slowly and\nfeel the light\n\nThen rest! Is it calm?\n12\n"
        assertEquals(listOf("Breathe in slowly and feel the light", "Then rest!", "Is it calm?"), sentencesOf(text))
        assertEquals(emptyList<String>(), sentencesOf("  \n "))
    }

    @Test fun `Hindi sentences end at the danda`() {
        val s = sentencesOf("ध्यान करें। मन शांत होगा।")
        assertEquals(listOf("ध्यान करें।", "मन शांत होगा।"), s)
    }

    @Test fun `a word split over two lines is joined`() {
        assertEquals(listOf("A medi tation, then healing."), sentencesOf("A medi tation, then heal-\ning."))
    }

    @Test fun `very long sentences are cut to what the voice can read`() {
        val s = sentencesOf("a".repeat(2500))
        assertTrue(s.all { it.length <= 1000 })
        assertEquals(2500, s.sumOf { it.length })
    }

    @Test fun `the voice follows the script of the page`() {
        assertEquals("hi", speechLocaleFor("ध्यान करें, Om").language)
        assertEquals("gu", speechLocaleFor("ધ્યાન કરો").language)
        assertEquals("en", speechLocaleFor("Close your eyes").language)
    }
}
