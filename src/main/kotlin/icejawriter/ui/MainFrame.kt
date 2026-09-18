package icejawriter.ui

import icejawriter.I18n
import icejawriter.Zoom
import icejawriter.app.AppInfo
import icejawriter.app.ProjectSession
import icejawriter.config.AppSettings
import icejawriter.config.SettingsStore
import icejawriter.export.DocxExporter
import icejawriter.export.ExportLabels
import icejawriter.export.ExportOptions
import icejawriter.install.InstallPaths
import icejawriter.install.UninstallUi
import icejawriter.install.Uninstaller
import icejawriter.llm.LlmException
import icejawriter.llm.LlmService
import icejawriter.llm.SceneCardProposal
import icejawriter.log.AppLog
import icejawriter.model.NovelDescription
import icejawriter.model.ProjectDocument
import icejawriter.model.SceneStructure
import icejawriter.project.ProjectFormat
import icejawriter.project.ProjectIo
import icejawriter.project.ProjectLockedException
import icejawriter.project.ProjectWriteException
import icejawriter.project.ValidationReport
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Dimension
import java.awt.Font
import java.awt.Insets
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Locale
import java.util.concurrent.CancellationException
import javax.swing.AbstractAction
import javax.swing.AbstractButton
import javax.swing.ButtonGroup
import javax.swing.Box
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JMenu
import javax.swing.JMenuBar
import javax.swing.JMenuItem
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JRadioButtonMenuItem
import javax.swing.JScrollPane
import javax.swing.JSplitPane
import javax.swing.JTextArea
import javax.swing.JToggleButton
import javax.swing.JToolBar
import javax.swing.KeyStroke
import javax.swing.SwingUtilities
import javax.swing.SwingWorker
import javax.swing.Timer
import javax.swing.filechooser.FileNameExtensionFilter

/**
 * Main window of IceJA Writer (ИНТ-01): the project tree on the left, the
 * editor area on the right, the menu bar on top and the status bar at the
 * bottom. Implements [EditorContext] so that the editor panels can report
 * edits and request navigation without depending on the frame itself.
 */
class MainFrame : JFrame(), EditorContext {

    override val session = ProjectSession()

    private val settingsStore = SettingsStore()

    override var settings: AppSettings = settingsStore.load()

    /** Shared scene CRUD used by the toolbar and the tree context menu. */
    private val commands = SceneCommands(this)

    // --- toolbar (ИНТ-02: all controls in one place) ---------------------
    private val toolbar = JToolBar()
    private val toolbarNewButton = JButton()
    private val toolbarOpenButton = JButton()
    private val toolbarSaveButton = JButton()
    private val toolbarStartButton = JButton()
    private val toolbarAddSceneButton = JButton()
    private val toolbarAddChapterButton = JButton()
    private val toolbarAddActButton = JButton()
    private val toolbarDuplicateButton = JButton()
    private val toolbarDeleteButton = JButton()
    private val humanModeButton = JToggleButton()
    private val aiModeButton = JToggleButton()
    private val cardModeGroup = ButtonGroup()
    private val toolbarCardButton = JButton()
    private val toolbarStopButton = JButton()
    private val toolbarDraftButton = JButton()
    private val toolbarChecksButton = JButton()
    private val toolbarSearchButton = JButton()
    private val toolbarExportButton = JButton()
    private val toolbarSettingsButton = JButton()
    private val emptyHintLabel = JLabel()
    private val emptyStartButton = JButton()

    // --- editor panels --------------------------------------------------
    private val treePanel = ProjectTree(this) { node -> onTreeSelected(node) }
    private val novelPanel = NovelPanel(this)
    private val worldPanel = BibleTextPanel(this, BibleSection.WORLD)
    private val rulesPanel = BibleTextPanel(this, BibleSection.RULES)
    private val stylePanel = BibleTextPanel(this, BibleSection.STYLE)
    private val charactersPanel = CharactersPanel(this)
    private val chronologyPanel = ChronologyPanel(this)
    private val storylinesPanel = StorylinesPanel(this)
    private val glossaryPanel = GlossaryPanel(this)
    private val scenePanel = ScenePanel(this)
    private val draftPanel = DraftPanel(this)
    private val searchPanel = SearchPanel(this)
    private val statisticsPanel = StatisticsPanel(this)

    private val editorCards = JPanel(CardLayout())
    private val editors: List<ProjectEditor> = listOf(
        novelPanel, worldPanel, rulesPanel, stylePanel, charactersPanel,
        chronologyPanel, storylinesPanel, glossaryPanel, scenePanel,
        searchPanel, statisticsPanel,
    )

    // --- menu items -----------------------------------------------------
    private val fileMenu = JMenu()
    private val newItem = JMenuItem()
    private val openItem = JMenuItem()
    private val recentMenu = JMenu()
    private val saveItem = JMenuItem()
    private val saveAsItem = JMenuItem()
    private val exportItem = JMenuItem()
    private val settingsItem = JMenuItem()
    private val closeProjectItem = JMenuItem()
    private val exitItem = JMenuItem()

    private val editMenu = JMenu()
    private val undoItem = JMenuItem()
    private val redoItem = JMenuItem()
    private val findItem = JMenuItem()

    private val viewMenu = JMenu()
    private val langMenu = JMenu()
    private val langSystemItem = JRadioButtonMenuItem()
    private val langRuItem = JRadioButtonMenuItem()
    private val langEnItem = JRadioButtonMenuItem()
    private val langGroup = ButtonGroup()
    private val zoomMenu = JMenu()
    private val zoomGroup = ButtonGroup()
    private val zoomItems = linkedMapOf<Double, JRadioButtonMenuItem>()

    private val helpMenu = JMenu()
    private val searchItem = JMenuItem()
    private val statsItem = JMenuItem()
    private val checksItem = JMenuItem()
    private val manualItem = JMenuItem()
    private val aboutItem = JMenuItem()
    private val uninstallItem = JMenuItem()

    // --- status bar -----------------------------------------------------
    private val wordsStatus = JLabel()
    private val saveStatus = JLabel()
    private val zoomStatus = JLabel()
    private val languageStatus = JLabel()
    private val llmStatus = JLabel()
    private val messageStatus = JLabel()

    private val findDialog = FindReplaceDialog(this, scenePanel.textComponent())
    private val autosaveTimer = Timer(60_000) { autosaveTick() }
    private var checksDialog: ChecksDialog? = null
    private var selectingFromCode = false
    private var currentSceneId: String? = null

    /** Continuous AI card generation state (until the author presses "Stop"). */
    private var cardChainActive = false
    private var cardRequestInFlight = false
    private var cardWorker: SwingWorker<SceneCardProposal, Void>? = null
    private var draftWorker: SwingWorker<String, Void>? = null

    init {
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(event: WindowEvent) = exitApplication()
        })

        buildMenuBar()
        buildContent()
        applySettings(settings)
        applyTexts()

        session.addListener {
            updateTitle()
            updateStatus()
        }
        refreshAll()
        setSize(1280, 840)
        minimumSize = Dimension(960, 640)
        setLocationRelativeTo(null)

        SwingUtilities.invokeLater {
            if (!session.isOpen()) showWelcomeMessage()
        }
    }

    // ------------------------------------------------------------------
    // layout
    // ------------------------------------------------------------------

    private fun buildMenuBar() {
        val menuBar = JMenuBar()
        menuBar.add(fileMenu)
        menuBar.add(editMenu)
        menuBar.add(viewMenu)
        menuBar.add(helpMenu)
        jMenuBar = menuBar

        fileMenu.add(newItem)
        fileMenu.add(openItem)
        fileMenu.add(recentMenu)
        fileMenu.addSeparator()
        fileMenu.add(saveItem)
        fileMenu.add(saveAsItem)
        fileMenu.add(closeProjectItem)
        fileMenu.addSeparator()
        fileMenu.add(exportItem)
        fileMenu.addSeparator()
        fileMenu.add(settingsItem)
        fileMenu.addSeparator()
        fileMenu.add(exitItem)

        editMenu.add(undoItem)
        editMenu.add(redoItem)
        editMenu.addSeparator()
        editMenu.add(findItem)
        editMenu.addSeparator()
        editMenu.add(searchItem)
        editMenu.add(statsItem)
        editMenu.add(checksItem)

        viewMenu.add(langMenu)
        langMenu.add(langSystemItem)
        langMenu.add(langRuItem)
        langMenu.add(langEnItem)
        langGroup.add(langSystemItem)
        langGroup.add(langRuItem)
        langGroup.add(langEnItem)

        viewMenu.add(zoomMenu)
        for (preset in ZOOM_PRESETS) {
            val item = JRadioButtonMenuItem(String.format("%.0f%%", preset * 100))
            item.addActionListener { changeZoom(preset) }
            zoomGroup.add(item)
            zoomMenu.add(item)
            zoomItems[preset] = item
        }

        helpMenu.add(manualItem)
        helpMenu.add(aboutItem)
        helpMenu.addSeparator()
        helpMenu.add(uninstallItem)

        newItem.addActionListener { newProject() }
        openItem.addActionListener { openProjectDialog() }
        saveItem.addActionListener { saveProject() }
        saveAsItem.addActionListener { saveProjectAs() }
        exportItem.addActionListener { exportDocx() }
        settingsItem.addActionListener { openSettings() }
        closeProjectItem.addActionListener { closeProject() }
        exitItem.addActionListener { exitApplication() }
        undoItem.addActionListener { scenePanel.undo() }
        redoItem.addActionListener { scenePanel.redo() }
        findItem.addActionListener { findDialog.open() }
        searchItem.addActionListener { showCard(CARD_SEARCH); searchPanel.runSearch() }
        statsItem.addActionListener { showStatistics() }
        checksItem.addActionListener { openChecks() }
        manualItem.addActionListener { ManualDialog(this).isVisible = true }
        aboutItem.addActionListener { showAbout() }
        uninstallItem.addActionListener { uninstallProgram() }

        langSystemItem.addActionListener { changeLanguage(AppSettings.LANGUAGE_SYSTEM) }
        langRuItem.addActionListener { changeLanguage(AppSettings.LANGUAGE_RU) }
        langEnItem.addActionListener { changeLanguage(AppSettings.LANGUAGE_EN) }

        installShortcuts()
    }

    private fun buildContent() {
        editorCards.add(EMPTY_PANEL, emptyPanel())
        editorCards.add(CARD_NOVEL, novelPanel)
        editorCards.add(CARD_WORLD, worldPanel)
        editorCards.add(CARD_RULES, rulesPanel)
        editorCards.add(CARD_STYLE, stylePanel)
        editorCards.add(CARD_CHARACTERS, charactersPanel)
        editorCards.add(CARD_CHRONOLOGY, chronologyPanel)
        editorCards.add(CARD_STORYLINES, storylinesPanel)
        editorCards.add(CARD_GLOSSARY, glossaryPanel)
        editorCards.add(CARD_SCENE, scenePanel)
        editorCards.add(CARD_SEARCH, searchPanel)
        editorCards.add(CARD_STATS, statisticsPanel)

        val split = JSplitPane(JSplitPane.HORIZONTAL_SPLIT, treePanel, editorCards)
        split.dividerLocation = 280
        split.resizeWeight = 0.22

        contentPane.layout = BorderLayout()
        contentPane.add(buildToolbar(), BorderLayout.NORTH)
        contentPane.add(split, BorderLayout.CENTER)
        contentPane.add(buildStatusBar(), BorderLayout.SOUTH)
    }

    /**
     * Builds the main toolbar: project actions, the prominent "Start writing"
     * button, scene CRUD, the card authoring mode switch and the LLM actions.
     * Everything the author needs is reachable in one click (ИНТ-30).
     */
    private fun buildToolbar(): JToolBar {
        toolbar.isFloatable = false
        toolbar.isRollover = true
        toolbar.border = javax.swing.BorderFactory.createEmptyBorder(2, 4, 2, 4)

        configureToolButton(toolbarNewButton) { newProject() }
        configureToolButton(toolbarOpenButton) { openProjectDialog() }
        configureToolButton(toolbarSaveButton) { saveProject() }
        toolbar.add(toolbarNewButton)
        toolbar.add(toolbarOpenButton)
        toolbar.add(toolbarSaveButton)
        toolbar.addSeparator()

        configureToolButton(toolbarStartButton, prominent = true) { startWriting() }
        toolbar.add(toolbarStartButton)
        toolbar.addSeparator()

        configureToolButton(toolbarAddSceneButton) { commands.addSceneAtEnd() }
        configureToolButton(toolbarAddChapterButton) { commands.addChapter(currentActOrLast()) }
        configureToolButton(toolbarAddActButton) { commands.addAct() }
        configureToolButton(toolbarDuplicateButton) { currentSceneId?.let { commands.duplicateScene(it) } }
        configureToolButton(toolbarDeleteButton) { currentSceneId?.let { commands.deleteScene(it, this) } }
        toolbar.add(toolbarAddSceneButton)
        toolbar.add(toolbarAddChapterButton)
        toolbar.add(toolbarAddActButton)
        toolbar.add(toolbarDuplicateButton)
        toolbar.add(toolbarDeleteButton)
        toolbar.addSeparator()

        // Card authoring mode: the author or the AI writes the scene cards.
        cardModeGroup.add(humanModeButton)
        cardModeGroup.add(aiModeButton)
        humanModeButton.addActionListener { setCardMode(AppSettings.CARD_MODE_HUMAN) }
        aiModeButton.addActionListener { setCardMode(AppSettings.CARD_MODE_AI) }
        toolbar.add(humanModeButton)
        toolbar.add(aiModeButton)
        toolbar.addSeparator()

        configureToolButton(toolbarCardButton) { startCardChain() }
        configureToolButton(toolbarStopButton) { stopCardChain(I18n.t("card.proposal.stopped")) }
        configureToolButton(toolbarDraftButton) {
            scenePanel.showDraftTab()
            draftPanel.requestDraft()
        }
        configureToolButton(toolbarChecksButton) { openChecks() }
        configureToolButton(toolbarSearchButton) {
            showCard(CARD_SEARCH)
            searchPanel.runSearch()
        }
        configureToolButton(toolbarExportButton) { exportDocx() }
        configureToolButton(toolbarSettingsButton) { openSettings() }
        toolbar.add(toolbarCardButton)
        toolbar.add(toolbarStopButton)
        toolbar.add(toolbarDraftButton)
        toolbar.add(toolbarChecksButton)
        toolbar.addSeparator()
        toolbar.add(toolbarSearchButton)
        toolbar.add(toolbarExportButton)
        toolbar.add(toolbarSettingsButton)
        return toolbar
    }

    /** Configures a toolbar button: flat, unfocusable, localized later. */
    private fun configureToolButton(button: AbstractButton, prominent: Boolean = false, action: () -> Unit) {
        button.isFocusable = false
        if (button is javax.swing.JButton) {
            button.margin = Insets(4, 10, 4, 10)
        }
        if (prominent) {
            button.font = button.font.deriveFont(Font.BOLD)
        }
        button.addActionListener { action() }
    }

    /** Applies localized texts and tooltips to every toolbar control (ИНТ-10). */
    private fun applyToolbarTexts() {
        toolbarNewButton.text = I18n.t("toolbar.new")
        toolbarNewButton.toolTipText = I18n.t("menu.file.new")
        toolbarOpenButton.text = I18n.t("toolbar.open")
        toolbarOpenButton.toolTipText = I18n.t("menu.file.open")
        toolbarSaveButton.text = I18n.t("toolbar.save")
        toolbarSaveButton.toolTipText = I18n.t("menu.file.save")
        toolbarStartButton.text = I18n.t("toolbar.start")
        toolbarStartButton.toolTipText = I18n.t("toolbar.start.tooltip")
        toolbarAddSceneButton.text = I18n.t("toolbar.addScene")
        toolbarAddSceneButton.toolTipText = I18n.t("tree.add.scene")
        toolbarAddChapterButton.text = I18n.t("toolbar.addChapter")
        toolbarAddChapterButton.toolTipText = I18n.t("tree.add.chapter")
        toolbarAddActButton.text = I18n.t("toolbar.addAct")
        toolbarAddActButton.toolTipText = I18n.t("tree.add.act")
        toolbarDuplicateButton.text = I18n.t("toolbar.duplicate")
        toolbarDuplicateButton.toolTipText = I18n.t("tree.duplicate.scene")
        toolbarDeleteButton.text = I18n.t("toolbar.delete")
        toolbarDeleteButton.toolTipText = I18n.t("tree.delete.scene")
        humanModeButton.text = I18n.t("toolbar.mode.human")
        humanModeButton.toolTipText = I18n.t("toolbar.mode.human.tooltip")
        aiModeButton.text = I18n.t("toolbar.mode.ai")
        aiModeButton.toolTipText = I18n.t("toolbar.mode.ai.tooltip")
        toolbarCardButton.text = I18n.t("toolbar.card")
        toolbarCardButton.toolTipText = I18n.t("toolbar.card.tooltip")
        toolbarStopButton.text = I18n.t("toolbar.stop")
        toolbarStopButton.toolTipText = I18n.t("toolbar.stop.tooltip")
        toolbarDraftButton.text = I18n.t("toolbar.draft")
        toolbarDraftButton.toolTipText = I18n.t("toolbar.draft.tooltip")
        toolbarChecksButton.text = I18n.t("toolbar.checks")
        toolbarChecksButton.toolTipText = I18n.t("menu.edit.checks")
        toolbarSearchButton.text = I18n.t("toolbar.search")
        toolbarSearchButton.toolTipText = I18n.t("menu.edit.search")
        toolbarExportButton.text = I18n.t("toolbar.export")
        toolbarExportButton.toolTipText = I18n.t("menu.file.export")
        toolbarSettingsButton.text = I18n.t("toolbar.settings")
        toolbarSettingsButton.toolTipText = I18n.t("menu.file.settings")
        emptyHintLabel.text = I18n.t("welcome.hint")
        emptyStartButton.text = I18n.t("toolbar.start")
        updateToolbarState()
    }

    /** Enables/disables toolbar controls according to the session state. */
    private fun updateToolbarState() {
        val open = session.isOpen()
        toolbarSaveButton.isEnabled = open
        toolbarAddSceneButton.isEnabled = open
        toolbarAddChapterButton.isEnabled = open
        toolbarAddActButton.isEnabled = open
        toolbarCardButton.isEnabled = open && !cardRequestInFlight
        toolbarStopButton.isEnabled = cardChainActive || cardRequestInFlight
        toolbarDraftButton.isEnabled = open
        toolbarChecksButton.isEnabled = open
        toolbarSearchButton.isEnabled = open
        toolbarExportButton.isEnabled = open
        val sceneSelected = currentSceneId != null
        toolbarDuplicateButton.isEnabled = sceneSelected
        toolbarDeleteButton.isEnabled = sceneSelected
        humanModeButton.isSelected = settings.cardMode == AppSettings.CARD_MODE_HUMAN
        aiModeButton.isSelected = settings.cardMode == AppSettings.CARD_MODE_AI
    }

    private fun buildStatusBar(): JPanel {
        val bar = JPanel(BorderLayout())
        bar.border = javax.swing.BorderFactory.createEmptyBorder(3, 8, 3, 8)

        val left = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 14, 0))
        left.add(wordsStatus)
        left.add(saveStatus)
        left.add(messageStatus)

        val right = JPanel(java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 14, 0))
        right.add(llmStatus)
        right.add(zoomStatus)
        right.add(languageStatus)

        bar.add(left, BorderLayout.WEST)
        bar.add(right, BorderLayout.EAST)
        return bar
    }

    private fun emptyPanel(): JPanel {
        val panel = JPanel(java.awt.GridBagLayout())
        val box = JPanel()
        box.layout = javax.swing.BoxLayout(box, javax.swing.BoxLayout.Y_AXIS)
        emptyHintLabel.alignmentX = JLabel.CENTER_ALIGNMENT
        emptyHintLabel.foreground = Theme.HINT_COLOR
        emptyStartButton.alignmentX = JLabel.CENTER_ALIGNMENT
        emptyStartButton.font = emptyStartButton.font.deriveFont(Font.BOLD, 18f)
        emptyStartButton.margin = Insets(10, 28, 10, 28)
        emptyStartButton.addActionListener { startWriting() }
        box.add(emptyHintLabel)
        box.add(Box.createVerticalStrut(16))
        box.add(emptyStartButton)
        panel.add(box)
        return panel
    }

    /** Installs the keyboard shortcuts of ИНТ-22/7.4. */
    private fun installShortcuts() {
        bind(KeyEvent.VK_N, KeyEvent.CTRL_DOWN_MASK) { newProject() }
        bind(KeyEvent.VK_O, KeyEvent.CTRL_DOWN_MASK) { openProjectDialog() }
        bind(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK) { saveProject() }
        bind(KeyEvent.VK_S, KeyEvent.CTRL_DOWN_MASK or KeyEvent.SHIFT_DOWN_MASK) { saveProjectAs() }
        bind(KeyEvent.VK_W, KeyEvent.CTRL_DOWN_MASK) { closeProject() }
        bind(KeyEvent.VK_F, KeyEvent.CTRL_DOWN_MASK) { findDialog.open() }
        bind(KeyEvent.VK_Z, KeyEvent.CTRL_DOWN_MASK) { scenePanel.undo() }
        bind(KeyEvent.VK_Y, KeyEvent.CTRL_DOWN_MASK) { scenePanel.redo() }
    }

    private fun bind(keyCode: Int, modifiers: Int, action: () -> Unit) {
        val keyStroke = KeyStroke.getKeyStroke(keyCode, modifiers)
        rootPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(keyStroke, keyStroke.toString())
        rootPane.actionMap.put(keyStroke.toString(), object : AbstractAction() {
            override fun actionPerformed(event: java.awt.event.ActionEvent) = action()
        })
    }

    // ------------------------------------------------------------------
    // EditorContext
    // ------------------------------------------------------------------

    override fun documentEdited() {
        session.markDirty()
        updateTitle()
        updateStatus()
    }

    override fun structureEdited() {
        session.markDirty()
        refreshTree()
        updateTitle()
        updateStatus()
    }

    override fun openScene(sceneId: String) {
        commitAll()
        // ФР-81: additionally autosave when the author switches scenes.
        maybeAutosaveOnSceneSwitch()
        currentSceneId = sceneId
        scenePanel.showScene(sceneId)
        draftPanel.showScene(sceneId)
        showCard(CARD_SCENE)
        selectingFromCode = true
        try {
            treePanel.selectScene(sceneId)
        } finally {
            selectingFromCode = false
        }
        updateStatus()
    }

    override fun reloadScene(sceneId: String) {
        currentSceneId = sceneId
        // loadScene (not showScene): the widgets must not commit stale values
        // over a document that was just changed externally.
        scenePanel.loadScene(sceneId)
        draftPanel.showScene(sceneId)
        showCard(CARD_SCENE)
        updateStatus()
    }

    override fun openBibleSection(key: String) {
        commitAll()
        when (key) {
            "world" -> showEditorCard(worldPanel, CARD_WORLD)
            "rules" -> showEditorCard(rulesPanel, CARD_RULES)
            "style" -> showEditorCard(stylePanel, CARD_STYLE)
            "characters" -> showEditorCard(charactersPanel, CARD_CHARACTERS)
            "chronology" -> showEditorCard(chronologyPanel, CARD_CHRONOLOGY)
            "storylines" -> showEditorCard(storylinesPanel, CARD_STORYLINES)
            "glossary" -> showEditorCard(glossaryPanel, CARD_GLOSSARY)
            else -> Unit
        }
        selectingFromCode = true
        try {
            treePanel.selectBibleSection(key)
        } finally {
            selectingFromCode = false
        }
    }

    override fun showStatus(message: String) {
        messageStatus.text = message
    }

    override fun characterChoices(): List<icejawriter.model.Character> =
        session.document?.bible?.characters ?: emptyList()

    override fun setLlmState(stateKey: String) {
        llmStatus.text = I18n.t(stateKey)
    }

    override fun showError(titleKey: String, message: String) {
        JOptionPane.showMessageDialog(this, message, I18n.t(titleKey), JOptionPane.ERROR_MESSAGE)
    }

    private fun showEditorCard(panel: ProjectEditor, card: String) {
        panel.refresh()
        showCard(card)
        updateStatus()
    }

    private fun showCard(card: String) {
        (editorCards.layout as CardLayout).show(editorCards, card)
    }

    private fun showStatistics() {
        commitAll()
        statisticsPanel.refresh()
        showCard(CARD_STATS)
        updateStatus()
    }

    /** Opens the continuity check dialog (LLM-40). */
    private fun openChecks() {
        if (!session.isOpen()) return
        commitAll()
        val dialog = checksDialog ?: ChecksDialog(this, this).also { checksDialog = it }
        dialog.fillScopes()
        dialog.isVisible = true
    }

    // ------------------------------------------------------------------
    // tree
    // ------------------------------------------------------------------

    private fun onTreeSelected(node: ProjectNode) {
        if (selectingFromCode) return
        when (node) {
            is ProjectNode.Description -> {
                commitAll()
                novelPanel.refresh()
                showCard(CARD_NOVEL)
            }
            is ProjectNode.Bible -> openBibleSection(node.key)
            is ProjectNode.SceneNode -> openScene(node.sceneId)
            else -> Unit
        }
        updateStatus()
    }

    // ------------------------------------------------------------------
    // project lifecycle (ФР-01..ФР-04)
    // ------------------------------------------------------------------

    private fun newProject(): Boolean {
        if (!confirmDiscardChanges()) return false
        val dialog = NewProjectDialog(this, settings)
        dialog.isVisible = true
        val request = dialog.result ?: return false
        return try {
            val document = ProjectDocument
                .newProject(request.title, AppInfo.VERSION)
                .copy(novel = NovelDescription(title = request.title, author = request.author))
            request.file.parent?.let { Files.createDirectories(it) }
            ProjectIo.write(request.file, document, settings.backupCount)
            session.open(request.file, document)
            rememberRecent(request.file)
            AppLog.info("Created project: " + request.file)
            refreshAll()
            true
        } catch (e: Exception) {
            AppLog.error("Could not create the project", e)
            showError("project.error.create", userMessage(e))
            false
        }
    }

    /**
     * The one-click "Start writing" flow: creates a project if none is open,
     * appends the next scene and opens it. In the "AI writes the cards" mode
     * the card proposal is generated immediately; in the human mode the empty
     * card is shown for the author to fill in.
     */
    private fun startWriting() {
        if (!session.isOpen() && !newProject()) return
        val document = session.document ?: return
        val target = if (document.scenes.isEmpty()) {
            commands.addScene(1, 1)
        } else {
            commands.addSceneAtEnd()
        }
        if (target == null) return
        openScene(target)
        scenePanel.showCardTab()
        if (settings.cardMode == AppSettings.CARD_MODE_AI) {
            startCardChain()
        } else {
            showStatus(I18n.t("toolbar.start.hintHuman"))
        }
    }

    /** Switches the card authoring mode and remembers it (ФР-101). */
    private fun setCardMode(mode: String) {
        if (settings.cardMode == mode) {
            updateToolbarState()
            return
        }
        settings = settings.copy(cardMode = mode)
        settingsStore.save(settings)
        applyToolbarTexts()
        showStatus(I18n.t(if (mode == AppSettings.CARD_MODE_AI) "toolbar.mode.ai.hint" else "toolbar.mode.human.hint"))
    }

    /**
     * Starts the fully automatic background generation: for the current scene
     * the app generates and applies the card, generates the scene text and
     * writes it in, then silently moves on to the next scene — without any
     * dialogs — until the author presses "Stop".
     */
    private fun startCardChain() {
        if (!session.isOpen() || cardChainActive) return
        val sceneId = currentSceneId
        if (sceneId == null) {
            showError("draft.error.title", I18n.t("card.proposal.noScene"))
            return
        }
        cardChainActive = true
        updateToolbarState()
        processSceneInChain(sceneId)
    }

    /** Stops the background generation and cancels the running requests. */
    private fun stopCardChain(message: String? = null) {
        val wasWorking = cardRequestInFlight
        cardChainActive = false
        cardRequestInFlight = false
        cardWorker?.cancel(true)
        cardWorker = null
        draftWorker?.cancel(true)
        draftWorker = null
        draftPanel.cancelGeneration()
        if (wasWorking) setLlmState("status.llm.cancelled")
        updateToolbarState()
        if (message != null) showStatus(message)
    }

    /**
     * One pipeline step for [sceneId]: generate and apply the card, then
     * generate and write the scene text, then create the next scene. Everything
     * runs in the background without dialogs.
     */
    private fun processSceneInChain(sceneId: String) {
        if (!cardChainActive) return
        val document = session.document ?: return
        val service = LlmService(settings.llm, languageCode())
        if (!service.isConfigured()) {
            showError("draft.error.title", I18n.t("draft.error.notConfigured"))
            stopCardChain()
            return
        }
        setLlmState("status.llm.working")
        showStatus(I18n.t("card.proposal.generating"))
        cardRequestInFlight = true
        updateToolbarState()
        val worker = object : SwingWorker<SceneCardProposal, Void>() {
            override fun doInBackground(): SceneCardProposal = service.generateCard(document, sceneId)

            override fun done() {
                cardRequestInFlight = false
                updateToolbarState()
                try {
                    val proposal = get()
                    if (!cardChainActive) return
                    val appliedSceneId = applyCardProposal(sceneId, proposal)
                    if (!cardChainActive) return
                    generateTextInChain(appliedSceneId, service)
                } catch (e: CancellationException) {
                    setLlmState("status.llm.cancelled")
                    stopCardChain(I18n.t("card.proposal.stopped"))
                } catch (e: Exception) {
                    setLlmState("status.llm.error")
                    LlmErrors.log("Scene card generation failed", e)
                    showError("draft.error.title", LlmErrors.message(e))
                    stopCardChain(I18n.t("card.proposal.stopped"))
                }
            }
        }
        cardWorker = worker
        worker.execute()
    }

    /** Generates the scene text in the background and writes it into the scene. */
    private fun generateTextInChain(sceneId: String, service: LlmService) {
        val document = session.document ?: return
        showStatus(I18n.t("draft.generating"))
        val worker = object : SwingWorker<String, Void>() {
            override fun doInBackground(): String = service.generateDraft(document, sceneId).draft

            override fun done() {
                try {
                    val text = get()
                    if (!cardChainActive) return
                    writeTextIntoScene(sceneId, text)
                    autosaveChainProgress()
                    continueCardChain()
                } catch (e: CancellationException) {
                    setLlmState("status.llm.cancelled")
                    stopCardChain(I18n.t("card.proposal.stopped"))
                } catch (e: Exception) {
                    setLlmState("status.llm.error")
                    LlmErrors.log("Scene text generation failed", e)
                    showError("draft.error.title", LlmErrors.message(e))
                    stopCardChain(I18n.t("card.proposal.stopped"))
                }
            }
        }
        draftWorker = worker
        worker.execute()
    }

    /** Writes the generated text into the scene and refreshes the editor. */
    private fun writeTextIntoScene(sceneId: String, text: String) {
        val number = session.document?.scenes?.firstOrNull { it.card.id == sceneId }?.card?.number
        session.update { document ->
            document.copy(scenes = document.scenes.map { scene ->
                if (scene.card.id == sceneId) scene.copy(text = text) else scene
            })
        }
        documentEdited()
        if (currentSceneId == sceneId) {
            // The author watches this scene: show the freshly written text.
            reloadScene(sceneId)
            scenePanel.showTextTab()
        }
        setLlmState("status.llm.done")
        showStatus(String.format(I18n.t("chain.sceneDone"), number ?: 0))
    }

    /** Saves the progress after each finished scene (if autosave is enabled). */
    private fun autosaveChainProgress() {
        if (settings.autosaveIntervalMinutes <= 0 || !session.isOpen() || !session.dirty) return
        if (novelPanel.isOverLimit()) return
        try {
            session.save(settings.backupCount)
            AppLog.info("Autosaved the chain progress: " + session.file)
        } catch (e: Exception) {
            AppLog.error("Chain autosave failed", e)
        }
    }

    /** Creates the next scene in the background without switching the view. */
    private fun continueCardChain() {
        if (!cardChainActive) return
        val document = session.document ?: return
        val last = SceneStructure.ordered(document.scenes).lastOrNull()
        val scene = SceneStructure.newScene(document.scenes, last?.card?.act ?: 1, last?.card?.chapter ?: 1)
        session.update { it.copy(scenes = SceneStructure.renumbered(it.scenes + scene)) }
        structureEdited()
        processSceneInChain(scene.card.id)
    }

    /**
     * Writes a generated proposal into the scene card and returns the scene id
     * after a possible renumbering. The editor is refreshed only when the
     * affected scene is the one currently shown; background scenes are updated
     * silently.
     */
    private fun applyCardProposal(sceneId: String, proposal: SceneCardProposal): String {
        val currentScene = session.document?.scenes?.firstOrNull { it.card.id == sceneId } ?: return sceneId
        // Flush uncommitted widget edits first; the proposal replaces the card.
        commitAll()

        val positionChanged = proposal.act != null && proposal.chapter != null &&
            (proposal.act != currentScene.card.act || proposal.chapter != currentScene.card.chapter)
        var appliedSceneId = sceneId
        session.update { document ->
            val updated = document.scenes.map { scene ->
                if (scene.card.id != sceneId) {
                    scene
                } else {
                    scene.copy(
                        card = scene.card.copy(
                            title = proposal.title.ifBlank { scene.card.title },
                            pov = proposal.pov,
                            location = proposal.location,
                            time = proposal.time,
                            goal = proposal.goal,
                            conflict = proposal.conflict,
                            twist = proposal.twist,
                            readerLearns = proposal.readerLearns,
                            charactersLearn = proposal.charactersLearn,
                            entry = proposal.entry,
                            exit = proposal.exit,
                            continuityNotes = proposal.continuityNotes,
                            summary = proposal.summary,
                            wordTargetMin = proposal.wordTargetMin,
                            wordTargetMax = proposal.wordTargetMax,
                            updatedAt = Instant.now().toString(),
                        ),
                    )
                }
            }
            if (positionChanged) {
                val result = SceneStructure.moveAndLocate(updated, sceneId, proposal.act!!, proposal.chapter!!)
                appliedSceneId = result.movedSceneId ?: sceneId
                document.copy(scenes = result.scenes)
            } else {
                document.copy(scenes = updated)
            }
        }

        val isCurrent = currentSceneId == appliedSceneId
        if (isCurrent) {
            // Reload the widgets BEFORE rebuilding the tree: the tree selection
            // event triggers a commit of the editor, and stale widget values
            // would overwrite the freshly applied proposal.
            reloadScene(appliedSceneId)
        }
        documentEdited()
        structureEdited()
        if (isCurrent) {
            selectingFromCode = true
            try {
                treePanel.selectScene(appliedSceneId)
            } finally {
                selectingFromCode = false
            }
        }
        showStatus(I18n.t("card.proposal.applied"))
        return appliedSceneId
    }

    /** Act of the selected scene, or of the last scene, or 1. */
    private fun currentActOrLast(): Int {
        val document = session.document ?: return 1
        val scene = currentSceneId?.let { id -> document.scenes.firstOrNull { it.card.id == id } }
        return scene?.card?.act ?: SceneStructure.ordered(document.scenes).lastOrNull()?.card?.act ?: 1
    }

    private fun openProjectDialog() {
        if (!confirmDiscardChanges()) return
        val chooser = JFileChooser(settings.effectiveProjectsDir())
        chooser.fileFilter = FileNameExtensionFilter(I18n.t("project.filter"), "ijw")
        chooser.dialogTitle = I18n.t("project.open.title")
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return
        openProject(chooser.selectedFile.toPath())
    }

    /** Opens the project; on a damaged archive offers recovery (ФРМ-22). */
    private fun openProject(file: Path) {
        commitAll()
        val result = ProjectIo.read(file)
        if (result.project == null) {
            AppLog.warn("Could not open the project: " + result.report.fatalMessage)
            offerRecovery(file, result.report.fatalMessage.orEmpty())
            return
        }
        session.open(file, result.project)
        rememberRecent(file)
        AppLog.info("Opened project: " + file)
        refreshAll()
        if (result.report.hasProblems) showValidationReport(result.report)
    }

    private fun offerRecovery(file: Path, fatalMessage: String) {
        val backup = newestReadableBackup(file)
        if (backup == null) {
            JOptionPane.showMessageDialog(
                this,
                String.format(I18n.t("project.corrupt.noBackup"), fatalMessage),
                I18n.t("project.corrupt.title"),
                JOptionPane.ERROR_MESSAGE,
            )
            return
        }
        val answer = JOptionPane.showConfirmDialog(
            this,
            String.format(I18n.t("project.corrupt.restore"), backup.toString()),
            I18n.t("project.corrupt.title"),
            JOptionPane.YES_NO_OPTION,
        )
        if (answer != JOptionPane.YES_OPTION) return
        try {
            Files.copy(backup, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            openProject(file)
        } catch (e: Exception) {
            AppLog.error("Could not restore the project from a backup", e)
            showError("project.corrupt.title", userMessage(e))
        }
    }

    private fun newestReadableBackup(file: Path): Path? {
        for (index in 1..ProjectFormat.MAX_BACKUP_COUNT) {
            val backup = ProjectFormat.backupFile(file, index)
            if (!Files.exists(backup)) continue
            if (ProjectIo.read(backup).project != null) return backup
        }
        return null
    }

    private fun saveProject(): Boolean {
        if (!session.isOpen()) return false
        commitAll()
        if (novelPanel.isOverLimit()) {
            showError(
                "project.save.tooLargeTitle",
                String.format(I18n.t("novel.overLimit"), novelPanel.byteSize(), ProjectFormat.NOVEL_DESCRIPTION_BYTES_LIMIT),
            )
            return false
        }
        return try {
            session.save(settings.backupCount)
            session.file?.let { rememberRecent(it) }
            AppLog.info("Saved project: " + session.file)
            updateTitle()
            updateStatus()
            true
        } catch (e: Exception) {
            AppLog.error("Could not save the project", e)
            showError("project.error.save", userMessage(e))
            false
        }
    }

    private fun saveProjectAs() {
        if (!session.isOpen()) return
        commitAll()
        if (novelPanel.isOverLimit()) {
            showError(
                "project.save.tooLargeTitle",
                String.format(I18n.t("novel.overLimit"), novelPanel.byteSize(), ProjectFormat.NOVEL_DESCRIPTION_BYTES_LIMIT),
            )
            return
        }
        val chooser = JFileChooser(session.file?.parent?.toString() ?: settings.effectiveProjectsDir())
        chooser.fileFilter = FileNameExtensionFilter(I18n.t("project.filter"), "ijw")
        chooser.dialogTitle = I18n.t("project.saveAs.title")
        chooser.selectedFile = java.io.File(session.file?.toString() ?: "novel.ijw")
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return
        var target = chooser.selectedFile.toPath()
        if (!target.fileName.toString().endsWith(ProjectFormat.FILE_EXTENSION)) {
            target = target.resolveSibling(target.fileName.toString() + ProjectFormat.FILE_EXTENSION)
        }
        try {
            session.saveAs(target, settings.backupCount)
            rememberRecent(target)
            AppLog.info("Saved project as: " + target)
            updateTitle()
            updateStatus()
        } catch (e: Exception) {
            AppLog.error("Could not save the project", e)
            showError("project.error.save", userMessage(e))
        }
    }

    private fun closeProject() {
        if (!confirmDiscardChanges()) return
        commitAll()
        session.close()
        currentSceneId = null
        refreshAll()
    }

    private fun exitApplication() {
        if (!confirmDiscardChanges()) return
        autosaveTimer.stop()
        session.close()
        dispose()
        System.exit(0)
    }

    /**
     * Asks about unsaved changes (ФР-04): Save / Don't save / Cancel. Returns
     * false when the user cancels or a save fails.
     */
    private fun confirmDiscardChanges(): Boolean {
        if (!session.isOpen() || !session.dirty) return true
        val options = arrayOf(I18n.t("project.unsaved.save"), I18n.t("project.unsaved.dontSave"), I18n.t("project.unsaved.cancel"))
        val answer = JOptionPane.showOptionDialog(
            this,
            I18n.t("project.unsaved.text"),
            I18n.t("project.unsaved.title"),
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.WARNING_MESSAGE,
            null,
            options,
            options[0],
        )
        return when (answer) {
            0 -> saveProject()
            1 -> true
            else -> false
        }
    }

    // ------------------------------------------------------------------
    // autosave (ФР-80, ФР-81)
    // ------------------------------------------------------------------

    private fun autosaveTick() {
        if (!session.isOpen() || !session.dirty) return
        commitAll()
        if (novelPanel.isOverLimit()) return
        try {
            session.save(settings.backupCount)
            AppLog.info("Autosaved project: " + session.file)
            showStatus(I18n.t("status.autosaved"))
        } catch (e: Exception) {
            AppLog.error("Autosave failed", e)
        }
    }

    /**
     * Autosaves when the author switches scenes (ФР-81) if autosave is enabled
     * and there are unsaved changes. Failures are logged but never interrupt
     * the author's work.
     */
    private fun maybeAutosaveOnSceneSwitch() {
        if (settings.autosaveIntervalMinutes <= 0) return
        if (!session.isOpen() || !session.dirty) return
        if (novelPanel.isOverLimit()) return
        try {
            session.save(settings.backupCount)
            AppLog.info("Autosaved on scene switch: " + session.file)
            showStatus(I18n.t("status.autosaved"))
        } catch (e: Exception) {
            AppLog.error("Autosave on scene switch failed", e)
        }
    }

    private fun restartAutosave() {
        autosaveTimer.stop()
        val minutes = settings.autosaveIntervalMinutes
        if (minutes > 0) {
            autosaveTimer.initialDelay = minutes * 60_000
            autosaveTimer.delay = minutes * 60_000
            autosaveTimer.start()
        }
    }

    // ------------------------------------------------------------------
    // settings, language, zoom
    // ------------------------------------------------------------------

    private fun openSettings() {
        val dialog = SettingsDialog(this, settings, languageCode())
        dialog.isVisible = true
        val updated = dialog.result ?: return
        val languageChanged = updated.language != settings.language
        val zoomChanged = updated.zoomScale != settings.zoomScale
        settings = updated
        settingsStore.save(settings)
        if (languageChanged) applyLanguage()
        if (zoomChanged) Zoom.apply(settings.zoomScale, this)
        restartAutosave()
        applyTexts()
        applyToolbarTexts()
        updateStatus()
    }

    private fun changeLanguage(language: String) {
        if (settings.language == language) return
        settings = settings.copy(language = language)
        settingsStore.save(settings)
        applyLanguage()
        applyTexts()
        refreshAll()
    }

    private fun changeZoom(scale: Double) {
        settings = settings.copy(zoomScale = scale)
        settingsStore.save(settings)
        Zoom.apply(scale, this)
        applyTexts()
    }

    /** Applies the configured interface language (ИНТ-11). */
    private fun applyLanguage() {
        val locale = when (settings.language) {
            AppSettings.LANGUAGE_RU -> Locale("ru")
            AppSettings.LANGUAGE_EN -> Locale.ENGLISH
            else -> Locale.getDefault()
        }
        I18n.setLocale(locale)
    }

    private fun applySettings(appSettings: AppSettings) {
        applyLanguage()
        Zoom.apply(appSettings.zoomScale, this)
        restartAutosave()
    }

    /** Re-translates every widget without losing the open document (ИНТ-10). */
    private fun applyTexts() {
        title = windowTitle()
        fileMenu.text = I18n.t("menu.file")
        newItem.text = I18n.t("menu.file.new")
        openItem.text = I18n.t("menu.file.open")
        recentMenu.text = I18n.t("menu.file.recent")
        saveItem.text = I18n.t("menu.file.save")
        saveAsItem.text = I18n.t("menu.file.saveAs")
        closeProjectItem.text = I18n.t("menu.file.close")
        exportItem.text = I18n.t("menu.file.export")
        settingsItem.text = I18n.t("menu.file.settings")
        exitItem.text = I18n.t("menu.exit")

        editMenu.text = I18n.t("menu.edit")
        undoItem.text = I18n.t("menu.edit.undo")
        redoItem.text = I18n.t("menu.edit.redo")
        findItem.text = I18n.t("menu.edit.find")
        searchItem.text = I18n.t("menu.edit.search")
        statsItem.text = I18n.t("menu.edit.stats")
        checksItem.text = I18n.t("menu.edit.checks")

        viewMenu.text = I18n.t("menu.view")
        langMenu.text = I18n.t("menu.lang")
        langSystemItem.text = I18n.t("lang.system")
        langRuItem.text = I18n.t("lang.ru")
        langEnItem.text = I18n.t("lang.en")
        zoomMenu.text = I18n.t("menu.zoom")

        helpMenu.text = I18n.t("menu.help")
        manualItem.text = I18n.t("menu.help.manual")
        aboutItem.text = I18n.t("menu.about")
        uninstallItem.text = I18n.t("uninstall.menu")

        langSystemItem.isSelected = settings.language == AppSettings.LANGUAGE_SYSTEM
        langRuItem.isSelected = settings.language == AppSettings.LANGUAGE_RU
        langEnItem.isSelected = settings.language == AppSettings.LANGUAGE_EN
        zoomItems[settings.zoomScale]?.isSelected = true

        saveItem.isEnabled = session.isOpen()
        saveAsItem.isEnabled = session.isOpen()
        closeProjectItem.isEnabled = session.isOpen()
        exportItem.isEnabled = session.isOpen()

        rebuildRecentMenu()
        treePanel.rebuild()
        applyToolbarTexts()
        updateStatus()
    }

    private fun rebuildRecentMenu() {
        recentMenu.removeAll()
        val recent = settings.recentProjects
        if (recent.isEmpty()) {
            val empty = JMenuItem(I18n.t("menu.file.recent.empty"))
            empty.isEnabled = false
            recentMenu.add(empty)
            return
        }
        recent.forEach { path ->
            val item = JMenuItem(path)
            item.addActionListener { openRecent(path) }
            recentMenu.add(item)
        }
    }

    private fun openRecent(path: String) {
        val file = Path.of(path)
        if (!Files.exists(file)) {
            showError("menu.file.recent", String.format(I18n.t("menu.file.recent.missing"), path))
            return
        }
        if (!confirmDiscardChanges()) return
        openProject(file)
    }

    private fun rememberRecent(file: Path) {
        settings = settings.withRecentProject(file.toString())
        settingsStore.save(settings)
        rebuildRecentMenu()
    }

    // ------------------------------------------------------------------
    // export (ФР-90..ФР-95)
    // ------------------------------------------------------------------

    private fun exportDocx() {
        val document = session.document ?: return
        commitAll()
        val dialog = ExportDialog(this, document, session.file?.parent ?: Path.of(settings.effectiveProjectsDir()))
        dialog.isVisible = true
        val request = dialog.result ?: return
        try {
            DocxExporter.export(
                project = document,
                target = request.file,
                options = request.options,
                labels = ExportLabels(
                    act = I18n.t("export.act"),
                    chapter = I18n.t("export.chapter"),
                ),
            )
            AppLog.info("Exported DOCX: " + request.file)
            showExportSuccess(request.file)
        } catch (e: Exception) {
            AppLog.error("DOCX export failed", e)
            showError("export.error", userMessage(e))
        }
    }

    private fun showExportSuccess(file: Path) {
        val openFolder = I18n.t("export.openFolder")
        val answer = JOptionPane.showOptionDialog(
            this,
            String.format(I18n.t("export.success"), file.toString()),
            I18n.t("export.title"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.INFORMATION_MESSAGE,
            null,
            arrayOf(openFolder, I18n.t("export.close")),
            openFolder,
        )
        if (answer == 0) {
            try {
                java.awt.Desktop.getDesktop().open(file.parent.toFile())
            } catch (e: Exception) {
                AppLog.warn("Could not open the export folder: " + e.message)
            }
        }
    }

    // ------------------------------------------------------------------
    // validation report (ФРМ-21)
    // ------------------------------------------------------------------

    private fun showValidationReport(report: ValidationReport) {
        val text = JTextArea()
        text.isEditable = false
        text.lineWrap = true
        text.wrapStyleWord = true
        val builder = StringBuilder()
        report.issues.forEach { issue ->
            builder.append(issue.file).append(": ").append(issue.problem).append('\n')
        }
        text.text = builder.toString()
        text.caretPosition = 0
        JOptionPane.showMessageDialog(
            this,
            JScrollPane(text).apply { preferredSize = Dimension(680, 320) },
            I18n.t("project.check.title"),
            JOptionPane.WARNING_MESSAGE,
        )
    }

    // ------------------------------------------------------------------
    // misc
    // ------------------------------------------------------------------

    private fun showAbout() {
        JOptionPane.showMessageDialog(
            this,
            String.format(I18n.t("dialog.about.text"), AppInfo.VERSION),
            I18n.t("dialog.about.title"),
            JOptionPane.INFORMATION_MESSAGE,
        )
    }

    private fun showWelcomeMessage() {
        showStatus(I18n.t("status.ready"))
    }

    private fun uninstallProgram() {
        val installDirectory = InstallPaths.runningJarPath()?.parent
        if (installDirectory == null || !Uninstaller.isInstalledDirectory(installDirectory)) {
            JOptionPane.showMessageDialog(
                this,
                I18n.t("uninstall.notFound"),
                I18n.t("uninstall.title"),
                JOptionPane.INFORMATION_MESSAGE,
            )
            return
        }
        if (UninstallUi.run(this, installDirectory)) {
            dispose()
            System.exit(0)
        }
    }

    private fun commitAll() {
        editors.forEach { it.commitToProject() }
    }

    /** Reloads every editor and the tree from the open document. */
    private fun refreshAll() {
        editors.forEach { it.refresh() }
        refreshTree()
        if (currentSceneId != null && session.document?.scenes?.none { it.card.id == currentSceneId } == true) {
            currentSceneId = null
        }
        currentSceneId?.let { scenePanel.showScene(it) }
        draftPanel.refresh()
        saveItem.isEnabled = session.isOpen()
        saveAsItem.isEnabled = session.isOpen()
        closeProjectItem.isEnabled = session.isOpen()
        exportItem.isEnabled = session.isOpen()
        updateTitle()
        updateStatus()
    }

    private fun refreshTree() {
        treePanel.rebuild()
    }

    private fun updateTitle() {
        title = windowTitle()
    }

    private fun windowTitle(): String {
        val document = session.document ?: return I18n.t("app.title")
        val dirtyMark = if (session.dirty) "*" else ""
        return I18n.t("app.title") + " — " + document.title + dirtyMark
    }

    private fun updateStatus() {
        val document = session.document
        val scene = currentSceneId?.let { id -> document?.scenes?.firstOrNull { it.card.id == id } }
        val words = scene?.let { SceneStructure.wordCount(it.text) }
            ?: document?.let { SceneStructure.totalWordCount(it.scenes) }
            ?: 0
        val characters = scene?.text?.length ?: document?.scenes?.sumOf { it.text.length } ?: 0
        wordsStatus.text = String.format(I18n.t("status.words"), words) + " | " +
            String.format(I18n.t("status.characters"), characters)
        saveStatus.text = when {
            !session.isOpen() -> I18n.t("status.noProject")
            session.dirty -> I18n.t("status.modified")
            else -> I18n.t("status.saved")
        }
        zoomStatus.text = String.format(I18n.t("status.zoom"), (settings.zoomScale * 100).toInt())
        languageStatus.text = when (settings.language) {
            AppSettings.LANGUAGE_RU -> I18n.t("lang.ru")
            AppSettings.LANGUAGE_EN -> I18n.t("lang.en")
            else -> I18n.t("lang.system")
        }
        if (llmStatus.text.isBlank()) llmStatus.text = I18n.t("status.llm.idle")
        updateToolbarState()
    }

    /** Localized message for a project write/read exception (ИНТ-31). */
    private fun userMessage(e: Exception): String = when (e) {
        is ProjectLockedException -> I18n.t("project.error.locked")
        is ProjectWriteException -> e.message ?: I18n.t("project.error.save")
        else -> e.message ?: e.javaClass.simpleName
    }

    private fun languageCode(): String = if (I18n.locale.language == "en") "en" else "ru"

    companion object {
        private const val EMPTY_PANEL = "empty"
        private const val CARD_NOVEL = "novel"
        private const val CARD_WORLD = "world"
        private const val CARD_RULES = "rules"
        private const val CARD_STYLE = "style"
        private const val CARD_CHARACTERS = "characters"
        private const val CARD_CHRONOLOGY = "chronology"
        private const val CARD_STORYLINES = "storylines"
        private const val CARD_GLOSSARY = "glossary"
        private const val CARD_SCENE = "scene"
        private const val CARD_SEARCH = "search"
        private const val CARD_STATS = "stats"

        private val ZOOM_PRESETS = listOf(0.75, 1.0, 1.25, 1.5, 2.0, 3.0)
    }
}