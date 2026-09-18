package icejawriter.llm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests the AI scene card proposal parser (card authoring mode): the model
 * answers with a JSON card inside free text and every field must be read
 * safely.
 */
class SceneCardProposalParserTest {

    @Test
    fun parsesCardInsideProse() {
        val answer = """
            Вот карточка следующей сцены.
            ```json
            {"title":"Встреча у моста","pov":"char-001","location":"мост","time":"вечер",
             "goal":"передать письмо","conflict":"страх","twist":"он знает имя",
             "readerLearns":"за героем следят","charactersLearn":"у незнакомца есть сведения",
             "entry":"герой у моста","exit":"герой уходит","continuityNotes":["дождь","восемь часов"],
             "summary":"","wordTargetMin":800,"wordTargetMax":1500,"act":2,"chapter":4}
            ```
        """.trimIndent()
        val proposal = SceneCardProposalParser.parse(answer)
        assertEquals("Встреча у моста", proposal!!.title)
        assertEquals("char-001", proposal.pov)
        assertEquals("передать письмо", proposal.goal)
        assertEquals(listOf("дождь", "восемь часов"), proposal.continuityNotes)
        assertEquals(2, proposal.act)
        assertEquals(4, proposal.chapter)
    }

    @Test
    fun parsesCardNestedInWrapperObject() {
        // The model sometimes wraps the payload: the outer object must not win
        // just because it contains the key as a substring.
        val answer = """{"card":{"title":"Вложенная","goal":"цель","conflict":"конфликт"}}"""
        val proposal = SceneCardProposalParser.parse(answer)
        assertEquals("Вложенная", proposal!!.title)
        assertEquals("цель", proposal.goal)
        assertEquals("конфликт", proposal.conflict)
    }

    @Test
    fun picksTheFilledCardAfterTheEmptyTemplate() {
        // Regression: the model prints the empty template first and the real
        // card second; the first object must not win.
        val answer = """
            Вот формат:
            {"title":"","pov":"","location":"","time":"","goal":"","conflict":"","twist":"","readerLearns":"","charactersLearn":"","entry":"","exit":"","continuityNotes":[],"summary":""}
            А вот карточка:
            {"title":"Настоящая сцена","goal":"найти письмо","conflict":"страх","twist":"он узнан","location":"мост"}
        """.trimIndent()
        val proposal = SceneCardProposalParser.parse(answer)
        assertEquals("Настоящая сцена", proposal!!.title)
        assertEquals("найти письмо", proposal.goal)
        assertEquals("страх", proposal.conflict)
    }

    @Test
    fun rejectsEmptyTemplateOnly() {
        val answer = """{"title":"","pov":"","location":"","time":"","goal":"","conflict":"","twist":"","readerLearns":"","charactersLearn":"","entry":"","exit":"","continuityNotes":[],"summary":""}"""
        assertNull(SceneCardProposalParser.parse(answer))
    }

    @Test
    fun returnsNullWhenNoCardPresent() {
        assertNull(SceneCardProposalParser.parse("Просто текст без JSON."))
        assertNull(SceneCardProposalParser.parse("""{"unrelated":"value"}"""))
    }

    @Test
    fun normalizesSwappedWordTargets() {
        val answer = """{"goal":"g","conflict":"c","wordTargetMin":1500,"wordTargetMax":800}"""
        val proposal = SceneCardProposalParser.parse(answer)!!
        assertEquals(800, proposal.wordTargetMin)
        assertEquals(1500, proposal.wordTargetMax)
    }

    @Test
    fun defaultsAreUsedForMissingFields() {
        val answer = """{"goal":"цель","conflict":"конфликт"}"""
        val proposal = SceneCardProposalParser.parse(answer)!!
        assertEquals("цель", proposal.goal)
        assertTrue(proposal.title.isEmpty())
        assertEquals(icejawriter.project.ProjectFormat.DEFAULT_WORD_TARGET_MIN, proposal.wordTargetMin)
        assertEquals(icejawriter.project.ProjectFormat.DEFAULT_WORD_TARGET_MAX, proposal.wordTargetMax)
        assertNull(proposal.act)
    }
}