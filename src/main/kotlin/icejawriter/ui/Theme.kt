package icejawriter.ui

import icejawriter.I18n
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BorderFactory
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingConstants
import javax.swing.border.EmptyBorder

/**
 * Small helpers shared by all editor panels of the light themed UI (ИНТ-01).
 */
object Theme {

    /** Accent color for section headers. */
    val HEADER_COLOR: Color = Color(0x2F4F6F)

    /** Error color used for over-limit counters and validation hints. */
    val ERROR_COLOR: Color = Color(0xB00020)

    /** Muted color for hints. */
    val HINT_COLOR: Color = Color(0x666666)

    /** Creates a bold section header label. */
    fun header(key: String): JLabel = JLabel(I18n.t(key)).apply {
        font = font.deriveFont(font.style or java.awt.Font.BOLD, font.size + 2f)
        foreground = HEADER_COLOR
        border = EmptyBorder(0, 0, 6, 0)
    }

    /**
     * Creates a two-column form panel with the given label/field rows and a
     * padded border. Rows are laid out top-down in a BorderLayout stack.
     */
    fun formPanel(): JPanel = JPanel().apply {
        layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
        border = EmptyBorder(8, 8, 8, 8)
    }

    /** Adds a labeled component row to a form panel. */
    fun addRow(form: JPanel, labelKey: String, component: JComponent, tooltipKey: String? = null) {
        val row = JPanel(BorderLayout(6, 2))
        val label = JLabel(I18n.t(labelKey))
        label.preferredSize = Dimension(160, label.preferredSize.height)
        label.verticalAlignment = SwingConstants.TOP
        row.add(label, BorderLayout.WEST)
        row.add(component, BorderLayout.CENTER)
        if (tooltipKey != null) {
            val tooltip = I18n.t(tooltipKey)
            label.toolTipText = tooltip
            component.toolTipText = tooltip
        }
        row.maximumSize = Dimension(Int.MAX_VALUE, row.preferredSize.height)
        form.add(row)
        form.add(javax.swing.Box.createVerticalStrut(4))
    }

    /** Creates a wrapping multi-line text area with a scroll pane. */
    fun textArea(rows: Int, tooltipKey: String? = null): JTextArea = JTextArea(rows, 40).apply {
        lineWrap = true
        wrapStyleWord = true
        if (tooltipKey != null) toolTipText = I18n.t(tooltipKey)
    }

    /** Wraps a component into a scroll pane with a thin border. */
    fun scroll(component: Component): JScrollPane = JScrollPane(component).apply {
        border = BorderFactory.createLineBorder(Color(0xCCCCCC))
    }

    /** Creates a horizontal button bar aligned to the left. */
    fun buttonBar(vararg buttons: JComponent): JPanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 6)).apply {
        buttons.forEach { add(it) }
    }

    /** Creates a panel with the given header and center component. */
    fun section(headerKey: String, center: Component): JPanel = JPanel(BorderLayout()).apply {
        border = EmptyBorder(10, 10, 10, 10)
        add(header(headerKey), BorderLayout.NORTH)
        add(center, BorderLayout.CENTER)
    }

    /** Creates a read-only single-line field. */
    fun readOnlyField(): JTextField = JTextField().apply {
        isEditable = false
        background = Color(0xF2F2F2)
    }
}