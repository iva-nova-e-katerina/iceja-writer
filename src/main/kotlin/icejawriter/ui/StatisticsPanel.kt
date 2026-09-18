package icejawriter.ui

import icejawriter.I18n
import icejawriter.model.SceneStatus
import icejawriter.model.SceneStructure
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.table.AbstractTableModel

/**
 * Summary statistics of the open project (ФР-71): total words/scenes/chapters,
 * volume per act and the approval progress of scenes.
 */
class StatisticsPanel(private val context: EditorContext) : JPanel(BorderLayout()), ProjectEditor {

    private val totalsLabel = JLabel()
    private val statusLabel = JLabel()
    private val model = ActStatsTableModel()
    private val table = JTable(model)

    init {
        val top = JPanel(GridLayout(2, 1, 4, 4))
        top.add(totalsLabel)
        top.add(statusLabel)
        top.border = javax.swing.BorderFactory.createEmptyBorder(6, 6, 6, 6)

        add(Theme.header("stats.header"), BorderLayout.NORTH)
        val center = JPanel(BorderLayout())
        center.add(top, BorderLayout.NORTH)
        center.add(JScrollPane(table), BorderLayout.CENTER)
        add(center, BorderLayout.CENTER)
    }

    override fun commitToProject() {
        // Statistics never edit the project.
    }

    override fun refresh() {
        val document = context.session.document
        if (document == null) {
            totalsLabel.text = ""
            statusLabel.text = ""
            model.setRows(emptyList())
            return
        }
        val scenes = document.scenes
        val totalWords = SceneStructure.totalWordCount(scenes)
        val chapterCount = scenes.map { it.card.act to it.card.chapter }.distinct().size
        totalsLabel.text = String.format(
            I18n.t("stats.totals"),
            totalWords,
            scenes.size,
            chapterCount,
            SceneStructure.acts(scenes).size,
        )

        val drafts = scenes.count { it.card.status == SceneStatus.DRAFT }
        val revised = scenes.count { it.card.status == SceneStatus.REVISED }
        val approved = scenes.count { it.card.status == SceneStatus.APPROVED }
        statusLabel.text = String.format(
            I18n.t("stats.statuses"),
            drafts,
            revised,
            approved,
        )

        val rows = SceneStructure.acts(scenes).map { act ->
            val actScenes = scenes.filter { it.card.act == act }
            ActStats(
                act = act,
                scenes = actScenes.size,
                words = SceneStructure.totalWordCount(actScenes),
                approved = actScenes.count { it.card.status == SceneStatus.APPROVED },
            )
        }
        model.setRows(rows)
    }

    private data class ActStats(val act: Int, val scenes: Int, val words: Int, val approved: Int)

    private class ActStatsTableModel : AbstractTableModel() {
        private var rows: List<ActStats> = emptyList()

        fun setRows(newRows: List<ActStats>) {
            rows = newRows
            fireTableDataChanged()
        }

        override fun getRowCount(): Int = rows.size
        override fun getColumnCount(): Int = 4
        override fun getColumnName(column: Int): String = when (column) {
            0 -> I18n.t("stats.column.act")
            1 -> I18n.t("stats.column.scenes")
            2 -> I18n.t("stats.column.words")
            else -> I18n.t("stats.column.approved")
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val row = rows[rowIndex]
            return when (columnIndex) {
                0 -> row.act
                1 -> row.scenes
                2 -> row.words
                else -> row.approved
            }
        }
    }
}