package icejawriter.ui

import icejawriter.I18n
import icejawriter.model.Character
import icejawriter.model.Scene
import icejawriter.model.SceneStatus
import icejawriter.model.SceneStructure
import java.awt.BorderLayout
import java.awt.Color
import java.awt.FlowLayout
import java.time.Instant
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSpinner
import javax.swing.JTabbedPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SpinnerNumberModel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.undo.UndoManager

/**
 * Scene editor (ФР-40, ФР-41, ФР-50..ФР-52): a tabbed panel with the scene
 * card (all ФРМ-13 fields), the Markdown text with [NEW] highlighting and an
 * optional LLM draft tab added by the main window.
 */
class ScenePanel(private val context: EditorContext) : JPanel(BorderLayout()), ProjectEditor {

    /** Id of the scene currently loaded in the widgets, or null. */
    var sceneId: String? = null
        private set

    /**
     * True while the widgets are being populated programmatically; document
     * events fired during loading must not mark the project dirty.
     */
    private var loading = false

    private val tabs = JTabbedPane()

    // --- card widgets -------------------------------------------------
    private val actSpinner = JSpinner(SpinnerNumberModel(1, 1, 9999, 1))
    private val chapterSpinner = JSpinner(SpinnerNumberModel(1, 1, 9999, 1))
    private val numberLabel = JLabel("-")
    private val titleField = JTextField()
    private val statusCombo = JComboBoxStatus()
    private val povCombo = CharacterComboBox()
    private val locationField = JTextField()
    private val timeField = JTextField()
    private val goalArea = Theme.textArea(2, "scene.goal.tooltip")
    private val conflictArea = Theme.textArea(2, "scene.conflict.tooltip")
    private val twistArea = Theme.textArea(2, "scene.twist.tooltip")
    private val readerLearnsArea = Theme.textArea(2, "scene.readerLearns.tooltip")
    private val charactersLearnArea = Theme.textArea(2, "scene.charactersLearn.tooltip")
    private val entryArea = Theme.textArea(2, "scene.entry.tooltip")
    private val exitArea = Theme.textArea(2, "scene.exit.tooltip")
    private val continuityArea = Theme.textArea(3, "scene.continuityNotes.tooltip")
    private val summaryArea = Theme.textArea(4, "scene.summary.tooltip")
    private val wordTargetMinSpinner = JSpinner(SpinnerNumberModel(800, 1, 100000, 50))
    private val wordTargetMaxSpinner = JSpinner(SpinnerNumberModel(1500, 1, 100000, 50))
    private val wordCountLabel = JLabel("-")
    private val updatedAtLabel = JLabel("-")

    // --- text widgets -------------------------------------------------
    private val textArea = SceneTextArea()
    private val undoManager = UndoManager().apply { limit = 500 }
    private val wordsLabel = JLabel()
    private val charactersLabel = JLabel()
    private val targetLabel = JLabel()

    private var draftTabComponent: JComponent? = null

    init {
        textArea.document.addUndoableEditListener(undoManager)
        textArea.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) {
                textChanged()
            }

            override fun removeUpdate(e: DocumentEvent) {
                textChanged()
            }

            override fun changedUpdate(e: DocumentEvent) {
                textChanged()
            }
        })

        // Card tab.
        val form = Theme.formPanel()
        Theme.addRow(form, "scene.title", titleField, "scene.title.tooltip")
        val actChapterRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        actChapterRow.add(actSpinner)
        actChapterRow.add(JLabel(I18n.t("scene.chapter")))
        actChapterRow.add(chapterSpinner)
        actChapterRow.add(JLabel(I18n.t("scene.number")))
        actChapterRow.add(numberLabel)
        Theme.addRow(form, "scene.act", actChapterRow, "scene.act.tooltip")
        Theme.addRow(form, "scene.status", statusCombo, "scene.status.tooltip")
        Theme.addRow(form, "scene.pov", povCombo, "scene.pov.tooltip")
        Theme.addRow(form, "scene.location", locationField, "scene.location.tooltip")
        Theme.addRow(form, "scene.time", timeField, "scene.time.tooltip")
        Theme.addRow(form, "scene.goal", Theme.scroll(goalArea), "scene.goal.tooltip")
        Theme.addRow(form, "scene.conflict", Theme.scroll(conflictArea), "scene.conflict.tooltip")
        Theme.addRow(form, "scene.twist", Theme.scroll(twistArea), "scene.twist.tooltip")
        Theme.addRow(form, "scene.readerLearns", Theme.scroll(readerLearnsArea), "scene.readerLearns.tooltip")
        Theme.addRow(form, "scene.charactersLearn", Theme.scroll(charactersLearnArea), "scene.charactersLearn.tooltip")
        Theme.addRow(form, "scene.entry", Theme.scroll(entryArea), "scene.entry.tooltip")
        Theme.addRow(form, "scene.exit", Theme.scroll(exitArea), "scene.exit.tooltip")
        Theme.addRow(form, "scene.continuityNotes", Theme.scroll(continuityArea), "scene.continuityNotes.tooltip")
        Theme.addRow(form, "scene.summary", Theme.scroll(summaryArea), "scene.summary.tooltip")
        val targetRow = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        targetRow.add(wordTargetMinSpinner)
        targetRow.add(JLabel("—"))
        targetRow.add(wordTargetMaxSpinner)
        Theme.addRow(form, "scene.wordTarget", targetRow, "scene.wordTarget.tooltip")
        Theme.addRow(form, "scene.wordCount", wordCountLabel, "scene.wordCount.tooltip")
        Theme.addRow(form, "scene.updatedAt", updatedAtLabel, null)

        // Text tab.
        val textPanel = JPanel(BorderLayout())
        textPanel.add(JScrollPane(textArea), BorderLayout.CENTER)
        val stats = JPanel(FlowLayout(FlowLayout.LEFT, 10, 4))
        stats.add(wordsLabel)
        stats.add(charactersLabel)
        stats.add(targetLabel)
        textPanel.add(stats, BorderLayout.SOUTH)

        tabs.addTab(I18n.t("scene.tab.card"), Theme.scroll(form))
        tabs.addTab(I18n.t("scene.tab.text"), textPanel)

        add(tabs, BorderLayout.CENTER)

        listOf(wordTargetMinSpinner, wordTargetMaxSpinner).forEach { spinner ->
            spinner.addChangeListener { updateTextStats() }
        }
    }

    /** Adds (or replaces) the LLM draft tab of this scene (ФР-22). */
    fun setDraftComponent(component: JComponent, titleKey: String) {
        draftTabComponent?.let { tabs.remove(it) }
        draftTabComponent = component
        tabs.addTab(I18n.t(titleKey), component)
    }

    /** Shows the scene card tab (ФР-41). */
    fun showCardTab() {
        tabs.selectedIndex = 0
    }

    /** Shows the scene text tab (ФР-41). */
    fun showTextTab() {
        tabs.selectedIndex = 1
    }

    /** Shows the LLM draft tab when it has been attached. */
    fun showDraftTab() {
        if (tabs.tabCount > 2) tabs.selectedIndex = 2
    }

    /** The text area used by Find/Replace and the [NEW] highlighter (ФР-50). */
    fun textComponent(): SceneTextArea = textArea

    /** Undo/redo entry points used by the Edit menu (ФР-50). */
    fun undo() {
        if (undoManager.canUndo()) undoManager.undo()
    }

    fun redo() {
        if (undoManager.canRedo()) undoManager.redo()
    }

    fun canUndo(): Boolean = undoManager.canUndo()
    fun canRedo(): Boolean = undoManager.canRedo()

    /** Loads the given scene into the editor, committing the previous one. */
    fun showScene(sceneId: String) {
        if (this.sceneId != null && this.sceneId != sceneId) commitToProject()
        this.sceneId = sceneId
        loadFromDocument()
    }

    /**
     * Loads a scene WITHOUT committing the current widget values. Used when the
     * document was changed externally (accepted AI card, accepted draft): the
     * stale widgets must not overwrite the new document content.
     */
    fun loadScene(sceneId: String) {
        this.sceneId = sceneId
        loadFromDocument()
    }

    /** Reloads the widgets from the document for the current scene. */
    private fun loadFromDocument() {
        loading = true
        val scene = context.session.document?.scenes?.firstOrNull { it.card.id == sceneId }
        if (scene == null) {
            clear()
            return
        }
        val card = scene.card
        titleField.text = card.title
        actSpinner.value = card.act
        chapterSpinner.value = card.chapter
        numberLabel.text = card.number.toString()
        statusCombo.selectedStatus = card.status
        povCombo.setCharacters(context.characterChoices(), card.pov)
        locationField.text = card.location
        timeField.text = card.time
        goalArea.text = card.goal
        conflictArea.text = card.conflict
        twistArea.text = card.twist
        readerLearnsArea.text = card.readerLearns
        charactersLearnArea.text = card.charactersLearn
        entryArea.text = card.entry
        exitArea.text = card.exit
        continuityArea.text = card.continuityNotes.joinToString("\n")
        summaryArea.text = card.summary
        wordTargetMinSpinner.value = card.wordTargetMin
        wordTargetMaxSpinner.value = card.wordTargetMax
        wordCountLabel.text = card.wordCount.toString()
        updatedAtLabel.text = card.updatedAt
        textArea.text = scene.text
        textArea.refreshHighlights()
        updateTextStats()
        tabs.setTitleAt(0, I18n.t("scene.tab.card"))
        tabs.setTitleAt(1, I18n.t("scene.tab.text"))
        loading = false
    }

    private fun clear() {
        loading = true
        titleField.text = ""
        actSpinner.value = 1
        chapterSpinner.value = 1
        numberLabel.text = "-"
        statusCombo.selectedStatus = SceneStatus.DRAFT
        povCombo.setCharacters(context.characterChoices(), "")
        locationField.text = ""
        timeField.text = ""
        goalArea.text = ""
        conflictArea.text = ""
        twistArea.text = ""
        readerLearnsArea.text = ""
        charactersLearnArea.text = ""
        entryArea.text = ""
        exitArea.text = ""
        continuityArea.text = ""
        summaryArea.text = ""
        wordCountLabel.text = "-"
        updatedAtLabel.text = "-"
        textArea.text = ""
        updateTextStats()
        loading = false
    }

    /** Updates the word/character counters and the target indicator (ФР-52). */
    private fun updateTextStats() {
        val text = textArea.text
        val words = SceneStructure.wordCount(text)
        val min = wordTargetMinSpinner.value as Int
        val max = wordTargetMaxSpinner.value as Int
        wordsLabel.text = String.format(I18n.t("status.words"), words)
        charactersLabel.text = String.format(I18n.t("status.characters"), text.length)
        val (key, color) = when {
            words < min -> "scene.target.below" to Color(0xB06A00)
            words > max -> "scene.target.above" to Theme.ERROR_COLOR
            else -> "scene.target.ok" to Color(0x1B7A1B)
        }
        targetLabel.text = String.format(I18n.t("scene.target"), I18n.t(key), min, max)
        targetLabel.foreground = color
    }

    private fun textChanged() {
        updateTextStats()
        // Programmatic loading (scene switch, refresh) must not mark the
        // project dirty (ФР-03); only real user edits do.
        if (loading) return
        context.documentEdited()
    }

    override fun commitToProject() {
        val id = sceneId ?: return
        val document = context.session.document ?: return
        val existing = document.scenes.firstOrNull { it.card.id == id } ?: return

        val baseCard = existing.card.copy(
            act = actSpinner.value as Int,
            chapter = chapterSpinner.value as Int,
            title = titleField.text,
            status = statusCombo.selectedStatus,
            pov = povCombo.selectedItem?.toString().orEmpty(),
            location = locationField.text,
            time = timeField.text,
            goal = goalArea.text,
            conflict = conflictArea.text,
            twist = twistArea.text,
            readerLearns = readerLearnsArea.text,
            charactersLearn = charactersLearnArea.text,
            entry = entryArea.text,
            exit = exitArea.text,
            continuityNotes = lines(continuityArea),
            summary = summaryArea.text,
            wordTargetMin = wordTargetMinSpinner.value as Int,
            wordTargetMax = wordTargetMaxSpinner.value as Int,
            wordCount = SceneStructure.wordCount(textArea.text),
        )
        val text = textArea.text
        if (baseCard == existing.card && text == existing.text) return

        val updated = existing.copy(
            card = baseCard.copy(updatedAt = Instant.now().toString()),
            text = text,
        )
        context.session.updateIfChanged { doc ->
            doc.copy(scenes = doc.scenes.map { if (it.card.id == id) updated else it })
        }
        wordCountLabel.text = updated.card.wordCount.toString()
        updatedAtLabel.text = updated.card.updatedAt
    }

    override fun refresh() {
        if (sceneId == null || context.session.document?.scenes?.none { it.card.id == sceneId } == true) {
            sceneId = null
            clear()
        } else {
            loadFromDocument()
        }
    }

    private fun lines(area: JTextArea): List<String> =
        area.text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * Status combo that displays localized status names while storing the
     * enum (ФР-40).
     */
    private class JComboBoxStatus : javax.swing.JComboBox<SceneStatus>() {
        init {
            addItem(SceneStatus.DRAFT)
            addItem(SceneStatus.REVISED)
            addItem(SceneStatus.APPROVED)
            renderer = object : javax.swing.DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: javax.swing.JList<*>?,
                    value: Any?,
                    index: Int,
                    isSelected: Boolean,
                    cellHasFocus: Boolean,
                ): java.awt.Component {
                    val key = "scene.status." + (value as? SceneStatus ?: SceneStatus.DRAFT).wireName
                    return super.getListCellRendererComponent(list, I18n.t(key), index, isSelected, cellHasFocus)
                }
            }
        }

        var selectedStatus: SceneStatus
            get() = selectedItem as? SceneStatus ?: SceneStatus.DRAFT
            set(value) {
                selectedItem = value
            }
    }
}