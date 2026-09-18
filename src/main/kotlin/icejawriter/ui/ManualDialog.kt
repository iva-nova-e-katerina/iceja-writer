package icejawriter.ui

import icejawriter.I18n
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Frame
import java.nio.charset.StandardCharsets
import javax.swing.JDialog
import javax.swing.JScrollPane
import javax.swing.JTextArea

/**
 * User manual viewer (ФР-110, ИНТ-33): shows the bundled Russian manual
 * `manual/UserManual.md` in a scrollable window. The manual ships inside the
 * JAR, so it works offline and without installation.
 */
class ManualDialog(owner: Frame?) : JDialog(owner, I18n.t("manual.title"), true) {

    init {
        val area = JTextArea().apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            text = loadManual()
            caretPosition = 0
            font = java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.PLAIN, 14)
        }
        val scroll = JScrollPane(area).apply {
            preferredSize = Dimension(760, 560)
        }
        contentPane.layout = BorderLayout()
        contentPane.add(scroll, BorderLayout.CENTER)
        pack()
        setLocationRelativeTo(owner)
    }

    /** Loads the manual from the JAR; a missing resource shows a hint. */
    private fun loadManual(): String {
        val stream = ManualDialog::class.java.getResourceAsStream("/manual/UserManual.md")
            ?: return I18n.t("manual.missing")
        return stream.use { it.readBytes().toString(StandardCharsets.UTF_8) }
    }
}