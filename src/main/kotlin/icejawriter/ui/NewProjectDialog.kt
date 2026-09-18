package icejawriter.ui

import icejawriter.I18n
import icejawriter.config.AppSettings
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Frame
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextField

/**
 * Request collected by the new-project dialog (ФР-01): title, author and the
 * target .ijw file.
 */
data class NewProjectRequest(
    val title: String,
    val author: String,
    val file: Path,
)

/**
 * New project dialog (ФР-01, ИНТ-32): the author enters the novel title and
 * name, picks a directory and the application creates an empty `.ijw` there.
 * The dialog is a single step, well within the "no more than three steps"
 * requirement.
 */
class NewProjectDialog(
    owner: Frame?,
    private val settings: AppSettings,
) : JDialog(owner, I18n.t("newproject.title"), true) {

    /** Filled with the request when the user confirms; null when cancelled. */
    var result: NewProjectRequest? = null

    private val titleField = JTextField(28)
    private val authorField = JTextField(28)
    private val directoryField = JTextField(settings.effectiveProjectsDir(), 28)

    init {
        val form = Theme.formPanel()
        Theme.addRow(form, "newproject.novelTitle", titleField, "newproject.novelTitle.tooltip")
        Theme.addRow(form, "newproject.author", authorField, "newproject.author.tooltip")

        val directoryPanel = JPanel(BorderLayout(6, 0))
        directoryPanel.add(directoryField, BorderLayout.CENTER)
        val browseButton = JButton(I18n.t("newproject.browse"))
        browseButton.addActionListener { chooseDirectory() }
        directoryPanel.add(browseButton, BorderLayout.EAST)
        Theme.addRow(form, "newproject.directory", directoryPanel, "newproject.directory.tooltip")

        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 6))
        val okButton = JButton(I18n.t("newproject.create"))
        val cancelButton = JButton(I18n.t("newproject.cancel"))
        okButton.addActionListener { confirm() }
        cancelButton.addActionListener { dispose() }
        buttons.add(okButton)
        buttons.add(cancelButton)

        val hint = JLabel(I18n.t("newproject.hint"))
        hint.foreground = Theme.HINT_COLOR

        contentPane.layout = BorderLayout()
        contentPane.add(form, BorderLayout.CENTER)
        val south = JPanel(BorderLayout())
        south.add(hint, BorderLayout.NORTH)
        south.add(buttons, BorderLayout.SOUTH)
        contentPane.add(south, BorderLayout.SOUTH)
        pack()
        setLocationRelativeTo(owner)
    }

    private fun chooseDirectory() {
        val chooser = JFileChooser(directoryField.text.ifBlank { settings.effectiveProjectsDir() })
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.dialogTitle = I18n.t("newproject.chooseDirectory")
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            directoryField.text = chooser.selectedFile.absolutePath
        }
    }

    private fun confirm() {
        val title = titleField.text.trim()
        if (title.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                I18n.t("newproject.error.title"),
                I18n.t("newproject.title"),
                JOptionPane.WARNING_MESSAGE,
            )
            return
        }
        val directory = Path.of(directoryField.text.trim().ifBlank { settings.effectiveProjectsDir() })
        val file = directory.resolve(sanitizeFileName(title) + ".ijw")
        if (Files.exists(file)) {
            val answer = JOptionPane.showConfirmDialog(
                this,
                String.format(I18n.t("newproject.overwrite"), file.toString()),
                I18n.t("newproject.title"),
                JOptionPane.YES_NO_OPTION,
            )
            if (answer != JOptionPane.YES_OPTION) return
        }
        result = NewProjectRequest(title = title, author = authorField.text.trim(), file = file)
        dispose()
    }

    /**
     * Turns the novel title into a safe file name: characters forbidden by
     * Windows/Linux file systems are replaced with underscores (ФР-93 uses the
     * same rule for the DOCX file name).
     */
    companion object {
        fun sanitizeFileName(title: String): String {
            val cleaned = title.replace(Regex("[\\\\/:*?\"<>|\\n\\r\\t]"), "_").trim().trim('.')
            return cleaned.ifBlank { "novel" }
        }
    }
}