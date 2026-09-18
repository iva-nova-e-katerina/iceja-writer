package icejawriter.llm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests the change protocol parser (LLM-30): the draft must survive even when
 * the model formats the JSON block badly, and protocol items must be parsed
 * exactly.
 */
class ChangeProtocolParserTest {

    @Test
    fun separatesDraftAndProtocol() {
        val answer = """
            Она вошла в комнату и закрыла дверь.

            {"newFacts":["Герой носит кольцо"],"stateChanges":["Герой ранен"],"openQuestions":["Кто следит?"],"contradictions":[]}
        """.trimIndent()
        val result = ChangeProtocolParser.parse(answer)
        assertEquals("Она вошла в комнату и закрыла дверь.", result.draft)
        assertEquals(listOf("Герой носит кольцо"), result.protocol.newFacts)
        assertEquals(listOf("Герой ранен"), result.protocol.stateChanges)
        assertEquals(listOf("Кто следит?"), result.protocol.openQuestions)
        assertTrue(result.protocol.contradictions.isEmpty())
    }

    @Test
    fun parsesFencedJsonBlock() {
        val answer = """
            Текст сцены.
            ```json
            {"newFacts":["Факт"],"stateChanges":[],"openQuestions":[],"contradictions":["Противоречие"]}
            ```
        """.trimIndent()
        val result = ChangeProtocolParser.parse(answer)
        assertEquals("Текст сцены.", result.draft)
        assertEquals(listOf("Факт"), result.protocol.newFacts)
        assertEquals(listOf("Противоречие"), result.protocol.contradictions)
    }

    @Test
    fun keepsWholeAnswerWhenNoProtocol() {
        val answer = "Просто текст без протокола."
        val result = ChangeProtocolParser.parse(answer)
        assertEquals(answer, result.draft)
        assertTrue(result.protocol.isEmpty)
    }

    @Test
    fun handlesBracesInsideStrings() {
        val answer = """Черновик.
            {"newFacts":["Фраза со скобкой } внутри"],"stateChanges":[],"openQuestions":[],"contradictions":[]}"""
        val result = ChangeProtocolParser.parse(answer)
        assertEquals(listOf("Фраза со скобкой } внутри"), result.protocol.newFacts)
        assertEquals("Черновик.", result.draft)
    }

    @Test
    fun picksFilledProtocolAfterEmptyTemplate() {
        val answer = """
            Текст сцены.
            {"newFacts":[],"stateChanges":[],"openQuestions":[],"contradictions":[]}
            {"newFacts":["Настоящий факт"],"stateChanges":[],"openQuestions":[],"contradictions":[]}
        """.trimIndent()
        val result = ChangeProtocolParser.parse(answer)
        assertEquals(listOf("Настоящий факт"), result.protocol.newFacts)
        assertEquals("Текст сцены.", result.draft)
    }

    @Test
    fun skipsProseObjectsBeforeProtocol() {
        val answer = """Начало.
            { "note": "не протокол" }
            {"newFacts":["Настоящий факт"],"stateChanges":[],"openQuestions":[],"contradictions":[]}"""
        val result = ChangeProtocolParser.parse(answer)
        assertEquals(listOf("Настоящий факт"), result.protocol.newFacts)
        assertTrue(result.draft.startsWith("Начало."))
    }
}