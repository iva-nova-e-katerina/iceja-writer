package icejawriter.ui

import icejawriter.I18n
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Frame
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.SwingUtilities

/**
 * Find and replace dialog for the scene text editor (ФР-50, Ctrl+F).
 * Searches the associated text area, selects the next occurrence and can
 * replace one occurrence or all of them.
 */
class FindReplaceDialog(
    owner: Frame?,
    private val textArea: SceneTextArea,
) : JDialog(owner, I18n.t("find.title"), false) {

    private val findField = JTextField(20)
    private val replaceField = JTextField(20)
    private val caseSensitive = JCheckBox(I18n.t("find.caseSensitive"))

    init {
        val form = JPanel(BorderLayout(6, 6))
        val labels = JPanel(java.awt.GridLayout(2, 1, 4, 4))
        labels.add(JLabel(I18n.t("find.find")))
        labels.add(JLabel(I18n.t("find.replace")))
        val fields = JPanel(java.awt.GridLayout(2, 1, 4, 4))
        fields.add(findField)
        fields.add(replaceField)
        form.add(labels, BorderLayout.WEST)
        form.add(fields, BorderLayout.CENTER)
        form.border = javax.swing.BorderFactory.createEmptyBorder(8, 8, 4, 8)

        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 6))
        val findButton = JButton(I18n.t("find.next"))
        val replaceButton = JButton(I18n.t("find.replaceOne"))
        val replaceAllButton = JButton(I18n.t("find.replaceAll"))
        val closeButton = JButton(I18n.t("find.close"))
        findButton.addActionListener { findNext() }
        replaceButton.addActionListener { replaceCurrent() }
        replaceAllButton.addActionListener { replaceAll() }
        closeButton.addActionListener { dispose() }
        buttons.add(caseSensitive)
        buttons.add(findButton)
        buttons.add(replaceButton)
        buttons.add(replaceAllButton)
        buttons.add(closeButton)

        contentPane.layout = BorderLayout()
        contentPane.add(form, BorderLayout.NORTH)
        contentPane.add(buttons, BorderLayout.SOUTH)
        findField.addActionListener { findNext() }
        pack()
        setLocationRelativeTo(owner)
    }

    /** Opens the dialog and focuses the find field. */
    fun open() {
        val selection = textArea.selectedText
        if (!selection.isNullOrBlank() && !selection.contains('\n')) {
            findField.text = selection
        }
        isVisible = true
        SwingUtilities.invokeLater {
            findField.requestFocusInWindow()
            findField.selectAll()
        }
    }

    /** Selects the next occurrence after the caret, wrapping around. */
    private fun findNext() {
        val query = findField.text
        if (query.isEmpty()) return
        val text = textArea.text
        val ignoreCase = !caseSensitive.isSelected
        val from = textArea.selectionEnd.coerceAtMost(text.length)
        var index = text.indexOf(query, startIndex = from, ignoreCase = ignoreCase)
        if (index < 0) index = text.indexOf(query, startIndex = 0, ignoreCase = ignoreCase)
        if (index < 0) return
        textArea.select(index, index + query.length)
        textArea.requestFocusInWindow()
    }

    /** Replaces the current selection when it matches the query. */
    private fun replaceCurrent() {
        val query = findField.text
        val selected = textArea.selectedText
        val matches = if (caseSensitive.isSelected) selected == query else selected.equals(query, ignoreCase = true)
        if (query.isNotEmpty() && matches) {
            textArea.replaceSelection(replaceField.text)
        }
        findNext()
    }

    /** Replaces every occurrence in the text. */
    private fun replaceAll() {
        val query = findField.text
        if (query.isEmpty()) return
        val replacement = replaceField.text
        val text = textArea.text
        val result = if (caseSensitive.isSelected) {
            text.replace(query, replacement)
        } else {
            text.replace(query, replacement, ignoreCase = true)
        }
        if (result != text) {
            textArea.text = result
            textArea.refreshHighlights()
        }
    }
}