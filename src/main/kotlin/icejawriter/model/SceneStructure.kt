package icejawriter.model

import icejawriter.project.ProjectFormat

/**
 * Domain operations on the scene list (ФР-10, ФР-11, ФР-12, ФР-60): the acts
 * and chapters are derived from the scenes themselves, because the .ijw format
 * stores act/chapter as attributes of a scene (ФРМ-13). All operations return
 * a new list; the caller replaces the project document.
 */
object SceneStructure {

    /** Scenes ordered by act, chapter and current number (the narrative order). */
    fun ordered(scenes: List<Scene>): List<Scene> =
        scenes.sortedWith(compareBy({ it.card.act }, { it.card.chapter }, { it.card.number }))

    /**
     * Renumbers the scenes 1..N in narrative order and rebuilds their ids
     * (`scene-0001` ...), which is required after any structural change
     * (ФР-11, ФР-60). The format requires a continuous numbering.
     */
    fun renumbered(scenes: List<Scene>): List<Scene> {
        var counter = 0
        return ordered(scenes).map { scene ->
            counter += 1
            scene.copy(
                card = scene.card.copy(
                    id = ProjectFormat.sceneFileBase(counter),
                    number = counter,
                ),
            )
        }
    }

    /** Next free scene number (max + 1). */
    fun nextNumber(scenes: List<Scene>): Int = (scenes.maxOfOrNull { it.card.number } ?: 0) + 1

    /** Distinct act numbers in narrative order. */
    fun acts(scenes: List<Scene>): List<Int> = ordered(scenes).map { it.card.act }.distinct()

    /** Distinct chapter numbers of one act in narrative order. */
    fun chapters(scenes: List<Scene>, act: Int): List<Int> =
        ordered(scenes).filter { it.card.act == act }.map { it.card.chapter }.distinct()

    /** Scenes of one chapter in narrative order. */
    fun scenesOf(scenes: List<Scene>, act: Int, chapter: Int): List<Scene> =
        ordered(scenes).filter { it.card.act == act && it.card.chapter == chapter }

    /** Creates a new empty scene appended to the given act and chapter (ФР-12). */
    fun newScene(scenes: List<Scene>, act: Int, chapter: Int): Scene {
        val number = nextNumber(scenes)
        return Scene(
            card = SceneCard(
                id = ProjectFormat.sceneFileBase(number),
                number = number,
                act = act,
                chapter = chapter,
            ),
        )
    }

    /** Creates a scene in a new chapter that follows the last chapter of [act]. */
    fun newChapter(scenes: List<Scene>, act: Int): Scene {
        val chapter = (chapters(scenes, act).maxOrNull() ?: 0) + 1
        return newScene(scenes, act, chapter)
    }

    /** Creates a scene in a new act that follows the last act. */
    fun newAct(scenes: List<Scene>): Scene {
        val act = (acts(scenes).maxOrNull() ?: 0) + 1
        return newScene(scenes, act, 1)
    }

    /**
     * Result of a move: the renumbered scenes and the new id of the moved
     * scene (its id changes together with its number, ФРМ-04).
     */
    data class MoveResult(val scenes: List<Scene>, val movedSceneId: String?)

    /**
     * Moves the scene to the end of the target chapter and renumbers the whole
     * list (ФР-11). Unknown ids leave the list unchanged.
     */
    fun move(scenes: List<Scene>, sceneId: String, targetAct: Int, targetChapter: Int): List<Scene> =
        moveAndLocate(scenes, sceneId, targetAct, targetChapter).scenes

    /**
     * Same as [move] but also reports the new id of the moved scene, so the UI
     * can keep showing the same scene after the automatic renumbering.
     */
    fun moveAndLocate(scenes: List<Scene>, sceneId: String, targetAct: Int, targetChapter: Int): MoveResult {
        val scene = scenes.firstOrNull { it.card.id == sceneId } ?: return MoveResult(scenes, null)
        val maxInTarget = scenes
            .filter { it.card.act == targetAct && it.card.chapter == targetChapter && it.card.id != sceneId }
            .maxOfOrNull { it.card.number } ?: 0
        val moved = scene.copy(
            card = scene.card.copy(act = targetAct, chapter = targetChapter, number = maxInTarget + 1),
        )
        val withMove = scenes.map { if (it.card.id == sceneId) moved else it }

        var movedSceneId: String? = null
        var counter = 0
        val renumbered = ordered(withMove).map { current ->
            counter += 1
            // Track the moved instance by identity before copying it.
            if (current === moved) movedSceneId = ProjectFormat.sceneFileBase(counter)
            current.copy(card = current.card.copy(id = ProjectFormat.sceneFileBase(counter), number = counter))
        }
        return MoveResult(renumbered, movedSceneId)
    }

    /**
     * Duplicates the scene (card + text) directly after the original and
     * renumbers everything (ФР-12). [copySuffix] is appended to the title so
     * the author can tell the copies apart.
     */
    fun duplicate(scenes: List<Scene>, sceneId: String, copySuffix: String): List<Scene> {
        val original = scenes.firstOrNull { it.card.id == sceneId } ?: return scenes
        val duplicate = original.copy(
            card = original.card.copy(
                title = original.card.title + copySuffix,
                number = original.card.number + 1,
            ),
        )
        return renumbered(scenes + duplicate)
    }

    /** Deletes the scene card and text and renumbers the rest (ФР-12). */
    fun delete(scenes: List<Scene>, sceneId: String): List<Scene> =
        renumbered(scenes.filterNot { it.card.id == sceneId })

    /** Total word count of all scene texts. */
    fun totalWordCount(scenes: List<Scene>): Int = scenes.sumOf { wordCount(it.text) }

    /** Word count of a text using the same rule as the status bar (ФР-52). */
    fun wordCount(text: String): Int = text.trim().split(WHITESPACE_REGEX).count { it.isNotBlank() }

    private val WHITESPACE_REGEX = Regex("\\s+")
}