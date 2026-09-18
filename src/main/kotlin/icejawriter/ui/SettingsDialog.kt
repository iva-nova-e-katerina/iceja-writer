package icejawriter.ui

import icejawriter.I18n
import icejawriter.config.AppSettings
import icejawriter.config.LlmProviderType
import icejawriter.config.LlmSettings
import icejawriter.llm.LlmException
import icejawriter.llm.LlmService
import icejawriter.llm.ModelListCache
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Frame
import java.awt.GridLayout
import java.nio.file.Path
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JDialog
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JPasswordField
import javax.swing.JSpinner
import javax.swing.JTabbedPane
import javax.swing.JTextField
import javax.swing.SpinnerNumberModel
import javax.swing.SwingWorker

/**
 * Settings dialog (ФР-100..ФР-102, LLM-02): the "General" tab edits language,
 * zoom, autosave, projects directory and backup count; the "LLM" tab edits the
 * provider connection. The model is always chosen from the list downloaded
 * from the provider (LLM-03); manual model entry is impossible by design.
 */
class SettingsDialog(
    owner: Frame?,
    private val current: AppSettings,
    private val language: String,
) : JDialog(owner, I18n.t("settings.title"), true) {

    /** Filled with the edited settings when the user confirms. */
    var result: AppSettings? = null

    // --- general widgets ------------------------------------------------
    private val languageCombo = JComboBox(arrayOf(LANGUAGE_SYSTEM, "ru", "en"))
    private val zoomCombo = JComboBox(ZOOM_PRESETS)
    private val autosaveSpinner = JSpinner(SpinnerNumberModel(current.autosaveIntervalMinutes, 0, 60, 1))
    private val backupSpinner = JSpinner(SpinnerNumberModel(current.backupCount, 0, 9, 1))
    private val cardModeCombo = JComboBox(arrayOf(AppSettings.CARD_MODE_HUMAN, AppSettings.CARD_MODE_AI))
    private val projectsDirField = JTextField(current.effectiveProjectsDir(), 26)

    // --- LLM widgets ----------------------------------------------------
    private val providerCombo = JComboBox(LlmProviderType.entries.toTypedArray())
    private val baseUrlField = JTextField(current.llm.baseUrl, 30)
    private val apiKeyField = JPasswordField(current.llm.apiKey, 30)
    private val showKeyCheck = JCheckBox(I18n.t("settings.llm.showKey"))
    private val modelCombo = JComboBox<String>()
    private val temperatureSpinner = JSpinner(SpinnerNumberModel(current.llm.temperature, 0.0, 2.0, 0.1))
    private val maxTokensSpinner = JSpinner(
        SpinnerNumberModel(current.llm.maxTokens, 1, AppSettings.MAX_OUTPUT_TOKENS, 1024),
    )
    private val timeoutSpinner = JSpinner(SpinnerNumberModel(current.llm.timeoutSeconds, 10, 600, 10))
    private val contextLimitSpinner = JSpinner(
        SpinnerNumberModel(current.llm.contextTokenLimit, 1024, AppSettings.MAX_CONTEXT_TOKENS, 1024),
    )
    private val statusLabel = JLabel(" ")
    private val loadModelsButton = JButton(I18n.t("settings.llm.loadModels"))
    private val checkButton = JButton(I18n.t("settings.llm.check"))

    init {
        providerCombo.renderer = object : javax.swing.DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: javax.swing.JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): java.awt.Component {
                val key = "settings.llm.provider." + (value as? LlmProviderType ?: LlmProviderType.OPENAI).wireName
                return super.getListCellRendererComponent(list, I18n.t(key), index, isSelected, cellHasFocus)
            }
        }
        providerCombo.selectedItem = current.llm.provider
        languageCombo.selectedItem = current.language
        cardModeCombo.selectedItem = current.cardMode
        cardModeCombo.renderer = object : javax.swing.DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: javax.swing.JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ): java.awt.Component {
                val mode = value?.toString() ?: AppSettings.CARD_MODE_HUMAN
                val key = if (mode == AppSettings.CARD_MODE_AI) "settings.cardMode.ai" else "settings.cardMode.human"
                return super.getListCellRendererComponent(list, I18n.t(key), index, isSelected, cellHasFocus)
            }
        }
        zoomCombo.selectedItem = ZOOM_PRESETS.minByOrNull { kotlin.math.abs(it - current.zoomScale) } ?: 1.0
        modelCombo.isEnabled = false
        // LLM-05: reuse the session-cached model list when it matches the
        // current configuration; otherwise show the stored model only.
        val cachedModels = ModelListCache.get(
            current.llm.provider.wireName,
            current.llm.effectiveBaseUrl(),
            current.llm.apiKey,
            current.llm.model,
        )
        if (cachedModels != null) {
            applyModels(cachedModels)
        } else if (current.llm.model.isNotBlank()) {
            modelCombo.addItem(current.llm.model)
            modelCombo.selectedItem = current.llm.model
            modelCombo.isEnabled = true
        }
        statusLabel.foreground = Theme.HINT_COLOR

        val tabs = JTabbedPane()
        tabs.addTab(I18n.t("settings.tab.general"), buildGeneralTab())
        tabs.addTab(I18n.t("settings.tab.llm"), buildLlmTab())

        val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 6, 6))
        val okButton = JButton(I18n.t("settings.ok"))
        val cancelButton = JButton(I18n.t("settings.cancel"))
        okButton.addActionListener { confirm() }
        cancelButton.addActionListener { dispose() }
        buttons.add(okButton)
        buttons.add(cancelButton)

        contentPane.layout = BorderLayout()
        contentPane.add(tabs, BorderLayout.CENTER)
        contentPane.add(buttons, BorderLayout.SOUTH)
        pack()
        setLocationRelativeTo(owner)
    }

    private fun buildGeneralTab(): JPanel {
        val form = Theme.formPanel()
        Theme.addRow(form, "settings.language", languageCombo, "settings.language.tooltip")
        Theme.addRow(form, "settings.zoom", zoomCombo, "settings.zoom.tooltip")
        Theme.addRow(form, "settings.autosave", autosaveSpinner, "settings.autosave.tooltip")
        Theme.addRow(form, "settings.backups", backupSpinner, "settings.backups.tooltip")
        Theme.addRow(form, "settings.cardMode", cardModeCombo, "settings.cardMode.tooltip")

        val directoryPanel = JPanel(BorderLayout(6, 0))
        directoryPanel.add(projectsDirField, BorderLayout.CENTER)
        val browseButton = JButton(I18n.t("settings.browse"))
        browseButton.addActionListener {
            val chooser = JFileChooser(projectsDirField.text)
            chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                projectsDirField.text = chooser.selectedFile.absolutePath
            }
        }
        directoryPanel.add(browseButton, BorderLayout.EAST)
        Theme.addRow(form, "settings.projectsDir", directoryPanel, "settings.projectsDir.tooltip")
        return form
    }

    private fun buildLlmTab(): JPanel {
        val form = Theme.formPanel()
        Theme.addRow(form, "settings.llm.provider", providerCombo, "settings.llm.provider.tooltip")
        Theme.addRow(form, "settings.llm.baseUrl", baseUrlField, "settings.llm.baseUrl.tooltip")

        val keyPanel = JPanel(BorderLayout(6, 0))
        keyPanel.add(apiKeyField, BorderLayout.CENTER)
        showKeyCheck.addActionListener {
            apiKeyField.echoChar = if (showKeyCheck.isSelected) 0.toChar() else ECHO_CHAR
        }
        keyPanel.add(showKeyCheck, BorderLayout.EAST)
        Theme.addRow(form, "settings.llm.apiKey", keyPanel, "settings.llm.apiKey.tooltip")

        val modelPanel = JPanel(BorderLayout(6, 0))
        modelPanel.add(modelCombo, BorderLayout.CENTER)
        modelPanel.add(loadModelsButton, BorderLayout.EAST)
        Theme.addRow(form, "settings.llm.model", modelPanel, "settings.llm.model.tooltip")

        Theme.addRow(form, "settings.llm.temperature", temperatureSpinner, "settings.llm.temperature.tooltip")
        Theme.addRow(form, "settings.llm.maxTokens", maxTokensSpinner, "settings.llm.maxTokens.tooltip")
        Theme.addRow(form, "settings.llm.timeout", timeoutSpinner, "settings.llm.timeout.tooltip")
        Theme.addRow(form, "settings.llm.contextLimit", contextLimitSpinner, "settings.llm.contextLimit.tooltip")
        Theme.addRow(form, "settings.llm.check", checkButton, "settings.llm.check.tooltip")

        val statusPanel = JPanel(FlowLayout(FlowLayout.LEFT, 6, 0))
        statusPanel.add(statusLabel)
        form.add(statusPanel)

        loadModelsButton.addActionListener { loadModels(showResult = false, force = true) }
        checkButton.addActionListener { loadModels(showResult = true, force = true) }
        return form
    }

    /** Fills the model combo with a downloaded or cached list. */
    private fun applyModels(models: List<String>) {
        val previous = modelCombo.selectedItem?.toString() ?: current.llm.model
        modelCombo.removeAllItems()
        models.forEach { modelCombo.addItem(it) }
        modelCombo.isEnabled = models.isNotEmpty()
        if (previous.isNotBlank() && previous in models) modelCombo.selectedItem = previous
    }

    /** Builds the settings snapshot currently shown in the widgets. */
    private fun currentFormLlm(): LlmSettings = current.llm.copy(
        provider = providerCombo.selectedItem as? LlmProviderType ?: LlmProviderType.OPENAI,
        baseUrl = baseUrlField.text.trim(),
        apiKey = String(apiKeyField.password),
        model = modelCombo.selectedItem?.toString() ?: "",
        temperature = (temperatureSpinner.value as Number).toDouble(),
        maxTokens = (maxTokensSpinner.value as Number).toInt(),
        timeoutSeconds = (timeoutSpinner.value as Number).toInt(),
        contextTokenLimit = (contextLimitSpinner.value as Number).toInt(),
    )

    /**
     * Loads the model list from the provider in the background (LLM-03,
     * LLM-05). On success the model combo becomes usable and the list is
     * cached for the session; on failure the combo stays disabled and the
     * reason is shown (LLM-04).
     */
    private fun loadModels(showResult: Boolean, force: Boolean) {
        val llm = currentFormLlm()
        if (!force) {
            val cached = ModelListCache.get(llm.provider.wireName, llm.effectiveBaseUrl(), llm.apiKey, llm.model)
            if (cached != null) {
                applyModels(cached)
                statusLabel.text = String.format(I18n.t("settings.llm.modelsLoaded"), cached.size)
                return
            }
        }
        val service = LlmService(llm, language)
        setBusy(true)
        statusLabel.text = I18n.t("settings.llm.loading")
        object : SwingWorker<List<String>, Void>() {
            override fun doInBackground(): List<String> = service.listModels()

            override fun done() {
                setBusy(false)
                try {
                    val models = get()
                    applyModels(models)
                    ModelListCache.put(llm.provider.wireName, llm.effectiveBaseUrl(), llm.apiKey, llm.model, models)
                    statusLabel.foreground = Theme.HINT_COLOR
                    val messageKey = if (showResult) "settings.llm.connectionOk" else "settings.llm.modelsLoaded"
                    statusLabel.text = String.format(I18n.t(messageKey), models.size)
                } catch (e: Exception) {
                    LlmErrors.log("Model list loading failed", e)
                    statusLabel.foreground = Theme.ERROR_COLOR
                    statusLabel.text = LlmErrors.message(e)
                }
            }
        }.execute()
    }

    private fun setBusy(busy: Boolean) {
        loadModelsButton.isEnabled = !busy
        checkButton.isEnabled = !busy
    }

    private fun confirm() {
        val general = current.copy(
            language = languageCombo.selectedItem?.toString() ?: AppSettings.LANGUAGE_SYSTEM,
            zoomScale = (zoomCombo.selectedItem as? Double) ?: 1.0,
            autosaveIntervalMinutes = (autosaveSpinner.value as Number).toInt(),
            backupCount = (backupSpinner.value as Number).toInt(),
            cardMode = cardModeCombo.selectedItem?.toString() ?: AppSettings.CARD_MODE_HUMAN,
            projectsDir = projectsDirField.text.trim(),
            llm = currentFormLlm(),
        )
        result = general
        dispose()
    }

    companion object {
        private const val ECHO_CHAR = '\u2022'
        private const val LANGUAGE_SYSTEM = "system"
        private val ZOOM_PRESETS = arrayOf(0.75, 1.0, 1.25, 1.5, 2.0, 3.0)
    }
}