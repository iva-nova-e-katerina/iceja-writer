package icejawriter.ui

import icejawriter.I18n
import icejawriter.model.Character
import icejawriter.model.ProjectDocument
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.table.AbstractTableModel

/**
 * Full-text search over the scene texts and the whole bible (ФР-70, ФР-35).
 * Double-clicking a result opens the corresponding scene or bible section.
 */
class SearchPanel(private val context: EditorContext) : JPanel(BorderLayout()), ProjectEditor {

    private val queryField = JTextField(24)
    private val caseSensitive = JCheckBox(I18n.t("search.caseSensitive"))
    private val resultsModel = ResultsTableModel()
    private val table = JTable(resultsModel)

    init {
        val top = JPanel(FlowLayout(FlowLayout.LEFT, 6, 6))
        top.add(queryField)
        top.add(caseSensitive)
        val searchButton = JButton(I18n.t("search.run"))
        searchButton.addActionListener { runSearch() }
        top.add(searchButton)
        queryField.addActionListener { runSearch() }

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2) openSelectedResult()
            }
        })

        add(Theme.header("search.header"), BorderLayout.NORTH)
        val center = JPanel(BorderLayout())
        center.add(top, BorderLayout.NORTH)
        center.add(JScrollPane(table), BorderLayout.CENTER)
        add(center, BorderLayout.CENTER)
    }

    /** Runs the search and fills the results table. */
    fun runSearch() {
        val query = queryField.text
        if (query.isBlank()) {
            resultsModel.setRows(emptyList())
            return
        }
        val document = context.session.document ?: return
        val ignoreCase = !caseSensitive.isSelected
        val hits = mutableListOf<SearchHit>()

        // Scene cards and texts.
        for (scene in document.scenes) {
            findIn(scene.text, query, ignoreCase) { snippet ->
                hits += SearchHit(
                    location = String.format(I18n.t("search.sceneLocation"), scene.card.number, scene.card.title),
                    snippet = snippet,
                    sceneId = scene.card.id,
                    bibleKey = null,
                )
            }
            findIn(scene.card.title, query, ignoreCase) { snippet ->
                hits += SearchHit(
                    location = String.format(I18n.t("search.sceneCardLocation"), scene.card.number),
                    snippet = snippet,
                    sceneId = scene.card.id,
                    bibleKey = null,
                )
            }
        }

        // Bible: search every textual field of every section.
        for ((key, labelKey) in ProjectTree.BIBLE_SECTIONS) {
            for ((field, value) in bibleFields(document, key)) {
                findIn(value, query, ignoreCase) { snippet ->
                    hits += SearchHit(
                        location = I18n.t(labelKey) + " → " + field,
                        snippet = snippet,
                        sceneId = null,
                        bibleKey = key,
                    )
                }
            }
        }

        resultsModel.setRows(hits.take(MAX_RESULTS))
        context.showStatus(String.format(I18n.t("search.results"), hits.size))
    }

    /** Returns the searchable string fields of a bible section. */
    private fun bibleFields(document: ProjectDocument, key: String): List<Pair<String, String>> {
        val bible = document.bible
        return when (key) {
            "world" -> listOf(
                "geography" to bible.world.geography,
                "politics" to bible.world.politics,
                "economy" to bible.world.economy,
                "techMagic" to bible.world.techMagic,
                "religion" to bible.world.religion,
                "languages" to bible.world.languages,
                "notes" to bible.world.notes,
            )
            "characters" -> bible.characters.flatMap { character: Character ->
                listOf(
                    character.id to character.name,
                    character.id to character.role,
                    character.id to character.voice,
                    character.id to character.weakness,
                    character.id to character.notes,
                    character.id to character.arcs.joinToString("\n"),
                    character.id to character.knows.joinToString("\n"),
                    character.id to character.doesNotKnow.joinToString("\n"),
                )
            }
            "chronology" -> bible.chronology.map { it.date to (it.event + "\n" + it.notes) }
            "rules" -> listOf(
                "possible" to bible.rules.possible,
                "impossible" to bible.rules.impossible,
                "cost" to bible.rules.cost,
                "limits" to bible.rules.limits,
                "notes" to bible.rules.notes,
            )
            "storylines" -> bible.storylines.map { it.id to (it.name + "\n" + it.setup + "\n" + it.resolution + "\n" + it.notes) }
            "glossary" -> bible.glossary.map { it.term to it.definition }
            "style" -> listOf(
                "pov" to bible.style.pov,
                "tense" to bible.style.tense,
                "tone" to bible.style.tone,
                "forbiddenWords" to bible.style.forbiddenWords.joinToString("\n"),
                "typicalPhrases" to bible.style.typicalPhrases.joinToString("\n"),
                "notes" to bible.style.notes,
            )
            else -> emptyList()
        }
    }

    /** Invokes [onFound] with a context snippet for every match in [text]. */
    private fun findIn(text: String, query: String, ignoreCase: Boolean, onFound: (String) -> Unit) {
        var index = text.indexOf(query, startIndex = 0, ignoreCase = ignoreCase)
        while (index >= 0) {
            val start = (index - SNIPPET_PADDING).coerceAtLeast(0)
            val end = (index + query.length + SNIPPET_PADDING).coerceAtMost(text.length)
            val snippet = text.substring(start, end).replace('\n', ' ').trim()
            onFound(snippet)
            index = text.indexOf(query, startIndex = index + query.length, ignoreCase = ignoreCase)
        }
    }

    private fun openSelectedResult() {
        val row = table.selectedRow
        if (row < 0) return
        val hit = resultsModel.rows().getOrNull(table.convertRowIndexToModel(row)) ?: return
        when {
            hit.sceneId != null -> context.openScene(hit.sceneId)
            hit.bibleKey != null -> context.openBibleSection(hit.bibleKey)
        }
    }

    override fun commitToProject() {
        // Search never edits the project.
    }

    override fun refresh() {
        resultsModel.setRows(emptyList())
    }

    /** One search hit: a location label, a snippet and a navigation target. */
    private data class SearchHit(
        val location: String,
        val snippet: String,
        val sceneId: String?,
        val bibleKey: String?,
    )

    private class ResultsTableModel : AbstractTableModel() {
        private var rows: List<SearchHit> = emptyList()

        fun setRows(newRows: List<SearchHit>) {
            rows = newRows
            fireTableDataChanged()
        }

        fun rows(): List<SearchHit> = rows

        override fun getRowCount(): Int = rows.size
        override fun getColumnCount(): Int = 2
        override fun getColumnName(column: Int): String =
            if (column == 0) I18n.t("search.column.location") else I18n.t("search.column.snippet")

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val hit = rows[rowIndex]
            return if (columnIndex == 0) hit.location else hit.snippet
        }
    }

    companion object {
        private const val MAX_RESULTS = 500
        private const val SNIPPET_PADDING = 40
    }
}