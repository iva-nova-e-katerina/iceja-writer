package icejawriter.ui

import icejawriter.I18n
import icejawriter.model.SceneStructure
import java.awt.Component
import javax.swing.JOptionPane

/**
 * Scene CRUD commands shared by the project tree context menu and the main
 * window toolbar (ФР-11, ФР-12): add scene / chapter / act, duplicate and
 * delete. All operations renumber the scenes continuously and open the
 * affected scene in the editor.
 */
class SceneCommands(private val context: EditorContext) {

    /** Adds a scene to the end of the novel (used by the toolbar). */
    fun addSceneAtEnd(): String? {
        val document = context.session.document ?: return null
        val last = SceneStructure.ordered(document.scenes).lastOrNull()
        return addScene(last?.card?.act ?: 1, last?.card?.chapter ?: 1)
    }

    /** Adds a scene to the given act/chapter and opens it. */
    fun addScene(act: Int, chapter: Int): String? {
        val document = context.session.document ?: return null
        val scene = SceneStructure.newScene(document.scenes, act, chapter)
        context.session.update { it.copy(scenes = SceneStructure.renumbered(it.scenes + scene)) }
        context.structureEdited()
        context.openScene(scene.card.id)
        return scene.card.id
    }

    /** Adds a scene in a new chapter of the given act and opens it. */
    fun addChapter(act: Int): String? {
        val document = context.session.document ?: return null
        val scene = SceneStructure.newChapter(document.scenes, act)
        context.session.update { it.copy(scenes = SceneStructure.renumbered(it.scenes + scene)) }
        context.structureEdited()
        context.openScene(scene.card.id)
        return scene.card.id
    }

    /** Adds a scene in a new act and opens it. */
    fun addAct(): String? {
        val document = context.session.document ?: return null
        val scene = SceneStructure.newAct(document.scenes)
        context.session.update { it.copy(scenes = SceneStructure.renumbered(it.scenes + scene)) }
        context.structureEdited()
        context.openScene(scene.card.id)
        return scene.card.id
    }

    /**
     * Duplicates the scene (card + text) and opens the copy (ФР-12). Returns
     * the id of the copy, or null when the scene does not exist.
     */
    fun duplicateScene(sceneId: String): String? {
        val document = context.session.document ?: return null
        if (document.scenes.none { it.card.id == sceneId }) return null
        val scenes = SceneStructure.duplicate(document.scenes, sceneId, I18n.t("tree.copySuffix"))
        context.session.update { it.copy(scenes = scenes) }
        context.structureEdited()
        val ordered = SceneStructure.ordered(scenes)
        val index = ordered.indexOfFirst { it.card.id == sceneId }
        val copy = ordered.getOrNull(index + 1) ?: return null
        context.openScene(copy.card.id)
        return copy.card.id
    }

    /** Deletes the scene after a confirmation dialog (ФР-12). */
    fun deleteScene(sceneId: String, parent: Component?) {
        val document = context.session.document ?: return
        val scene = document.scenes.firstOrNull { it.card.id == sceneId } ?: return
        val answer = JOptionPane.showConfirmDialog(
            parent,
            String.format(I18n.t("tree.delete.scene.confirm"), scene.card.title.ifBlank { scene.card.id }),
            I18n.t("tree.delete.scene"),
            JOptionPane.YES_NO_OPTION,
        )
        if (answer != JOptionPane.YES_OPTION) return
        context.session.update { it.copy(scenes = SceneStructure.delete(it.scenes, sceneId)) }
        context.structureEdited()
    }
}