package icejawriter.llm

import icejawriter.config.LlmSettings
import icejawriter.project.TestProjects
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests the continuity-check helpers of LlmService (LLM-40..LLM-42): the
 * issue parser and the scope resolution. No network calls are made here.
 */
class LlmServiceTest {

    private val service = LlmService(LlmSettings(), "ru")

    @Test
    fun parsesJsonIssueArray() {
        val answer = """[{"scene": 3, "problem": "Противоречие с библией"}, {"scene": "5", "problem": "Ошибка"}]"""
        val issues = service.parseCheckIssues(answer)
        assertEquals(2, issues.size)
        assertEquals(3, issues[0].sceneNumber)
        assertEquals("Противоречие с библией", issues[0].problem)
        assertEquals(5, issues[1].sceneNumber)
    }

    @Test
    fun returnsEmptyListForEmptyArray() {
        assertTrue(service.parseCheckIssues("[]").isEmpty())
    }

    @Test
    fun fallsBackToPlainLines() {
        val issues = service.parseCheckIssues("Проблема один\n- Проблема два")
        assertEquals(2, issues.size)
        assertEquals("Проблема один", issues[0].problem)
        assertEquals("Проблема два", issues[1].problem)
    }

    @Test
    fun resolvesScopes() {
        val document = TestProjects.sampleProject()
        val first = document.scenes.first { it.card.number == 1 }.card.id
        val second = document.scenes.first { it.card.number == 2 }.card.id

        assertEquals(setOf(first, second), service.scenesInScope(document, LlmService.SCOPE_NOVEL))
        assertEquals(setOf(first), service.scenesInScope(document, "act:1"))
        assertEquals(setOf(second), service.scenesInScope(document, "chapter:2:1"))
        assertTrue(service.scenesInScope(document, "act:99").isEmpty())
    }
}