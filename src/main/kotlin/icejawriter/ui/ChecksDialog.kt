package icejawriter.ui

import icejawriter.I18n
import icejawriter.llm.CheckIssue
import icejawriter.llm.LlmException
import icejawriter.llm.LlmService
import icejawriter.model.SceneStructure
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Frame
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.CancellationException
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JScrollPane
import javax.swing.JTable
import javax.swing.SwingWorker
import javax.swing.table.AbstractTableModel

/** Check types of the "editor-continuity" dialog (LLM-40). */
enum class CheckType(val labelKey: String) {
    CONTINUITY("checks.type.continuity"),
    LOGIC("checks.type.logic"),
    CHRONOLOGY("checks.type.chronology"),
    KNOWLEDGE("checks.type.knowledge"),
    STYLE("checks.type.style"),
    PLOT_HOLES("checks.type.plotholes"),
}

/**
 * Continuity check dialog (LLM-40..LLM-42): choose a check type and a scope
 * (whole novel / act / chapter), run the check asynchronously, and get only a
 * list of problems with scene references. Double-clicking a problem opens the
 * corresponding scene.
 */
class ChecksDialog(
    owner: Frame?,
    private val context: EditorContext,
) : JDialog(owner, I18n.t("checks.title"), false) {

    private val typeCombo = JComboBox(CheckType.entries.toTypedArray())
    private val scopeCombo = JComboBox<ScopeOption>()
    private val model = IssuesTableModel()
    private val table = JTable(model)
    private val runButton = JButton(I18n.t("checks.run"))
    private val cancelButton = JButton(I18n.t("checks.cancel"))
    private val progressBar = JProgressBar().apply {
        isIndeterminate = true
        isVisible = false
    }
    private val statusLabel = JLabel(" ")
    private var worker: SwingWorker<List<CheckIssue>, Void>? = null

    init {
        typeCombo.renderer = object : javax.swing.DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: javax.swing.JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): java.awt.Component {
                val key = (value as? CheckType ?: CheckType.CONTINUITY).labelKey
                return super.getListCellRendererComponent(list, I18n.t(key), index, isSelected, cellHasFocus)
            }
        }
        fillScopes()
        statusLabel.foreground = Theme.HINT_COLOR

        val top = JPanel(FlowLayout(FlowLayout.LEFT, 6, 6))
        top.add(JLabel(I18n.t("checks.type")))
        top.add(typeCombo)
        top.add(JLabel(I18n.t("checks.scope")))
        top.add(scopeCombo)
        top.add(runButton)
        top.add(cancelButton)

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2) openSelectedIssue()
            }
        })

        val bottom = JPanel(BorderLayout())
        bottom.add(statusLabel, BorderLayout.WEST)
        bottom.add(progressBar, BorderLayout.EAST)

        contentPane.layout = BorderLayout()
        contentPane.add(top, BorderLayout.NORTH)
        contentPane.add(JScrollPane(table), BorderLayout.CENTER)
        contentPane.add(bottom, BorderLayout.SOUTH)

        runButton.addActionListener { runChecks() }
        cancelButton.addActionListener { cancelChecks() }
        setBusy(false)
        setSize(760, 520)
        setLocationRelativeTo(owner)
    }

    /** Rebuilds the scope choices from the current project structure. */
    fun fillScopes() {
        scopeCombo.removeAllItems()
        scopeCombo.addItem(ScopeOption(I18n.t("checks.scope.novel"), LlmService.SCOPE_NOVEL))
        val document = context.session.document ?: return
        for (act in SceneStructure.acts(document.scenes)) {
            scopeCombo.addItem(
                ScopeOption(String.format(I18n.t("checks.scope.act"), act), "act:$act"),
            )
            for (chapter in SceneStructure.chapters(document.scenes, act)) {
                scopeCombo.addItem(
                    ScopeOption(String.format(I18n.t("checks.scope.chapter"), act, chapter), "chapter:$act:$chapter"),
                )
            }
        }
    }

    private fun runChecks() {
        val document = context.session.document ?: return
        val settings = context.settings.llm
        val language = if (I18n.locale.language == "en") "en" else "ru"
        val service = LlmService(settings, language)
        if (!service.isConfigured()) {
            context.showError("checks.title", I18n.t("draft.error.notConfigured"))
            return
        }
        val type = typeCombo.selectedItem as? CheckType ?: CheckType.CONTINUITY
        val scope = scopeCombo.selectedItem as? ScopeOption ?: return
        val sceneIds = service.scenesInScope(document, scope.scope)
        if (sceneIds.isEmpty()) {
            statusLabel.text = I18n.t("checks.noIssues")
            model.setIssues(emptyList())
            return
        }

        setBusy(true)
        context.setLlmState("status.llm.working")
        statusLabel.text = I18n.t("checks.running")
        worker = object : SwingWorker<List<CheckIssue>, Void>() {
            override fun doInBackground(): List<CheckIssue> =
                service.runChecks(document, sceneIds, I18n.t(type.labelKey), I18n.t(scope.label))

            override fun done() {
                setBusy(false)
                try {
                    val issues = get()
                    model.setIssues(issues)
                    context.setLlmState("status.llm.done")
                    statusLabel.text = if (issues.isEmpty()) {
                        I18n.t("checks.noIssues")
                    } else {
                        String.format(I18n.t("checks.results"), issues.size)
                    }
                } catch (e: CancellationException) {
                    context.setLlmState("status.llm.cancelled")
                    statusLabel.text = I18n.t("draft.cancelled")
                } catch (e: Exception) {
                    context.setLlmState("status.llm.error")
                    LlmErrors.log("Continuity check failed", e)
                    statusLabel.text = LlmErrors.message(e)
                    statusLabel.foreground = Theme.ERROR_COLOR
                }
            }
        }.also { it.execute() }
    }

    private fun cancelChecks() {
        worker?.cancel(true)
        setBusy(false)
        context.setLlmState("status.llm.cancelled")
    }

    private fun setBusy(busy: Boolean) {
        runButton.isEnabled = !busy
        cancelButton.isEnabled = busy
        progressBar.isVisible = busy
        statusLabel.foreground = Theme.HINT_COLOR
    }

    /** Double-click opens the scene the problem refers to (LLM-42). */
    private fun openSelectedIssue() {
        val row = table.selectedRow
        if (row < 0) return
        val issue = model.issues().getOrNull(table.convertRowIndexToModel(row)) ?: return
        val number = issue.sceneNumber ?: return
        val scene = context.session.document?.scenes?.firstOrNull { it.card.number == number } ?: return
        context.openScene(scene.card.id)
    }

    /** A scope choice: a localized label and the machine scope string. */
    private data class ScopeOption(val label: String, val scope: String)

    private class IssuesTableModel : AbstractTableModel() {
        private var issues: List<CheckIssue> = emptyList()

        fun setIssues(newIssues: List<CheckIssue>) {
            issues = newIssues
            fireTableDataChanged()
        }

        fun issues(): List<CheckIssue> = issues

        override fun getRowCount(): Int = issues.size
        override fun getColumnCount(): Int = 2
        override fun getColumnName(column: Int): String =
            if (column == 0) I18n.t("checks.column.scene") else I18n.t("checks.column.problem")

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val issue = issues[rowIndex]
            return if (columnIndex == 0) issue.sceneNumber?.toString().orEmpty() else issue.problem
        }
    }
}