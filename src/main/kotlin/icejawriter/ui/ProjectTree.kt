package icejawriter.ui

import icejawriter.I18n
import icejawriter.model.SceneStructure
import java.awt.BorderLayout
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.Transferable
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DropMode
import javax.swing.JComponent
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JScrollPane
import javax.swing.JTree
import javax.swing.TransferHandler
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreePath

/**
 * Payload of a project tree node (ФР-10).
 */
sealed class ProjectNode {

    /** "Описание" leaf — the novel description editor. */
    object Description : ProjectNode()

    /** One of the seven bible sections; [key] matches ProjectFormat entries. */
    data class Bible(val key: String) : ProjectNode()

    /** An act grouping node. */
    data class Act(val act: Int) : ProjectNode()

    /** A chapter inside an act. */
    data class Chapter(val act: Int, val chapter: Int) : ProjectNode()

    /** A scene leaf referencing the scene id. */
    data class SceneNode(val sceneId: String) : ProjectNode()
}

/**
 * Project navigator tree (ФР-10): description, bible, acts/chapters/scenes.
 * Supports drag&drop of scenes between chapters with automatic renumbering
 * (ФР-11) and context operations (ФР-12).
 */
class ProjectTree(
    private val context: EditorContext,
    private val onSelect: (ProjectNode) -> Unit,
) : JPanel(BorderLayout()) {

    private val root = DefaultMutableTreeNode("project")
    private val model = DefaultTreeModel(root)
    private val commands = SceneCommands(context)
    private val tree = JTree(model).apply {
        isRootVisible = false
        showsRootHandles = true
        dragEnabled = true
        dropMode = DropMode.ON
        transferHandler = SceneTransferHandler()
    }

    init {
        add(JScrollPane(tree), BorderLayout.CENTER)
        tree.cellRenderer = ProjectNodeRenderer()

        tree.addTreeSelectionListener {
            val node = selectedNode() ?: return@addTreeSelectionListener
            onSelect(node)
        }
        tree.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(event: MouseEvent) = maybeShowPopup(event)
            override fun mouseReleased(event: MouseEvent) = maybeShowPopup(event)
        })
    }

    /** The payload of the currently selected node, if any. */
    fun selectedNode(): ProjectNode? =
        (tree.lastSelectedPathComponent as? DefaultMutableTreeNode)?.userObject as? ProjectNode

    /** Rebuilds the whole tree from the open document (ФР-10). */
    fun rebuild() {
        root.removeAllChildren()
        val document = context.session.document
        if (document != null) {
            root.add(DefaultMutableTreeNode(ProjectNode.Description))

            val bible = DefaultMutableTreeNode(ProjectNode.Bible("bible"))
            BIBLE_SECTIONS.forEach { (key, _) ->
                bible.add(DefaultMutableTreeNode(ProjectNode.Bible(key)))
            }
            root.add(bible)

            for (act in SceneStructure.acts(document.scenes)) {
                val actNode = DefaultMutableTreeNode(ProjectNode.Act(act))
                for (chapter in SceneStructure.chapters(document.scenes, act)) {
                    val chapterNode = DefaultMutableTreeNode(ProjectNode.Chapter(act, chapter))
                    for (scene in SceneStructure.scenesOf(document.scenes, act, chapter)) {
                        chapterNode.add(DefaultMutableTreeNode(ProjectNode.SceneNode(scene.card.id)))
                    }
                    actNode.add(chapterNode)
                }
                root.add(actNode)
            }
        }
        model.reload()
        expandStructure()
    }

    /** Selects the tree node that represents the given scene. */
    fun selectScene(sceneId: String) {
        for (i in 0 until tree.rowCount) {
            val path = tree.getPathForRow(i)
            val node = path.lastPathComponent as? DefaultMutableTreeNode ?: continue
            val payload = node.userObject as? ProjectNode
            if (payload is ProjectNode.SceneNode && payload.sceneId == sceneId) {
                tree.selectionPath = path
                return
            }
        }
    }

    /** Selects the node of a bible section by its key. */
    fun selectBibleSection(key: String) {
        for (i in 0 until tree.rowCount) {
            val path = tree.getPathForRow(i)
            val payload = (path.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? ProjectNode
            if (payload is ProjectNode.Bible && payload.key == key) {
                tree.selectionPath = path
                return
            }
        }
    }

    /** Selects the description node. */
    fun selectDescription() {
        for (i in 0 until tree.rowCount) {
            val path = tree.getPathForRow(i)
            val payload = (path.lastPathComponent as? DefaultMutableTreeNode)?.userObject
            if (payload is ProjectNode.Description) {
                tree.selectionPath = path
                return
            }
        }
    }

    private fun expandStructure() {
        for (i in 0 until tree.rowCount) {
            val path = tree.getPathForRow(i)
            val payload = (path.lastPathComponent as? DefaultMutableTreeNode)?.userObject
            if (payload !is ProjectNode.SceneNode) {
                tree.expandPath(path)
            }
        }
    }

    /** Localized labels for the tree nodes (ИНТ-10). */
    private inner class ProjectNodeRenderer : DefaultTreeCellRenderer() {
        override fun getTreeCellRendererComponent(
            tree: JTree?,
            value: Any?,
            selected: Boolean,
            expanded: Boolean,
            leaf: Boolean,
            row: Int,
            hasFocus: Boolean,
        ): java.awt.Component {
            super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus)
            val payload = (value as? DefaultMutableTreeNode)?.userObject
            text = when (payload) {
                is ProjectNode.Description -> I18n.t("tree.description")
                is ProjectNode.Bible -> bibleLabel(payload.key)
                is ProjectNode.Act -> String.format(I18n.t("tree.act"), payload.act)
                is ProjectNode.Chapter -> String.format(I18n.t("tree.chapter"), payload.chapter)
                is ProjectNode.SceneNode -> sceneLabel(payload.sceneId)
                else -> ""
            }
            return this
        }
    }

    private fun bibleLabel(key: String): String {
        if (key == "bible") return I18n.t("tree.bible")
        val labelKey = BIBLE_SECTIONS.firstOrNull { it.first == key }?.second ?: return I18n.t("tree.bible")
        return I18n.t(labelKey)
    }

    private fun sceneLabel(sceneId: String): String {
        val scene = context.session.document?.scenes?.firstOrNull { it.card.id == sceneId } ?: return sceneId
        val title = scene.card.title.ifBlank { String.format(I18n.t("tree.scene"), scene.card.number) }
        return scene.card.number.toString() + ". " + title
    }

    // ------------------------------------------------------------------
    // context menu (ФР-12)
    // ------------------------------------------------------------------

    private fun maybeShowPopup(event: MouseEvent) {
        if (!event.isPopupTrigger) return
        val path = tree.getPathForLocation(event.x, event.y) ?: return
        tree.selectionPath = path
        val node = selectedNode() ?: return
        val menu = JPopupMenu()
        buildMenu(menu, node)
        if (menu.componentCount > 0) menu.show(tree, event.x, event.y)
    }

    private fun buildMenu(menu: JPopupMenu, node: ProjectNode) {
        when (node) {
            is ProjectNode.Act -> {
                menu.add(item("tree.add.chapter") { addChapter(node.act) })
                menu.add(item("tree.add.scene") { addScene(node.act, lastChapterOf(node.act)) })
            }
            is ProjectNode.Chapter -> {
                menu.add(item("tree.add.scene") { addScene(node.act, node.chapter) })
                menu.add(item("tree.add.chapter") { addChapter(node.act) })
            }
            is ProjectNode.SceneNode -> {
                menu.add(item("tree.duplicate.scene") { duplicateScene(node.sceneId) })
                menu.addSeparator()
                menu.add(item("tree.delete.scene") { deleteScene(node.sceneId) })
            }
            else -> {
                menu.add(item("tree.add.act") { addAct() })
            }
        }
    }

    private fun item(key: String, action: () -> Unit): JMenuItem =
        JMenuItem(I18n.t(key)).apply { addActionListener { action() } }

    private fun lastChapterOf(act: Int): Int {
        val scenes = context.session.document?.scenes ?: return 1
        return SceneStructure.chapters(scenes, act).maxOrNull() ?: 1
    }

    // Scene CRUD is shared with the main toolbar (SceneCommands, DRY).
    private fun addScene(act: Int, chapter: Int) {
        commands.addScene(act, chapter)
    }

    private fun addChapter(act: Int) {
        commands.addChapter(act)
    }

    private fun addAct() {
        commands.addAct()
    }

    private fun duplicateScene(sceneId: String) {
        commands.duplicateScene(sceneId)
    }

    private fun deleteScene(sceneId: String) {
        commands.deleteScene(sceneId, this)
    }

    // ------------------------------------------------------------------
    // drag & drop (ФР-11)
    // ------------------------------------------------------------------

    private inner class SceneTransferHandler : TransferHandler() {

        override fun getSourceActions(component: JComponent): Int = MOVE

        override fun createTransferable(component: JComponent): Transferable? {
            val node = selectedNode() as? ProjectNode.SceneNode ?: return null
            return StringTransferable(node.sceneId)
        }

        override fun canImport(support: TransferSupport): Boolean =
            support.isDrop && support.isDataFlavorSupported(SCENE_FLAVOR) && dropTarget(support) != null

        override fun importData(support: TransferSupport): Boolean {
            val sceneId = support.transferable.getTransferData(SCENE_FLAVOR) as? String ?: return false
            val target = dropTarget(support) ?: return false
            val document = context.session.document ?: return false
            val moved = SceneStructure.move(document.scenes, sceneId, target.act, target.chapter)
            if (moved == document.scenes) return false
            context.session.update { it.copy(scenes = moved) }
            context.structureEdited()
            context.openScene(sceneId)
            return true
        }

        /** Resolves the act/chapter the scene is dropped onto. */
        private fun dropTarget(support: TransferSupport): ChapterTarget? {
            val location = support.dropLocation as? JTree.DropLocation ?: return null
            val path = location.path ?: return null
            val payload = (path.lastPathComponent as? DefaultMutableTreeNode)?.userObject as? ProjectNode
            return when (payload) {
                is ProjectNode.Act -> ChapterTarget(payload.act, lastChapterOf(payload.act))
                is ProjectNode.Chapter -> ChapterTarget(payload.act, payload.chapter)
                is ProjectNode.SceneNode -> {
                    val scene = context.session.document?.scenes?.firstOrNull { it.card.id == payload.sceneId }
                    scene?.let { ChapterTarget(it.card.act, it.card.chapter) }
                }
                else -> null
            }
        }
    }

    private data class ChapterTarget(val act: Int, val chapter: Int)

    /** Minimal string transferable using a private JVM-local flavor. */
    private class StringTransferable(private val value: String) : Transferable {
        override fun getTransferDataFlavors(): Array<DataFlavor> = arrayOf(SCENE_FLAVOR)
        override fun isDataFlavorSupported(flavor: DataFlavor): Boolean = flavor == SCENE_FLAVOR
        override fun getTransferData(flavor: DataFlavor): Any = value
    }

    companion object {
        private val SCENE_FLAVOR = DataFlavor("application/x-iceja-scene;class=java.lang.String")

        /** Bible section keys and their tree label keys (ФР-10). */
        val BIBLE_SECTIONS: List<Pair<String, String>> = listOf(
            "world" to "tree.bible.world",
            "characters" to "tree.bible.characters",
            "chronology" to "tree.bible.chronology",
            "rules" to "tree.bible.rules",
            "storylines" to "tree.bible.storylines",
            "glossary" to "tree.bible.glossary",
            "style" to "tree.bible.style",
        )
    }
}