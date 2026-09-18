package icejawriter

import java.awt.BorderLayout
import java.awt.Font
import java.awt.Color
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingConstants
import javax.swing.border.EmptyBorder
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Light themed main content panel of the IceJA Writer: the manuscript editor
 * in the center and a status bar with word/character counters at the bottom.
 * The menu bar of [MainFrame] owns language switching and zoom.
 */
class WriterPanel : JPanel(BorderLayout()) {

    private val editorArea = JTextArea()
    private val statusLabel = JLabel("", SwingConstants.LEFT)

    init {
        border = EmptyBorder(12, 12, 12, 12)

        // Light theme manuscript editor.
        editorArea.background = Color.WHITE
        editorArea.foreground = Color.BLACK
        editorArea.caretColor = Color.BLACK
        editorArea.font = Font(Font.MONOSPACED, Font.PLAIN, 14)
        editorArea.lineWrap = true
        editorArea.wrapStyleWord = true
        editorArea.border = BorderFactory.createEmptyBorder(10, 12, 10, 12)
        editorArea.text = I18n.t("editor.placeholder")
        editorArea.caretPosition = 0
        editorArea.document.addDocumentListener(StatusDocumentListener())

        statusLabel.border = BorderFactory.createEmptyBorder(6, 4, 0, 4)
        statusLabel.foreground = Color.DARK_GRAY

        add(JScrollPane(editorArea), BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)

        refreshStatus()
    }

    /**
     * Refreshes all texts that depend on the current locale without touching
     * the manuscript content the user may have typed.
     */
    fun applyTexts() {
        refreshStatus()
    }

    /**
     * Recomputes the word and character counters shown in the status bar.
     */
    private fun refreshStatus() {
        val text = editorArea.text
        val wordCount = text.trim().split(WHITESPACE_REGEX).count { it.isNotBlank() }
        statusLabel.text = String.format(
            "%s: %d | %s: %d",
            I18n.t("status.words"),
            wordCount,
            I18n.t("status.characters"),
            text.length
        )
    }

    /**
     * Updates the status bar whenever the manuscript text changes.
     */
    private inner class StatusDocumentListener : DocumentListener {
        override fun insertUpdate(event: DocumentEvent) {
            refreshStatus()
        }

        override fun removeUpdate(event: DocumentEvent) {
            refreshStatus()
        }

        override fun changedUpdate(event: DocumentEvent) {
            refreshStatus()
        }
    }

    companion object {
        private val WHITESPACE_REGEX = Regex("\\s+")
    }
}