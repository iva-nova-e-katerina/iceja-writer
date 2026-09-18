package icejawriter.ui

import icejawriter.I18n
import icejawriter.llm.ChangeProtocol
import icejawriter.llm.DraftResult
import icejawriter.llm.LlmException
import icejawriter.llm.LlmService
import icejawriter.model.ChronologyEvent
import icejawriter.model.GlossaryTerm
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Frame
import java.awt.GridLayout
import java.util.concurrent.CancellationException
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JProgressBar
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.swing.SwingWorker

/**
 * LLM draft panel (LLM-20..LLM-23, LLM-30..LLM-32) shown as a tab of the
 * scene editor. Generation runs asynchronously (НФР-02, ИНТ-40): the draft is
 * never written into the scene automatically — the author accepts, edits,
 * requests an alternative or rejects it.
 */
class DraftPanel(private val context: EditorContext) : JPanel(BorderLayout()) {

    private var sceneId: String? = null
    private val alternatives = mutableListOf<DraftResult>()
    private var currentIndex = -1
    private var worker: SwingWorker<*, Void>? = null

    private val draftArea = JTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
        isEditable = false
    }
    private val protocolPanel = ProtocolPanel()
    private val statusLabel = JLabel(" ")
    private val progressBar = JProgressBar().apply {
        isIndeterminate = true
        isVisible = false
    }
    private val alternativeCombo = JComboBox<Int>()

    private val generateButton = JButton(I18n.t("draft.generate"))
    private val cancelButton = JButton(I18n.t("draft.cancel"))
    private val acceptButton = JButton(I18n.t("draft.accept"))
    private val editButton = JButton(I18n.t("draft.edit"))
    private val alternativeButton = JButton(I18n.t("draft.alternative"))
    private val rejectButton = JButton(I18n.t("draft.reject"))
    private val summaryButton = JButton(I18n.t("draft.summary"))

    init {
        val buttons = JPanel(FlowLayout(FlowLayout.LEFT, 6, 6))
        buttons.add(generateButton)
        buttons.add(cancelButton)
        buttons.add(acceptButton)
        buttons.add(editButton)
        buttons.add(alternativeButton)
        buttons.add(rejectButton)
        buttons.add(summaryButton)

        val top = JPanel(BorderLayout())
        top.add(buttons, BorderLayout.WEST)
        val alternativePanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 6))
        alternativePanel.add(JLabel(I18n.t("draft.variant")))
        alternativePanel.add(alternativeCombo)
        top.add(alternativePanel, BorderLayout.EAST)

        val bottom = JPanel(BorderLayout())
        bottom.add(statusLabel, BorderLayout.WEST)
        bottom.add(progressBar, BorderLayout.EAST)

        val split = JSplitPane(JSplitPane.VERTICAL_SPLIT, JScrollPane(draftArea), protocolPanel)
        split.resizeWeight = 0.65
        split.dividerLocation = 420

        add(top, BorderLayout.NORTH)
        add(split, BorderLayout.CENTER)
        add(bottom, BorderLayout.SOUTH)

        generateButton.addActionListener { generate() }
        cancelButton.addActionListener { cancel() }
        acceptButton.addActionListener { accept() }
        editButton.addActionListener { toggleEdit() }
        alternativeButton.addActionListener { generate() }
        rejectButton.addActionListener { clearAll() }
        summaryButton.addActionListener { generateSummary() }
        alternativeCombo.addActionListener {
            val index = alternativeCombo.selectedItem as? Int ?: return@addActionListener
            showAlternative(index)
        }
        setBusy(false)
        updateButtons()
    }

    /** Binds the panel to a scene; clears previous results. */
    fun showScene(sceneId: String) {
        this.sceneId = sceneId
        clearAll()
        statusLabel.text = " "
    }

    /** Called by the main window on locale change; nothing to reload. */
    fun refresh() {
        updateButtons()
    }

    /**
     * Starts generation for the current scene (toolbar "Draft" button).
     * [onFinished] receives true when a draft was produced, false when the
     * request was cancelled or failed — the continuous chain uses it.
     */
    fun requestDraft(onFinished: ((Boolean) -> Unit)? = null) {
        generate(onFinished)
    }

    private fun llmService(): LlmService? {
        val settings = context.settings.llm
        val service = LlmService(settings, if (I18n.locale.language == "en") "en" else "ru")
        if (!service.isConfigured()) {
            context.showError("draft.error.title", I18n.t("draft.error.notConfigured"))
            return null
        }
        return service
    }

    /** Starts asynchronous generation (LLM-21). */
    private fun generate(onFinished: ((Boolean) -> Unit)? = null) {
        val id = sceneId
        val document = context.session.document
        if (id == null || document == null) {
            onFinished?.invoke(false)
            return
        }
        val service = llmService()
        if (service == null) {
            onFinished?.invoke(false)
            return
        }
        setBusy(true)
        context.setLlmState("status.llm.working")
        statusLabel.text = I18n.t("draft.generating")
        worker = object : SwingWorker<DraftResult, Void>() {
            override fun doInBackground(): DraftResult = service.generateDraft(document, id)

            override fun done() {
                setBusy(false)
                var produced = false
                try {
                    val result = get()
                    addAlternative(result)
                    context.setLlmState("status.llm.done")
                    statusLabel.text = I18n.t("draft.done")
                    produced = true
                } catch (e: CancellationException) {
                    context.setLlmState("status.llm.cancelled")
                    statusLabel.text = I18n.t("draft.cancelled")
                } catch (e: Exception) {
                    context.setLlmState("status.llm.error")
                    statusLabel.text = " "
                    LlmErrors.log("Draft generation failed", e)
                    context.showError("draft.error.title", LlmErrors.message(e))
                } finally {
                    onFinished?.invoke(produced)
                }
            }
        }.also { it.execute() }
    }

    private fun cancel() {
        worker?.cancel(true)
        setBusy(false)
        context.setLlmState("status.llm.cancelled")
    }

    /**
     * Cancels the running generation (used by the toolbar "Stop" button, which
     * stops the whole continuous card chain).
     */
    fun cancelGeneration() {
        worker?.cancel(true)
    }

    /**
     * Generates the scene summary (LLM-50) and stores it into the card after
     * the author explicitly requested it. The summary of the previous scene is
     * then automatically part of the next generation context (LLM-51).
     */
    private fun generateSummary() {
        val id = sceneId ?: return
        val document = context.session.document ?: return
        val service = llmService() ?: return
        setBusy(true)
        context.setLlmState("status.llm.working")
        statusLabel.text = I18n.t("draft.summary.generating")
        worker = object : SwingWorker<String, Void>() {
            override fun doInBackground(): String = service.summarizeScene(document, id)

            override fun done() {
                setBusy(false)
                try {
                    val summary = get().trim()
                    if (summary.isNotEmpty()) {
                        context.session.update { doc ->
                            doc.copy(scenes = doc.scenes.map { scene ->
                                if (scene.card.id == id) {
                                    scene.copy(card = scene.card.copy(summary = summary))
                                } else {
                                    scene
                                }
                            })
                        }
                        context.documentEdited()
                        context.reloadScene(id)
                        context.setLlmState("status.llm.done")
                        statusLabel.text = I18n.t("draft.summary.done")
                    }
                } catch (e: CancellationException) {
                    context.setLlmState("status.llm.cancelled")
                    statusLabel.text = I18n.t("draft.cancelled")
                } catch (e: Exception) {
                    context.setLlmState("status.llm.error")
                    statusLabel.text = " "
                    LlmErrors.log("Scene summary generation failed", e)
                    context.showError("draft.error.title", LlmErrors.message(e))
                }
            }
        }.also { it.execute() }
    }

    /** Adds a generated variant, keeping at most three (LLM-23). */
    private fun addAlternative(result: DraftResult) {
        if (alternatives.size >= MAX_ALTERNATIVES) {
            statusLabel.text = I18n.t("draft.tooMany")
            return
        }
        alternatives += result
        alternativeCombo.addItem(alternatives.size)
        alternativeCombo.selectedIndex = alternatives.size - 1
    }

    private fun showAlternative(index: Int) {
        val result = alternatives.getOrNull(index) ?: return
        currentIndex = index
        draftArea.text = result.draft
        draftArea.caretPosition = 0
        protocolPanel.setProtocol(result.protocol)
        updateButtons()
    }

    /** Writes the draft into the scene text (LLM-22, explicit accept). */
    private fun accept() {
        if (acceptDraft()) context.showStatus(I18n.t("draft.accepted"))
    }

    /**
     * Writes the shown draft into the scene text and returns true on success.
     * Used both by the "Accept" button and by the continuous chain, which
     * writes the generated draft automatically (author's choice).
     */
    fun acceptDraft(): Boolean {
        val id = sceneId ?: return false
        val draft = draftArea.text
        if (draft.isBlank()) return false
        context.session.update { document ->
            document.copy(scenes = document.scenes.map { scene ->
                if (scene.card.id == id) scene.copy(text = draft) else scene
            })
        }
        context.documentEdited()
        context.reloadScene(id)
        clearAll()
        return true
    }

    private fun toggleEdit() {
        draftArea.isEditable = !draftArea.isEditable
        editButton.text = if (draftArea.isEditable) I18n.t("draft.edit.done") else I18n.t("draft.edit")
    }

    private fun clearAll() {
        alternatives.clear()
        currentIndex = -1
        alternativeCombo.removeAllItems()
        draftArea.text = ""
        draftArea.isEditable = false
        editButton.text = I18n.t("draft.edit")
        protocolPanel.setProtocol(ChangeProtocol())
        updateButtons()
    }

    private fun setBusy(busy: Boolean) {
        generateButton.isEnabled = !busy
        alternativeButton.isEnabled = !busy && alternatives.size < MAX_ALTERNATIVES
        cancelButton.isEnabled = busy
        summaryButton.isEnabled = !busy && sceneId != null
        progressBar.isVisible = busy
        acceptButton.isEnabled = !busy && draftArea.text.isNotBlank()
        editButton.isEnabled = !busy && draftArea.text.isNotBlank()
        rejectButton.isEnabled = !busy && alternatives.isNotEmpty()
    }

    private fun updateButtons() {
        acceptButton.isEnabled = draftArea.text.isNotBlank()
        editButton.isEnabled = draftArea.text.isNotBlank()
        rejectButton.isEnabled = alternatives.isNotEmpty()
        alternativeButton.isEnabled = alternatives.size < MAX_ALTERNATIVES
        summaryButton.isEnabled = sceneId != null
    }

    /**
     * Protocol panel (LLM-30..LLM-32): four lists with explicit "add to bible"
     * buttons; nothing is applied automatically.
     */
    private inner class ProtocolPanel : JPanel(GridLayout(2, 2, 6, 6)) {

        private val lists = linkedMapOf(
            "draft.protocol.newFacts" to DefaultListModel<String>(),
            "draft.protocol.stateChanges" to DefaultListModel<String>(),
            "draft.protocol.openQuestions" to DefaultListModel<String>(),
            "draft.protocol.contradictions" to DefaultListModel<String>(),
        )

        init {
            border = javax.swing.BorderFactory.createEmptyBorder(4, 4, 4, 4)
            for ((titleKey, model) in lists) {
                val list = JList(model)
                val panel = JPanel(BorderLayout())
                panel.add(JLabel(I18n.t(titleKey)), BorderLayout.NORTH)
                panel.add(JScrollPane(list), BorderLayout.CENTER)
                val addButton = JButton(I18n.t("draft.protocol.addToBible"))
                addButton.addActionListener { addSelectedToBible(list) }
                panel.add(addButton, BorderLayout.SOUTH)
                add(panel)
            }
        }

        fun setProtocol(protocol: ChangeProtocol) {
            setList("draft.protocol.newFacts", protocol.newFacts)
            setList("draft.protocol.stateChanges", protocol.stateChanges)
            setList("draft.protocol.openQuestions", protocol.openQuestions)
            setList("draft.protocol.contradictions", protocol.contradictions)
        }

        private fun setList(key: String, values: List<String>) {
            val model = lists.getValue(key)
            model.clear()
            values.forEach { model.addElement(it) }
        }

        private fun addSelectedToBible(list: JList<String>) {
            val text = list.selectedValue ?: return
            val dialog = AddToBibleDialog(
                owner = this@DraftPanel.topLevelAncestor as? Frame,
                characters = context.characterChoices().map { it.id to it.name },
                storylines = context.session.document?.bible?.storylines?.map { it.id to it.name } ?: emptyList(),
                text = text,
            )
            dialog.isVisible = true
            val request = dialog.result ?: return
            applyToBible(request.section, request.targetId, request.text)
        }
    }

    /** Applies one protocol item to the chosen bible section (LLM-32). */
    private fun applyToBible(section: String, targetId: String?, text: String) {
        context.session.update { document ->
            val bible = document.bible
            val updated = when (section) {
                "characters" -> bible.copy(characters = bible.characters.map { character ->
                    if (character.id == targetId) character.copy(notes = appendLine(character.notes, text)) else character
                })
                "chronology" -> bible.copy(chronology = bible.chronology + ChronologyEvent(date = "", event = text))
                "rules" -> bible.copy(rules = bible.rules.copy(notes = appendLine(bible.rules.notes, text)))
                "glossary" -> bible.copy(glossary = bible.glossary + GlossaryTerm(term = text.take(60), definition = text))
                "world" -> bible.copy(world = bible.world.copy(notes = appendLine(bible.world.notes, text)))
                "style" -> bible.copy(style = bible.style.copy(notes = appendLine(bible.style.notes, text)))
                "storylines" -> bible.copy(storylines = bible.storylines.map { storyline ->
                    if (storyline.id == targetId) storyline.copy(notes = appendLine(storyline.notes, text)) else storyline
                })
                else -> bible
            }
            document.copy(bible = updated)
        }
        context.documentEdited()
        context.showStatus(I18n.t("draft.protocol.added"))
    }

    private fun appendLine(existing: String, addition: String): String =
        if (existing.isBlank()) addition else existing.trimEnd() + "\n" + addition

    companion object {
        private const val MAX_ALTERNATIVES = 3
    }
}

/**
 * Request of the "add to bible" dialog: the target section, an optional target
 * element (character or storyline) and the text to append.
 */
data class AddToBibleRequest(
    val section: String,
    val targetId: String?,
    val text: String,
)

/**
 * Small dialog that lets the author choose where a protocol item goes
 * (LLM-32): characters / chronology / rules / glossary / world / style /
 * storylines, with a target character or storyline when relevant.
 */
class AddToBibleDialog(
    owner: Frame?,
    characters: List<Pair<String, String>>,
    storylines: List<Pair<String, String>>,
    private val text: String,
) : JDialog(owner, I18n.t("draft.addToBible.title"), true) {

    var result: AddToBibleRequest? = null

    private val sectionCombo = JComboBox(SECTIONS)
    private val targetCombo = JComboBox<String>()
    private val textArea = JTextArea(text, 5, 40).apply { lineWrap = true; wrapStyleWord = true }

    init {
        sectionCombo.renderer = object : javax.swing.DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): java.awt.Component =
                super.getListCellRendererComponent(list, I18n.t("bible.section.$value"), index, isSelected, cellHasFocus)
        }

        val form = Theme.formPanel()
        Theme.addRow(form, "draft.addToBible.section", sectionCombo, "draft.addToBible.section.tooltip")
        Theme.addRow(form, "draft.addToBible.target", targetCombo, "draft.addToBible.target.tooltip")
        Theme.addRow(form, "draft.addToBible.text", Theme.scroll(textArea), null)

        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 6))
        val okButton = JButton(I18n.t("draft.addToBible.ok"))
        val cancelButton = JButton(I18n.t("draft.addToBible.cancel"))
        okButton.addActionListener { confirm() }
        cancelButton.addActionListener { dispose() }
        buttons.add(okButton)
        buttons.add(cancelButton)

        contentPane.layout = BorderLayout()
        contentPane.add(form, BorderLayout.CENTER)
        contentPane.add(buttons, BorderLayout.SOUTH)
        pack()
        setLocationRelativeTo(owner)

        val characterTargets = characters.map { it.first }
        val storylineTargets = storylines.map { it.first }
        sectionCombo.addActionListener {
            updateTargets(characterTargets, storylineTargets)
        }
        updateTargets(characterTargets, storylineTargets)
    }

    private fun updateTargets(characters: List<String>, storylines: List<String>) {
        targetCombo.removeAllItems()
        when (sectionCombo.selectedItem) {
            "characters" -> characters.forEach { targetCombo.addItem(it) }
            "storylines" -> storylines.forEach { targetCombo.addItem(it) }
        }
        targetCombo.isEnabled = targetCombo.itemCount > 0
    }

    private fun confirm() {
        val section = sectionCombo.selectedItem?.toString() ?: return
        val target = targetCombo.selectedItem?.toString()
        if ((section == "characters" && target == null) || (section == "storylines" && target == null)) {
            JOptionPane.showMessageDialog(
                this,
                I18n.t("draft.addToBible.noTarget"),
                I18n.t("draft.addToBible.title"),
                JOptionPane.WARNING_MESSAGE,
            )
            return
        }
        result = AddToBibleRequest(section = section, targetId = target, text = textArea.text)
        dispose()
    }

    companion object {
        private val SECTIONS = arrayOf("characters", "chronology", "rules", "glossary", "world", "style", "storylines")
    }
}