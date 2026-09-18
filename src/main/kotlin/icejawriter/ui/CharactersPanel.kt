package icejawriter.ui

import icejawriter.I18n
import icejawriter.model.Character
import icejawriter.model.RelationshipRef
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.DefaultCellEditor
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTable
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel

/**
 * Character cards editor (ФР-31): list of characters on the left, the full
 * card form on the right (all fields of ФРМ-07). Relationship targets are
 * chosen from the existing characters so that references stay valid (ФРМ-20).
 */
class CharactersPanel(private val context: EditorContext) : JPanel(BorderLayout()), ProjectEditor {

    private var characters: List<Character> = emptyList()
    private var selectedIndex: Int = -1
    private var updatingSelection: Boolean = false

    private val listModel = DefaultListModel<String>()
    private val characterList = JList(listModel).apply {
        selectionMode = ListSelectionModel.SINGLE_SELECTION
        visibleRowCount = 18
    }

    private val idField = JTextField()
    private val nameField = JTextField()
    private val aliasesArea = Theme.textArea(3, "bible.characters.aliases.tooltip")
    private val roleField = JTextField()
    private val ageArea = Theme.textArea(3, "bible.characters.ageByYear.tooltip")
    private val arcsArea = Theme.textArea(3, "bible.characters.arcs.tooltip")
    private val voiceArea = Theme.textArea(3, "bible.characters.voice.tooltip")
    private val knowsArea = Theme.textArea(3, "bible.characters.knows.tooltip")
    private val doesNotKnowArea = Theme.textArea(3, "bible.characters.doesNotKnow.tooltip")
    private val traumasArea = Theme.textArea(3, "bible.characters.traumas.tooltip")
    private val goalsArea = Theme.textArea(3, "bible.characters.goals.tooltip")
    private val weaknessField = JTextField()
    private val notesArea = Theme.textArea(4, "bible.characters.notes.tooltip")

    private val relationshipsModel = RelationshipTableModel()
    private val relationshipsTable = JTable(relationshipsModel)

    init {
        // Left: character list + add/delete buttons.
        val left = JPanel(BorderLayout())
        left.add(JScrollPane(characterList), BorderLayout.CENTER)
        val listButtons = JPanel(FlowLayout(FlowLayout.LEFT, 4, 4))
        val addButton = JButton(I18n.t("bible.characters.add"))
        val deleteButton = JButton(I18n.t("bible.characters.delete"))
        addButton.addActionListener { addCharacter() }
        deleteButton.addActionListener { deleteCharacter() }
        listButtons.add(addButton)
        listButtons.add(deleteButton)
        left.add(listButtons, BorderLayout.SOUTH)

        // Right: the card form.
        val form = Theme.formPanel()
        Theme.addRow(form, "bible.characters.id", idField, "bible.characters.id.tooltip")
        Theme.addRow(form, "bible.characters.name", nameField, "bible.characters.name.tooltip")
        Theme.addRow(form, "bible.characters.aliases", Theme.scroll(aliasesArea), "bible.characters.aliases.tooltip")
        Theme.addRow(form, "bible.characters.role", roleField, "bible.characters.role.tooltip")
        Theme.addRow(form, "bible.characters.ageByYear", Theme.scroll(ageArea), "bible.characters.ageByYear.tooltip")
        Theme.addRow(form, "bible.characters.arcs", Theme.scroll(arcsArea), "bible.characters.arcs.tooltip")

        relationshipsTable.setDefaultEditor(
            String::class.java,
            DefaultCellEditor(JComboBox<String>()).apply { clickCountToStart = 1 },
        )
        relationshipsTable.preferredScrollableViewportSize = Dimension(360, 90)
        val relationshipsPanel = JPanel(BorderLayout())
        relationshipsPanel.add(JScrollPane(relationshipsTable), BorderLayout.CENTER)
        val relationshipButtons = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        val addRelationship = JButton(I18n.t("bible.characters.relationship.add"))
        val removeRelationship = JButton(I18n.t("bible.characters.relationship.remove"))
        addRelationship.addActionListener { relationshipsModel.addRow() }
        removeRelationship.addActionListener { relationshipsModel.removeRow(relationshipsTable.selectedRow) }
        relationshipButtons.add(addRelationship)
        relationshipButtons.add(removeRelationship)
        relationshipsPanel.add(relationshipButtons, BorderLayout.SOUTH)
        Theme.addRow(form, "bible.characters.relationships", relationshipsPanel, "bible.characters.relationships.tooltip")

        Theme.addRow(form, "bible.characters.voice", Theme.scroll(voiceArea), "bible.characters.voice.tooltip")
        Theme.addRow(form, "bible.characters.knows", Theme.scroll(knowsArea), "bible.characters.knows.tooltip")
        Theme.addRow(form, "bible.characters.doesNotKnow", Theme.scroll(doesNotKnowArea), "bible.characters.doesNotKnow.tooltip")
        Theme.addRow(form, "bible.characters.traumas", Theme.scroll(traumasArea), "bible.characters.traumas.tooltip")
        Theme.addRow(form, "bible.characters.goals", Theme.scroll(goalsArea), "bible.characters.goals.tooltip")
        Theme.addRow(form, "bible.characters.weakness", weaknessField, "bible.characters.weakness.tooltip")
        Theme.addRow(form, "bible.characters.notes", Theme.scroll(notesArea), "bible.characters.notes.tooltip")

        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, Theme.scroll(form))
        split.resizeWeight = 0.28
        split.dividerLocation = 260

        add(Theme.header("bible.characters.header"), BorderLayout.NORTH)
        add(split, BorderLayout.CENTER)

        characterList.addListSelectionListener { event ->
            if (!event.valueIsAdjusting && !updatingSelection) {
                applyFormToCurrent()
                selectedIndex = characterList.selectedIndex
                loadFormFromSelection()
            }
        }
    }

    /** Applies the form values to the currently selected character. */
    private fun applyFormToCurrent() {
        val index = selectedIndex
        if (index !in characters.indices) return
        characters = characters.toMutableList().also { list ->
            list[index] = list[index].copy(
                id = idField.text.trim(),
                name = nameField.text,
                aliases = lines(aliasesArea),
                role = roleField.text,
                ageByYear = parseAgeMap(ageArea.text),
                arcs = lines(arcsArea),
                relationships = relationshipsModel.rows(),
                voice = voiceArea.text,
                knows = lines(knowsArea),
                doesNotKnow = lines(doesNotKnowArea),
                traumas = lines(traumasArea),
                goals = lines(goalsArea),
                weakness = weaknessField.text,
                notes = notesArea.text,
            )
        }
        refreshListModel()
    }

    /** Fills the form from the selected character. */
    private fun loadFormFromSelection() {
        val character = characters.getOrNull(selectedIndex)
        updatingSelection = true
        try {
            if (character == null) {
                idField.text = ""
                nameField.text = ""
                aliasesArea.text = ""
                roleField.text = ""
                ageArea.text = ""
                arcsArea.text = ""
                voiceArea.text = ""
                knowsArea.text = ""
                doesNotKnowArea.text = ""
                traumasArea.text = ""
                goalsArea.text = ""
                weaknessField.text = ""
                notesArea.text = ""
                relationshipsModel.setRows(emptyList())
            } else {
                idField.text = character.id
                nameField.text = character.name
                aliasesArea.text = character.aliases.joinToString("\n")
                roleField.text = character.role
                ageArea.text = character.ageByYear.entries.joinToString("\n") { (year, age) -> "$year: $age" }
                arcsArea.text = character.arcs.joinToString("\n")
                voiceArea.text = character.voice
                knowsArea.text = character.knows.joinToString("\n")
                doesNotKnowArea.text = character.doesNotKnow.joinToString("\n")
                traumasArea.text = character.traumas.joinToString("\n")
                goalsArea.text = character.goals.joinToString("\n")
                weaknessField.text = character.weakness
                notesArea.text = character.notes
                relationshipsModel.setRows(character.relationships)
            }
            relationshipsTable.columnModel.getColumn(0).cellEditor = DefaultCellEditor(
                JComboBox(characterIds().toTypedArray()),
            ).apply { clickCountToStart = 1 }
        } finally {
            updatingSelection = false
        }
    }

    private fun addCharacter() {
        applyFormToCurrent()
        val id = nextCharacterId()
        characters = characters + Character(id = id, name = "")
        refreshListModel()
        characterList.selectedIndex = characters.lastIndex
        selectedIndex = characters.lastIndex
        loadFormFromSelection()
        context.documentEdited()
    }

    private fun deleteCharacter() {
        val index = selectedIndex
        if (index !in characters.indices) return
        val answer = JOptionPane.showConfirmDialog(
            this,
            String.format(I18n.t("bible.characters.delete.confirm"), characters[index].name.ifBlank { characters[index].id }),
            I18n.t("bible.characters.delete"),
            JOptionPane.YES_NO_OPTION,
        )
        if (answer != JOptionPane.YES_OPTION) return
        characters = characters.toMutableList().also { it.removeAt(index) }
        selectedIndex = -1
        refreshListModel()
        if (characters.isNotEmpty()) {
            characterList.selectedIndex = index.coerceAtMost(characters.lastIndex)
            selectedIndex = characterList.selectedIndex
        }
        loadFormFromSelection()
        context.documentEdited()
    }

    private fun nextCharacterId(): String {
        val maxSuffix = characters.mapNotNull { character ->
            character.id.removePrefix("char-").toIntOrNull()
        }.maxOrNull() ?: 0
        return "char-" + (maxSuffix + 1).toString().padStart(3, '0')
    }

    /** Ids available for relationship references (ФР-31, ФРМ-20). */
    private fun characterIds(): List<String> = characters.map { it.id }.filter { it.isNotBlank() }

    private fun refreshListModel() {
        // Guard against re-entering the selection listener while the list is
        // being rebuilt (clear() fires selection events).
        val previous = updatingSelection
        updatingSelection = true
        try {
            listModel.clear()
            characters.forEach { character ->
                listModel.addElement(character.name.ifBlank { character.id } + "  (" + character.id + ")")
            }
            characterList.selectedIndex = selectedIndex
        } finally {
            updatingSelection = previous
        }
    }

    override fun commitToProject() {
        applyFormToCurrent()
        val updated = characters
        context.session.updateIfChanged { document ->
            if (document.bible.characters == updated) {
                document
            } else {
                document.copy(bible = document.bible.copy(characters = updated))
            }
        }
    }

    override fun refresh() {
        characters = context.session.document?.bible?.characters ?: emptyList()
        selectedIndex = if (characters.isEmpty()) -1 else 0
        refreshListModel()
        loadFormFromSelection()
    }

    private fun lines(area: JTextArea): List<String> =
        area.text.split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    /** Parses "1918: 25" lines into a year -> age map (ФРМ-07). */
    private fun parseAgeMap(text: String): Map<String, Int> {
        val result = LinkedHashMap<String, Int>()
        for (line in text.split('\n')) {
            val parts = line.split(':', limit = 2)
            if (parts.size != 2) continue
            val year = parts[0].trim()
            val age = parts[1].trim().toIntOrNull() ?: continue
            if (year.isNotEmpty()) result[year] = age
        }
        return result
    }

    /**
     * Table model of the relationships list with editable withId/type/notes.
     */
    private class RelationshipTableModel : AbstractTableModel() {
        private var rows: List<RelationshipRef> = emptyList()

        fun setRows(newRows: List<RelationshipRef>) {
            rows = newRows
            fireTableDataChanged()
        }

        fun rows(): List<RelationshipRef> = rows

        fun addRow() {
            rows = rows + RelationshipRef()
            fireTableDataChanged()
        }

        fun removeRow(index: Int) {
            if (index !in rows.indices) return
            rows = rows.toMutableList().also { it.removeAt(index) }
            fireTableDataChanged()
        }

        override fun getRowCount(): Int = rows.size
        override fun getColumnCount(): Int = 3
        override fun getColumnName(column: Int): String = when (column) {
            0 -> I18n.t("bible.characters.relationship.withId")
            1 -> I18n.t("bible.characters.relationship.type")
            else -> I18n.t("bible.characters.relationship.notes")
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = true

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val ref = rows[rowIndex]
            return when (columnIndex) {
                0 -> ref.withId
                1 -> ref.type
                else -> ref.notes
            }
        }

        override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
            val ref = rows[rowIndex]
            val updated = when (columnIndex) {
                0 -> ref.copy(withId = value?.toString() ?: "")
                1 -> ref.copy(type = value?.toString() ?: "")
                else -> ref.copy(notes = value?.toString() ?: "")
            }
            rows = rows.toMutableList().also { it[rowIndex] = updated }
            fireTableRowsUpdated(rowIndex, rowIndex)
        }
    }
}