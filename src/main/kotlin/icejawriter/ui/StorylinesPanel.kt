package icejawriter.ui

import icejawriter.I18n
import icejawriter.model.Storyline
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.ListSelectionModel

/**
 * Storylines editor (ФР-33): list of plot lines A/B/C with the fields of
 * ФРМ-10 (setup, secrets, foreshadowing, resolution, notes).
 */
class StorylinesPanel(private val context: EditorContext) : JPanel(BorderLayout()), ProjectEditor {

    private var storylines: List<Storyline> = emptyList()
    private var selectedIndex: Int = -1
    private var updatingSelection: Boolean = false

    private val listModel = DefaultListModel<String>()
    private val storylineList = JList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        visibleRowCount = 12
    }

    private val idCombo = JComboBox(arrayOf("A", "B", "C")).apply { isEditable = true }
    private val nameField = JTextField()
    private val setupArea = Theme.textArea(4, "bible.storylines.setup.tooltip")
    private val secretsArea = Theme.textArea(3, "bible.storylines.secrets.tooltip")
    private val foreshadowingArea = Theme.textArea(3, "bible.storylines.foreshadowing.tooltip")
    private val resolutionArea = Theme.textArea(4, "bible.storylines.resolution.tooltip")
    private val notesArea = Theme.textArea(3, "bible.storylines.notes.tooltip")

    init {
        val left = JPanel(BorderLayout())
        left.add(JScrollPane(storylineList), BorderLayout.CENTER)
        val listButtons = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4))
        val addButton = JButton(I18n.t("bible.storylines.add"))
        val deleteButton = JButton(I18n.t("bible.storylines.delete"))
        addButton.addActionListener { addStoryline() }
        deleteButton.addActionListener { deleteStoryline() }
        listButtons.add(addButton)
        listButtons.add(deleteButton)
        left.add(listButtons, BorderLayout.SOUTH)

        val form = Theme.formPanel()
        Theme.addRow(form, "bible.storylines.id", idCombo, "bible.storylines.id.tooltip")
        Theme.addRow(form, "bible.storylines.name", nameField, "bible.storylines.name.tooltip")
        Theme.addRow(form, "bible.storylines.setup", Theme.scroll(setupArea), "bible.storylines.setup.tooltip")
        Theme.addRow(form, "bible.storylines.secrets", Theme.scroll(secretsArea), "bible.storylines.secrets.tooltip")
        Theme.addRow(form, "bible.storylines.foreshadowing", Theme.scroll(foreshadowingArea), "bible.storylines.foreshadowing.tooltip")
        Theme.addRow(form, "bible.storylines.resolution", Theme.scroll(resolutionArea), "bible.storylines.resolution.tooltip")
        Theme.addRow(form, "bible.storylines.notes", Theme.scroll(notesArea), "bible.storylines.notes.tooltip")

        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, Theme.scroll(form))
        split.dividerLocation = 260

        add(Theme.header("bible.storylines.header"), BorderLayout.NORTH)
        add(split, BorderLayout.CENTER)

        storylineList.addListSelectionListener { event ->
            if (!event.valueIsAdjusting && !updatingSelection) {
                applyFormToCurrent()
                selectedIndex = storylineList.selectedIndex
                loadFormFromSelection()
            }
        }
    }

    private fun applyFormToCurrent() {
        val index = selectedIndex
        if (index !in storylines.indices) return
        storylines = storylines.toMutableList().also { list ->
            list[index] = list[index].copy(
                id = idCombo.editor.item.toString().trim(),
                name = nameField.text,
                setup = setupArea.text,
                secrets = lines(secretsArea),
                foreshadowing = lines(foreshadowingArea),
                resolution = resolutionArea.text,
                notes = notesArea.text,
            )
        }
        refreshListModel()
    }

    private fun loadFormFromSelection() {
        val storyline = storylines.getOrNull(selectedIndex)
        updatingSelection = true
        try {
            idCombo.editor.item = storyline?.id ?: ""
            nameField.text = storyline?.name ?: ""
            setupArea.text = storyline?.setup ?: ""
            secretsArea.text = storyline?.secrets?.joinToString("\n") ?: ""
            foreshadowingArea.text = storyline?.foreshadowing?.joinToString("\n") ?: ""
            resolutionArea.text = storyline?.resolution ?: ""
            notesArea.text = storyline?.notes ?: ""
        } finally {
            updatingSelection = false
        }
    }

    private fun addStoryline() {
        applyFormToCurrent()
        val nextId = listOf("A", "B", "C").firstOrNull { candidate -> storylines.none { it.id == candidate } } ?: ""
        storylines = storylines + Storyline(id = nextId)
        selectedIndex = storylines.lastIndex
        refreshListModel()
        storylineList.selectedIndex = selectedIndex
        loadFormFromSelection()
        context.documentEdited()
    }

    private fun deleteStoryline() {
        val index = selectedIndex
        if (index !in storylines.indices) return
        val answer = JOptionPane.showConfirmDialog(
            this,
            String.format(I18n.t("bible.storylines.delete.confirm"), storylines[index].id),
            I18n.t("bible.storylines.delete"),
            JOptionPane.YES_NO_OPTION,
        )
        if (answer != JOptionPane.YES_OPTION) return
        storylines = storylines.toMutableList().also { it.removeAt(index) }
        selectedIndex = -1
        refreshListModel()
        if (storylines.isNotEmpty()) {
            storylineList.selectedIndex = index.coerceAtMost(storylines.lastIndex)
            selectedIndex = storylineList.selectedIndex
        }
        loadFormFromSelection()
        context.documentEdited()
    }

    private fun refreshListModel() {
        // Guard against re-entering the selection listener while the list is
        // being rebuilt (clear() fires selection events).
        val previous = updatingSelection
        updatingSelection = true
        try {
            listModel.clear()
            storylines.forEach { storyline ->
                listModel.addElement(storyline.id + " — " + storyline.name)
            }
            storylineList.selectedIndex = selectedIndex
        } finally {
            updatingSelection = previous
        }
    }

    override fun commitToProject() {
        applyFormToCurrent()
        val updated = storylines
        context.session.updateIfChanged { document ->
            if (document.bible.storylines == updated) document else document.copy(bible = document.bible.copy(storylines = updated))
        }
    }

    override fun refresh() {
        storylines = context.session.document?.bible?.storylines ?: emptyList()
        selectedIndex = if (storylines.isEmpty()) -1 else 0
        refreshListModel()
        loadFormFromSelection()
    }

    private fun lines(area: JTextArea): List<String> =
        area.text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }
}