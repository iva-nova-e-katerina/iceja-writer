package icejawriter.ui

import icejawriter.I18n
import icejawriter.model.Rules
import icejawriter.model.Style
import icejawriter.model.World
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * Bible section shown by [BibleTextPanel].
 */
enum class BibleSection(val wireName: String, val headerKey: String) {
    WORLD("world", "bible.world.header"),
    RULES("rules", "bible.rules.header"),
    STYLE("style", "bible.style.header"),
}

/**
 * Editor for the multi-line sections of the novel bible (ФР-30): world, rules
 * and style. Style list fields (forbidden words, typical phrases) are edited
 * one item per line.
 */
class BibleTextPanel(
    private val context: EditorContext,
    private val section: BibleSection,
) : JPanel(BorderLayout()), ProjectEditor {

    private val fields = LinkedHashMap<String, JTextArea>()

    init {
        val form = Theme.formPanel()
        for (field in FIELDS.getValue(section)) {
            val labelKey = "bible.${section.wireName}.${field}"
            val area = Theme.textArea(if (field == "notes") 6 else 4, "$labelKey.tooltip")
            fields[field] = area
            Theme.addRow(form, labelKey, Theme.scroll(area), "$labelKey.tooltip")
        }
        add(Theme.header(section.headerKey), BorderLayout.NORTH)
        add(Theme.scroll(form), BorderLayout.CENTER)
    }

    override fun commitToProject() {
        when (section) {
            BibleSection.WORLD -> {
                val world = World(
                    geography = text("geography"),
                    politics = text("politics"),
                    economy = text("economy"),
                    techMagic = text("techMagic"),
                    religion = text("religion"),
                    languages = text("languages"),
                    notes = text("notes"),
                )
                context.session.updateIfChanged { document ->
                    if (document.bible.world == world) document else document.copy(bible = document.bible.copy(world = world))
                }
            }
            BibleSection.RULES -> {
                val rules = Rules(
                    possible = text("possible"),
                    impossible = text("impossible"),
                    cost = text("cost"),
                    limits = text("limits"),
                    notes = text("notes"),
                )
                context.session.updateIfChanged { document ->
                    if (document.bible.rules == rules) document else document.copy(bible = document.bible.copy(rules = rules))
                }
            }
            BibleSection.STYLE -> {
                val style = Style(
                    pov = text("pov"),
                    tense = text("tense"),
                    tone = text("tone"),
                    forbiddenWords = lines("forbiddenWords"),
                    typicalPhrases = lines("typicalPhrases"),
                    notes = text("notes"),
                )
                context.session.updateIfChanged { document ->
                    if (document.bible.style == style) document else document.copy(bible = document.bible.copy(style = style))
                }
            }
        }
    }

    override fun refresh() {
        val bible = context.session.document?.bible
        when (section) {
            BibleSection.WORLD -> {
                val world = bible?.world ?: World()
                setText("geography", world.geography)
                setText("politics", world.politics)
                setText("economy", world.economy)
                setText("techMagic", world.techMagic)
                setText("religion", world.religion)
                setText("languages", world.languages)
                setText("notes", world.notes)
            }
            BibleSection.RULES -> {
                val rules = bible?.rules ?: Rules()
                setText("possible", rules.possible)
                setText("impossible", rules.impossible)
                setText("cost", rules.cost)
                setText("limits", rules.limits)
                setText("notes", rules.notes)
            }
            BibleSection.STYLE -> {
                val style = bible?.style ?: Style()
                setText("pov", style.pov)
                setText("tense", style.tense)
                setText("tone", style.tone)
                setText("forbiddenWords", style.forbiddenWords.joinToString("\n"))
                setText("typicalPhrases", style.typicalPhrases.joinToString("\n"))
                setText("notes", style.notes)
            }
        }
    }

    private fun text(key: String): String = fields[key]?.text ?: ""

    private fun setText(key: String, value: String) {
        fields[key]?.text = value
    }

    /** Splits a text area into non-blank trimmed lines (list fields). */
    private fun lines(key: String): List<String> =
        text(key).split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        /** JSON field names per section, in display order. */
        private val FIELDS: Map<BibleSection, List<String>> = mapOf(
            BibleSection.WORLD to listOf("geography", "politics", "economy", "techMagic", "religion", "languages", "notes"),
            BibleSection.RULES to listOf("possible", "impossible", "cost", "limits", "notes"),
            BibleSection.STYLE to listOf("pov", "tense", "tone", "forbiddenWords", "typicalPhrases", "notes"),
        )
    }
}