package icejawriter.ui

import icejawriter.I18n
import icejawriter.model.NovelDescription
import icejawriter.project.ProjectFormat
import icejawriter.project.ProjectJsonCodec
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

/**
 * Editor of the novel description (`novel.json`, ФР-20) with the mandatory
 * byte counter and the 2 KiB save block (ФР-21, ФРМ-05).
 */
class NovelPanel(private val context: EditorContext) : JPanel(BorderLayout()), ProjectEditor {

    private val titleField = JTextField()
    private val authorField = JTextField()
    private val loglineField = JTextField()
    private val themeField = JTextField()
    private val plotArea = Theme.textArea(12, "novel.plot.tooltip")
    private val counterLabel = JLabel()

    init {
        val form = Theme.formPanel()
        Theme.addRow(form, "novel.title", titleField, "novel.title.tooltip")
        Theme.addRow(form, "novel.author", authorField, "novel.author.tooltip")
        Theme.addRow(form, "novel.logline", loglineField, "novel.logline.tooltip")
        Theme.addRow(form, "novel.theme", themeField, "novel.theme.tooltip")
        Theme.addRow(form, "novel.plot", Theme.scroll(plotArea), "novel.plot.tooltip")
        form.add(counterLabel)

        add(Theme.header("novel.header"), BorderLayout.NORTH)
        add(Theme.scroll(form), BorderLayout.CENTER)

        val listener = object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = updateCounter()
            override fun removeUpdate(e: DocumentEvent) = updateCounter()
            override fun changedUpdate(e: DocumentEvent) = updateCounter()
        }
        listOf(titleField, authorField, loglineField, themeField).forEach { it.document.addDocumentListener(listener) }
        plotArea.document.addDocumentListener(listener)
        updateCounter()
    }

    /** Builds the description from the current field values. */
    private fun currentDescription(): NovelDescription = NovelDescription(
        title = titleField.text,
        author = authorField.text,
        logline = loglineField.text,
        theme = themeField.text,
        plot = plotArea.text,
    )

    /** True when the serialized description exceeds the 2 KiB limit (ФР-21). */
    fun isOverLimit(): Boolean =
        ProjectJsonCodec.novelByteCount(currentDescription()) > ProjectFormat.NOVEL_DESCRIPTION_BYTES_LIMIT

    /** Byte size shown in the counter. */
    fun byteSize(): Int = ProjectJsonCodec.novelByteCount(currentDescription())

    override fun commitToProject() {
        val description = currentDescription()
        context.session.updateIfChanged { document ->
            if (document.novel == description) document else document.copy(novel = description)
        }
    }

    override fun refresh() {
        val novel = context.session.document?.novel ?: NovelDescription()
        titleField.text = novel.title
        authorField.text = novel.author
        loglineField.text = novel.logline
        themeField.text = novel.theme
        plotArea.text = novel.plot
        updateCounter()
    }

    /**
     * Refreshes the "N / 2048 bytes" counter and colors it red when the limit
     * is exceeded (ФР-21).
     */
    private fun updateCounter() {
        val bytes = ProjectJsonCodec.novelByteCount(currentDescription())
        counterLabel.text = String.format(
            I18n.t("novel.counter"),
            bytes,
            ProjectFormat.NOVEL_DESCRIPTION_BYTES_LIMIT,
        )
        counterLabel.foreground = if (bytes > ProjectFormat.NOVEL_DESCRIPTION_BYTES_LIMIT) {
            Theme.ERROR_COLOR
        } else {
            Theme.HINT_COLOR
        }
    }
}