package icejawriter.ui

import icejawriter.I18n
import icejawriter.model.GlossaryTerm
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableRowSorter

/**
 * Glossary table editor (ФР-34): "Term / Definition", sorted alphabetically,
 * with add/edit/delete (ФРМ-11).
 */
class GlossaryPanel(private val context: EditorContext) : JPanel(BorderLayout()), ProjectEditor {

    private val model = GlossaryTableModel()
    private val sorter = TableRowSorter(model).apply {
        setComparator(0) { left, right ->
            left.toString().lowercase().compareTo(right.toString().lowercase())
        }
    }
    private val table = JTable(model).apply {
        rowSorter = sorter
    }

    init {
        val buttons = JPanel(FlowLayout(FlowLayout.LEFT, 6, 6))
        val addButton = JButton(I18n.t("bible.glossary.add"))
        val deleteButton = JButton(I18n.t("bible.glossary.delete"))
        addButton.addActionListener {
            model.addRow()
            context.documentEdited()
        }
        deleteButton.addActionListener {
            model.removeRow(table.convertRowIndexToModel(table.selectedRow))
            context.documentEdited()
        }
        buttons.add(addButton)
        buttons.add(deleteButton)

        add(Theme.header("bible.glossary.header"), BorderLayout.NORTH)
        add(JScrollPane(table), BorderLayout.CENTER)
        add(buttons, BorderLayout.SOUTH)
    }

    override fun commitToProject() {
        val terms = model.rows()
        context.session.updateIfChanged { document ->
            if (document.bible.glossary == terms) document else document.copy(bible = document.bible.copy(glossary = terms))
        }
    }

    override fun refresh() {
        model.setRows(context.session.document?.bible?.glossary ?: emptyList())
    }

    private class GlossaryTableModel : AbstractTableModel() {
        private var rows: List<GlossaryTerm> = emptyList()

        fun setRows(newRows: List<GlossaryTerm>) {
            rows = newRows
            fireTableDataChanged()
        }

        fun rows(): List<GlossaryTerm> = rows

        fun addRow() {
            rows = rows + GlossaryTerm()
            fireTableRowsInserted(rows.size - 1, rows.size - 1)
        }

        fun removeRow(index: Int) {
            if (index !in rows.indices) return
            rows = rows.toMutableList().also { it.removeAt(index) }
            fireTableDataChanged()
        }

        override fun getRowCount(): Int = rows.size
        override fun getColumnCount(): Int = 2
        override fun getColumnName(column: Int): String =
            if (column == 0) I18n.t("bible.glossary.term") else I18n.t("bible.glossary.definition")

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = true

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val term = rows[rowIndex]
            return if (columnIndex == 0) term.term else term.definition
        }

        override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
            val term = rows[rowIndex]
            val updated = if (columnIndex == 0) {
                term.copy(term = value?.toString() ?: "")
            } else {
                term.copy(definition = value?.toString() ?: "")
            }
            rows = rows.toMutableList().also { it[rowIndex] = updated }
            fireTableRowsUpdated(rowIndex, rowIndex)
        }
    }
}