package icejawriter.ui

import icejawriter.I18n
import icejawriter.model.Character
import java.awt.Color
import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JComboBox
import javax.swing.JList
import javax.swing.text.BadLocationException
import javax.swing.text.DefaultHighlighter
import javax.swing.text.Highlighter
import javax.swing.JTextArea
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.Timer

/**
 * Combo box of character ids that displays "id — name" while storing the id
 * (ФР-31, ФР-40). An empty first entry means "not assigned".
 */
class CharacterComboBox : JComboBox<String>() {

    init {
        renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): Component {
                val id = value?.toString().orEmpty()
                val display = if (id.isEmpty()) {
                    I18n.t("scene.pov.none")
                } else {
                    val name = characterNames[id]
                    if (name.isNullOrBlank()) id else "$id — $name"
                }
                return super.getListCellRendererComponent(list, display, index, isSelected, cellHasFocus)
            }
        }
    }

    private var characterNames: Map<String, String> = emptyMap()

    /** Replaces the available choices with the current bible characters. */
    fun setCharacters(characters: List<Character>, selectedId: String) {
        characterNames = characters.associate { it.id to it.name }
        removeAllItems()
        addItem("")
        characters.forEach { addItem(it.id) }
        selectedItem = selectedId
    }
}

/**
 * Markdown text area of a scene that highlights the service marks `[NEW]`
 * (ФР-51). Highlighting is debounced so that typing stays responsive on large
 * scenes (НФР-01).
 */
class SceneTextArea : JTextArea() {

    private val markPainter: Highlighter.HighlightPainter =
        DefaultHighlighter.DefaultHighlightPainter(Color(0xFFF0A0))

    private val debounce = Timer(250) { refreshHighlights() }.apply { isRepeats = false }

    init {
        lineWrap = true
        wrapStyleWord = true
        document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = debounce.restart()
            override fun removeUpdate(e: DocumentEvent) = debounce.restart()
            override fun changedUpdate(e: DocumentEvent) = debounce.restart()
        })
    }

    /** Recomputes all [NEW] highlights. */
    fun refreshHighlights() {
        highlighter.removeAllHighlights()
        val content = text
        var index = content.indexOf(NEW_MARK)
        while (index >= 0) {
            try {
                highlighter.addHighlight(index, index + NEW_MARK.length, markPainter)
            } catch (_: BadLocationException) {
                break
            }
            index = content.indexOf(NEW_MARK, index + NEW_MARK.length)
        }
    }

    companion object {
        const val NEW_MARK = "[NEW]"
    }
}