package icejawriter.llm

import icejawriter.model.ProjectDocument
import icejawriter.model.Scene
import icejawriter.model.SceneCard
import icejawriter.model.SceneStatus
import icejawriter.model.Style
import icejawriter.project.TestProjects
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests the curated context builder (LLM-10, LLM-11, НФР-12): the card is
 * always present, the previous scene summary is included, and a tight token
 * budget drops the bible slice before the mandatory card.
 */
class ContextBuilderTest {

    private fun projectWithSummary(): ProjectDocument {
        val base = TestProjects.sampleProject()
        val scenes = base.scenes.map { scene ->
            if (scene.card.number == 1) {
                scene.copy(
                    card = scene.card.copy(summary = "Резюме первой сцены.", status = SceneStatus.APPROVED),
                    text = "Первый текст.",
                )
            } else {
                scene
            }
        }
        return base.copy(
            scenes = scenes,
            bible = base.bible.copy(style = Style(pov = "char-001", tense = "past", tone = "мрачный")),
        )
    }

    @Test
    fun includesCardPreviousSummaryAndStyle() {
        val document = projectWithSummary()
        val second = document.scenes.first { it.card.number == 2 }
        val context = ContextBuilder.build(document, second.card.id, tokenLimit = 16384)

        assertTrue(context.contains("Карточка сцены"))
        assertTrue(context.contains("Резюме первой сцены."))
        assertTrue(context.contains("Стиль"))
        assertTrue(context.contains("мрачный"))
    }

    @Test
    fun tightBudgetKeepsMandatoryCardAndDropsBible() {
        val document = projectWithSummary()
        val second = document.scenes.first { it.card.number == 2 }
        val full = ContextBuilder.build(document, second.card.id, tokenLimit = 16384)
        assertTrue(full.contains("Библия романа"))

        // A limit of one token leaves room only for the mandatory card.
        val trimmed = ContextBuilder.build(document, second.card.id, tokenLimit = 1)
        assertTrue(trimmed.contains("Карточка сцены"))
        assertFalse(trimmed.contains("Библия романа"))
        assertFalse(trimmed.contains("Резюме первой сцены."))
    }

    @Test
    fun checksContextContainsScenesWithinBudget() {
        val document = projectWithSummary()
        val ids = document.scenes.map { it.card.id }.toSet()
        val context = ContextBuilder.checksContext(document, ids, tokenLimit = 16384)
        assertTrue(context.contains("Карточка сцены №1"))
        assertTrue(context.contains("Карточка сцены №2"))
        assertTrue(context.contains("Первый текст."))
    }

    @Test
    fun estimateTokensGrowsWithText() {
        assertTrue(ContextBuilder.estimateTokens("a".repeat(400)) > ContextBuilder.estimateTokens("a".repeat(40)))
    }

    @Test
    fun unknownSceneYieldsEmptyContext() {
        val document = projectWithSummary()
        assertTrue(ContextBuilder.build(document, "scene-9999", 16384).isEmpty())
    }
}