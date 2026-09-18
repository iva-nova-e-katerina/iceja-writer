package icejawriter.model

import icejawriter.project.TestProjects
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests the scene structure operations (ФР-10..ФР-12, ФР-60): continuous
 * renumbering after moves, duplication and deletion.
 */
class SceneStructureTest {

    private fun scenes() = TestProjects.sampleProject().scenes

    @Test
    fun renumberingIsContinuousInNarrativeOrder() {
        val shuffled = listOf(
            Scene(SceneCard(id = "scene-0009", number = 9, act = 2, chapter = 1), "b"),
            Scene(SceneCard(id = "scene-0003", number = 3, act = 1, chapter = 2), "c"),
            Scene(SceneCard(id = "scene-0001", number = 1, act = 1, chapter = 1), "a"),
        )
        val renumbered = SceneStructure.renumbered(shuffled)
        assertEquals(listOf(1, 2, 3), renumbered.map { it.card.number })
        assertEquals(listOf("scene-0001", "scene-0002", "scene-0003"), renumbered.map { it.card.id })
        assertEquals(listOf("a", "c", "b"), renumbered.map { it.text })
    }

    @Test
    fun moveAppendsToTargetChapterAndRenumbers() {
        val moved = SceneStructure.move(scenes(), "scene-0002", targetAct = 1, targetChapter = 1)
        val second = moved.first { it.card.id == "scene-0001" }
        assertEquals(1, second.card.act)
        assertEquals(1, second.card.chapter)
        assertEquals(1, second.card.number)
        assertEquals(listOf(1, 2), moved.map { it.card.number }.sorted())
    }

    @Test
    fun moveAndLocateReportsTheNewId() {
        val result = SceneStructure.moveAndLocate(scenes(), "scene-0002", targetAct = 1, targetChapter = 1)
        val moved = result.scenes.first { it.card.id == result.movedSceneId }
        assertEquals(1, moved.card.act)
        assertEquals(1, moved.card.chapter)
        assertEquals(2, moved.card.number)
        assertEquals("scene-0002", result.movedSceneId)
    }

    @Test
    fun duplicateInsertsCopyAfterOriginal() {
        val duplicated = SceneStructure.duplicate(scenes(), "scene-0001", " (копия)")
        assertEquals(3, duplicated.size)
        val ordered = SceneStructure.ordered(duplicated)
        assertTrue(ordered[1].card.title.endsWith(" (копия)"))
        assertEquals(listOf(1, 2, 3), ordered.map { it.card.number })
    }

    @Test
    fun deleteRemovesSceneAndRenumbers() {
        val deleted = SceneStructure.delete(scenes(), "scene-0001")
        assertEquals(1, deleted.size)
        assertEquals("scene-0001", deleted.single().card.id)
        assertEquals(1, deleted.single().card.number)
    }

    @Test
    fun newChapterAndActGetNextNumbers() {
        val chapter = SceneStructure.newChapter(scenes(), act = 1)
        assertEquals(2, chapter.card.chapter)

        val act = SceneStructure.newAct(scenes())
        assertEquals(3, act.card.act)
        assertEquals(1, act.card.chapter)
    }

    @Test
    fun wordCountIgnoresWhitespace() {
        assertEquals(3, SceneStructure.wordCount("раз два\nтри  "))
        assertEquals(0, SceneStructure.wordCount("   \n  "))
    }
}