package icejawriter.ui

import icejawriter.I18n
import icejawriter.export.ExportOptions
import icejawriter.export.SceneSeparator
import icejawriter.model.ProjectDocument
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Frame
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JTextField

/** Export request collected by the dialog (ФР-92, ФР-93). */
data class ExportRequest(
    val file: Path,
    val options: ExportOptions,
)

/**
 * DOCX export dialog (ФР-92): title page, status filter, separator and [NEW]
 * removal options plus the target file.
 */
class ExportDialog(
    owner: Frame?,
    project: ProjectDocument,
    defaultDirectory: Path,
) : JDialog(owner, I18n.t("export.title"), true) {

    var result: ExportRequest? = null

    private val titlePageCheck = JCheckBox(I18n.t("export.includeTitlePage"), true)
    private val approvedOnlyCheck = JCheckBox(I18n.t("export.approvedOnly"), false)
    private val stripNewCheck = JCheckBox(I18n.t("export.stripNew"), true)
    private val separatorCombo = JComboBox(SceneSeparator.entries.toTypedArray())
    private val fileField = JTextField(
        defaultDirectory.resolve(NewProjectDialog.sanitizeFileName(project.title) + ".docx").toString(),
        34,
    )

    init {
        separatorCombo.renderer = object : javax.swing.DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: javax.swing.JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): java.awt.Component {
                val key = "export.separator." + (value as? SceneSeparator ?: SceneSeparator.BLANK).name.lowercase()
                return super.getListCellRendererComponent(list, I18n.t(key), index, isSelected, cellHasFocus)
            }
        }
        separatorCombo.selectedItem = SceneSeparator.BLANK

        val form = Theme.formPanel()
        Theme.addRow(form, "export.options", titlePageCheck, "export.includeTitlePage.tooltip")
        form.add(approvedOnlyCheck)
        form.add(stripNewCheck)
        Theme.addRow(form, "export.separator", separatorCombo, "export.separator.tooltip")

        val filePanel = JPanel(BorderLayout(6, 0))
        filePanel.add(fileField, BorderLayout.CENTER)
        val browseButton = JButton(I18n.t("export.browse"))
        browseButton.addActionListener { chooseFile() }
        filePanel.add(browseButton, BorderLayout.EAST)
        Theme.addRow(form, "export.file", filePanel, "export.file.tooltip")

        val hint = JLabel(I18n.t("export.hint"))
        hint.foreground = Theme.HINT_COLOR

        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 6))
        val exportButton = JButton(I18n.t("export.run"))
        val cancelButton = JButton(I18n.t("export.cancel"))
        exportButton.addActionListener { confirm() }
        cancelButton.addActionListener { dispose() }
        buttons.add(exportButton)
        buttons.add(cancelButton)

        contentPane.layout = BorderLayout()
        contentPane.add(form, BorderLayout.CENTER)
        val south = JPanel(BorderLayout())
        south.add(hint, BorderLayout.NORTH)
        south.add(buttons, BorderLayout.SOUTH)
        contentPane.add(south, BorderLayout.SOUTH)
        pack()
        setLocationRelativeTo(owner)
    }

    private fun chooseFile() {
        val chooser = JFileChooser(fileField.text)
        chooser.dialogTitle = I18n.t("export.chooseFile")
        if (chooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            var selected = chooser.selectedFile
            if (!selected.name.lowercase().endsWith(".docx")) {
                selected = java.io.File(selected.parentFile, selected.name + ".docx")
            }
            fileField.text = selected.absolutePath
        }
    }

    private fun confirm() {
        val path = fileField.text.trim()
        if (path.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                I18n.t("export.error.noFile"),
                I18n.t("export.title"),
                JOptionPane.WARNING_MESSAGE,
            )
            return
        }
        val target = Path.of(path)
        if (java.nio.file.Files.exists(target)) {
            val answer = JOptionPane.showConfirmDialog(
                this,
                String.format(I18n.t("export.overwrite"), target.toString()),
                I18n.t("export.title"),
                JOptionPane.YES_NO_OPTION,
            )
            if (answer != JOptionPane.YES_OPTION) return
        }
        result = ExportRequest(
            file = target,
            options = ExportOptions(
                includeTitlePage = titlePageCheck.isSelected,
                approvedOnly = approvedOnlyCheck.isSelected,
                separator = separatorCombo.selectedItem as? SceneSeparator ?: SceneSeparator.BLANK,
                stripNewMarks = stripNewCheck.isSelected,
            ),
        )
        dispose()
    }
}