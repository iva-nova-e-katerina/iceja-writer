package icejawriter.ui

import icejawriter.I18n
import icejawriter.model.ChronologyEvent
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.table.AbstractTableModel
import javax.swing.table.TableRowSorter

/**
 * Chronology table editor (ФР-32): "Date / Event / Notes", sorted by date,
 * with add/edit/delete (ФРМ-08).
 */
class ChronologyPanel(private val context: EditorContext) : JPanel(BorderLayout()), ProjectEditor {

    private val model = ChronologyTableModel()
    private val sorter = TableRowSorter(model).apply {
        setComparator(0) { left, right -> left.toString().compareTo(right.toString()) }
    }
    private val table = JTable(model).apply {
        rowSorter = sorter
    }

    init {
        val buttons = JPanel(FlowLayout(FlowLayout.LEFT, 6, 6))
        val addButton = JButton(I18n.t("bible.chronology.add"))
        val deleteButton = JButton(I18n.t("bible.chronology.delete"))
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

        add(Theme.header("bible.chronology.header"), BorderLayout.NORTH)
        add(JScrollPane(table), BorderLayout.CENTER)
        add(buttons, BorderLayout.SOUTH)
    }

    override fun commitToProject() {
        val events = model.rows()
        context.session.updateIfChanged { document ->
            if (document.bible.chronology == events) document else document.copy(bible = document.bible.copy(chronology = events))
        }
    }

    override fun refresh() {
        model.setRows(context.session.document?.bible?.chronology ?: emptyList())
    }

    private class ChronologyTableModel : AbstractTableModel() {
        private var rows: List<ChronologyEvent> = emptyList()

        fun setRows(newRows: List<ChronologyEvent>) {
            rows = newRows
            fireTableDataChanged()
        }

        fun rows(): List<ChronologyEvent> = rows

        fun addRow() {
            rows = rows + ChronologyEvent()
            fireTableRowsInserted(rows.size - 1, rows.size - 1)
        }

        fun removeRow(index: Int) {
            if (index !in rows.indices) return
            rows = rows.toMutableList().also { it.removeAt(index) }
            fireTableDataChanged()
        }

        override fun getRowCount(): Int = rows.size
        override fun getColumnCount(): Int = 3
        override fun getColumnName(column: Int): String = when (column) {
            0 -> I18n.t("bible.chronology.date")
            1 -> I18n.t("bible.chronology.event")
            else -> I18n.t("bible.chronology.notes")
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean = true

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val event = rows[rowIndex]
            return when (columnIndex) {
                0 -> event.date
                1 -> event.event
                else -> event.notes
            }
        }

        override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
            val event = rows[rowIndex]
            val updated = when (columnIndex) {
                0 -> event.copy(date = value?.toString() ?: "")
                1 -> event.copy(event = value?.toString() ?: "")
                else -> event.copy(notes = value?.toString() ?: "")
            }
            rows = rows.toMutableList().also { it[rowIndex] = updated }
            fireTableRowsUpdated(rowIndex, rowIndex)
        }
    }
}