package com.ojashealing.family.data.read

import com.ojashealing.family.domain.MemberLevel
import com.ojashealing.family.domain.Role
import com.ojashealing.family.domain.UserLevel
import com.ojashealing.family.domain.Viewer
import com.ojashealing.family.ui.content.canEditRead
import com.ojashealing.family.ui.content.speechChunks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadQaTest {
    private val qa = ReadQa(
        "q1", "प्रश्न", "उत्तर", null,
        questionEn = "Question", answerEn = "Answer", questionGu = "પ્રશ્ન", answerGu = "જવાબ",
    )

    @Test fun eachMemberReadsTheirLanguage() {
        assertEquals("प्रश्न", qa.questionIn("hi"))
        assertEquals("Answer", qa.answerIn("en"))
        assertEquals("પ્રશ્ન", qa.questionIn("gu"))
        assertEquals("જવાબ", qa.answerIn("gu"))
    }

    @Test fun aMissingTranslationShowsTheHindi() {
        val blank = qa.copy(answerGu = " ", questionEn = "")
        assertEquals("उत्तर", blank.answerIn("gu"))
        assertEquals("प्रश्न", blank.questionIn("en"))
    }

    @Test fun anotherPhoneLanguageShowsEnglish() {
        assertEquals("Question", qa.questionIn("fr"))
        assertEquals("प्रश्न", qa.copy(questionEn = "").questionIn("fr"))
    }

    @Test fun sameHindiKeepsCorrectedTranslations() {
        val t = ReadTranslations(" Fixed Q ", "Fixed A", "પ્ર", "જ")
        val f = readUpdateFields(qa, " प्रश्न ", "उत्तर", t)
        assertEquals("प्रश्न", f["question"])
        assertEquals("Fixed Q", f["question_en"])
        assertEquals("Fixed A", f["answer_en"])
        assertEquals("પ્ર", f["question_gu"])
        assertEquals("જ", f["answer_gu"])
    }

    @Test fun untouchedTranslationsAreNotWritten() {
        val same = ReadTranslations(qa.questionEn, qa.answerEn, qa.questionGu, " Fixed ")
        val f = readUpdateFields(qa, qa.question, qa.answer, same)
        for (k in listOf("question_en", "answer_en", "question_gu")) assertFalse(k in f)
        assertEquals("Fixed", f["answer_gu"])
    }

    @Test fun emptyFieldsOpenedBeforeTranslationAreNotWritten() {
        val before = qa.copy(questionEn = "", answerEn = "", questionGu = "", answerGu = "")
        val f = readUpdateFields(before, before.question, before.answer, ReadTranslations("", "", "", ""))
        for (k in listOf("question_en", "answer_en", "question_gu", "answer_gu")) assertFalse(k in f)
    }

    @Test fun hindiChangedByAnotherAdminIsAConflict() {
        assertTrue(readEditConflict(qa, qa.copy(answer = "दूसरा")))
        assertFalse(readEditConflict(qa, qa.copy(answerEn = "New translation")))
        assertFalse(readEditConflict(qa, null))
    }

    @Test fun changedHindiClearsTranslationsUntilTheNewOnesArrive() {
        val f = readUpdateFields(qa, "प्रश्न", "नया उत्तर", ReadTranslations("Q", "A", "પ્ર", "જ"))
        assertEquals("नया उत्तर", f["answer"])
        for (k in listOf("question_en", "answer_en", "question_gu", "answer_gu")) {
            assertTrue(k in f)
            assertEquals(null, f[k])
        }
    }

    @Test fun voiceLanguageFollowsTheScript() {
        assertEquals("en-IN", ttsLanguageFor("What is protection?"))
        assertEquals("hi-IN", ttsLanguageFor("सुरक्षा क्या है?"))
        assertEquals("gu-IN", ttsLanguageFor("રક્ષણ શું છે?"))
        // A Hindi answer with a few English words is still Hindi
        assertEquals("hi-IN", ttsLanguageFor("ध्यान में blue light की कल्पना करें और शांत रहें"))
        assertEquals("en-IN", ttsLanguageFor(""))
    }

    @Test fun longTextIsSplitAtSentenceEnds() {
        val text = (1..50).joinToString(" ") { "Sentence number $it is here." }
        val parts = speechChunks(text, 200)
        assertTrue(parts.all { it.length <= 200 })
        assertTrue(parts.dropLast(1).all { it.endsWith(".") })
        assertEquals(text.replace(" ", ""), parts.joinToString("").replace(" ", ""))
        assertEquals(listOf("Short."), speechChunks("  Short.  ", 200))
        // No break anywhere: still cut to size
        assertTrue(speechChunks("x".repeat(1000), 300).all { it.length <= 300 })
    }

    @Test fun bothPartsNeededWithinLimits() {
        assertTrue(validReadQa("Q?", "A."))
        assertFalse(validReadQa(" ", "A."))
        assertFalse(validReadQa("Q?", ""))
        assertFalse(validReadQa("Q".repeat(ReadQuestionMax + 1), "A"))
    }

    @Test fun onlyAdminsAndSwamijiWrite() {
        fun v(r: Role) = Viewer(UserLevel.L1, MemberLevel.None, r)
        assertTrue(canEditRead(v(Role.Admin)))
        assertTrue(canEditRead(v(Role.Swamiji)))
        assertFalse(canEditRead(v(Role.Moderator)))
        assertFalse(canEditRead(v(Role.Member)))
        assertFalse(canEditRead(null))
    }

    @Test fun firstQuestionIsOneNotYetDone() {
        val ids = listOf("a", "b", "c", "d")
        repeat(50) { seed ->
            val pick = ReadTicks.pickFirst(ids, done = setOf("a", "c"), random = kotlin.random.Random(seed))
            assertTrue(pick == "b" || pick == "d")
        }
        // All Done: still one of them, never nothing
        assertTrue(ReadTicks.pickFirst(ids, done = ids.toSet()) in ids)
        assertEquals(null, ReadTicks.pickFirst(emptyList(), done = emptySet()))
    }

    @Test fun firstQuestionChangesBetweenVisits() {
        val ids = (1..10).map { "q$it" }
        val picks = (0 until 30).map { ReadTicks.pickFirst(ids, emptySet(), kotlin.random.Random(it)) }.toSet()
        assertTrue(picks.size > 1)
    }
}
