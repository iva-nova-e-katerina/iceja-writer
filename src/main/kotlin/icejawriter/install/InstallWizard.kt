package icejawriter.install

import icejawriter.I18n
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Dialog.ModalityType
import java.awt.FlowLayout
import java.awt.Font
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JDialog
import javax.swing.JFileChooser
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JRadioButton
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.JTextField
import javax.swing.SwingWorker
import javax.swing.WindowConstants
import javax.swing.border.EmptyBorder

/**
 * Portable install wizard (УСТ-20..УСТ-24, four steps). Step 1 welcomes the
 * user and shows the MIT license; step 2 chooses the install directory with
 * system directories rejected; step 3 runs the installation with a live
 * output log; step 4 offers "Run IceJA Writer" and, on Linux only, an
 * opt-in desktop .desktop shortcut (created only on explicit consent, УСТ-24).
 * The wizard language follows the OS locale and can be switched to Russian or
 * English at any time (УСТ-20).
 */
class InstallWizard(owner: JFrame?) : JDialog(owner, ModalityType.APPLICATION_MODAL) {

    companion object {
        private const val CARD_WELCOME = "welcome"
        private const val CARD_DIRECTORY = "directory"
        private const val CARD_INSTALL = "install"
        private const val CARD_FINISH = "finish"

        private const val MIT_LICENSE_RESOURCE = "/licenses/MIT.txt"
    }

    private val cardsPanel = JPanel(CardLayout())
    private var currentCardName = CARD_WELCOME

    private val langGroup = ButtonGroup()
    private val langRuButton = JRadioButton()
    private val langEnButton = JRadioButton()

    private val welcomeTitleLabel = JLabel()
    private val welcomeDescriptionArea = JTextArea()
    private val licenseTitleLabel = JLabel()
    private val licensePane = JTextArea()

    private val directoryPromptLabel = JLabel()
    private val directoryField = JTextField()
    private val browseButton = JButton()
    private val directoryHintLabel = JLabel()

    private val installStatusLabel = JLabel()
    private val installOutputPane = JTextArea()

    private val finishTextLabel = JLabel()
    private val finishDirectoryLabel = JLabel()
    private val desktopShortcutCheckbox = JCheckBox()

    private val backButton = JButton()
    private val nextButton = JButton()
    private val cancelButton = JButton()
    private val launchButton = JButton()
    private val closeButton = JButton()

    private var targetDirectory: Path = InstallPaths.defaultInstallDirectory()
    private var installRunning = false

    init {
        title = I18n.t("install.window.title")
        defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
        setSize(700, 560)
        setLocationRelativeTo(owner)

        buildContent()
        applyTexts()

        // Wizard language follows the OS locale with an in-dialog override.
        val isRussian = I18n.locale.language == "ru"
        langRuButton.isSelected = isRussian
        langEnButton.isSelected = !isRussian

        showCard(CARD_WELCOME)
    }

    private fun buildContent() {
        contentPane.layout = BorderLayout()
        contentPane.add(buildHeader(), BorderLayout.NORTH)
        contentPane.add(buildCards(), BorderLayout.CENTER)
        contentPane.add(buildButtonBar(), BorderLayout.SOUTH)
    }

    private fun buildHeader(): JPanel {
        val header = JPanel(BorderLayout())
        header.border = EmptyBorder(10, 16, 0, 16)

        val languagePanel = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0))
        langGroup.add(langRuButton)
        langGroup.add(langEnButton)
        langRuButton.addActionListener { switchLanguage(Locale("ru")) }
        langEnButton.addActionListener { switchLanguage(Locale.ENGLISH) }
        languagePanel.add(langRuButton)
        languagePanel.add(langEnButton)

        header.add(languagePanel, BorderLayout.LINE_END)
        return header
    }

    private fun buildCards(): JPanel {
        cardsPanel.add(buildWelcomeCard(), CARD_WELCOME)
        cardsPanel.add(buildDirectoryCard(), CARD_DIRECTORY)
        cardsPanel.add(buildInstallCard(), CARD_INSTALL)
        cardsPanel.add(buildFinishCard(), CARD_FINISH)
        return cardsPanel
    }

    private fun buildWelcomeCard(): JPanel {
        val card = JPanel(BorderLayout())
        card.border = EmptyBorder(4, 16, 8, 16)

        val content = JPanel()
        content.layout = BoxLayout(content, BoxLayout.Y_AXIS)

        welcomeTitleLabel.font = welcomeTitleLabel.font.deriveFont(Font.BOLD, 18f)
        welcomeDescriptionArea.lineWrap = true
        welcomeDescriptionArea.wrapStyleWord = true
        welcomeDescriptionArea.isEditable = false
        welcomeDescriptionArea.isOpaque = false
        welcomeDescriptionArea.border = EmptyBorder(10, 0, 10, 0)

        licensePane.isEditable = false
        licensePane.lineWrap = true
        licensePane.wrapStyleWord = true
        licensePane.font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
        licensePane.text = loadLicenseText()
        val licenseScroll = JScrollPane(licensePane)
        licenseScroll.preferredSize = java.awt.Dimension(1, 190)
        licenseScroll.border = BorderFactory.createTitledBorder("")

        listOf(welcomeTitleLabel, welcomeDescriptionArea, licenseTitleLabel, licenseScroll).forEach {
            it.alignmentX = Component.LEFT_ALIGNMENT
            content.add(it)
        }
        content.add(Box.createVerticalGlue())

        card.add(content, BorderLayout.CENTER)
        return card
    }

    private fun buildDirectoryCard(): JPanel {
        val card = JPanel(GridBagLayout())
        card.border = EmptyBorder(4, 16, 8, 16)

        val constraints = GridBagConstraints()
        constraints.insets = Insets(6, 6, 6, 6)
        constraints.anchor = GridBagConstraints.LINE_START
        constraints.fill = GridBagConstraints.HORIZONTAL

        constraints.gridx = 0
        constraints.gridy = 0
        constraints.gridwidth = 2
        constraints.weightx = 1.0
        card.add(directoryPromptLabel, constraints)

        constraints.gridy = 1
        constraints.gridwidth = 1
        constraints.weightx = 1.0
        card.add(directoryField, constraints)

        constraints.gridx = 1
        constraints.weightx = 0.0
        card.add(browseButton, constraints)

        constraints.gridx = 0
        constraints.gridy = 2
        constraints.gridwidth = 2
        card.add(directoryHintLabel, constraints)

        browseButton.addActionListener { browseForDirectory() }
        return card
    }

    private fun buildInstallCard(): JPanel {
        val card = JPanel(BorderLayout())
        card.border = EmptyBorder(4, 16, 8, 16)

        installOutputPane.isEditable = false
        installOutputPane.font = Font(Font.MONOSPACED, Font.PLAIN, 12)
        installOutputPane.lineWrap = false

        card.add(installStatusLabel, BorderLayout.PAGE_START)
        card.add(JScrollPane(installOutputPane), BorderLayout.CENTER)
        return card
    }

    private fun buildFinishCard(): JPanel {
        val card = JPanel()
        card.layout = BoxLayout(card, BoxLayout.Y_AXIS)
        card.border = EmptyBorder(4, 16, 8, 16)

        desktopShortcutCheckbox.isVisible = isLinux()
        finishTextLabel.font = finishTextLabel.font.deriveFont(Font.BOLD, 16f)

        listOf(finishTextLabel, finishDirectoryLabel, desktopShortcutCheckbox).forEach {
            it.alignmentX = Component.LEFT_ALIGNMENT
            card.add(it)
            card.add(Box.createVerticalStrut(10))
        }
        card.add(Box.createVerticalGlue())
        return card
    }

    private fun buildButtonBar(): JPanel {
        val bar = JPanel(FlowLayout(FlowLayout.RIGHT, 8, 8))
        bar.add(backButton)
        bar.add(nextButton)
        bar.add(cancelButton)
        bar.add(launchButton)
        bar.add(closeButton)

        backButton.addActionListener { showCard(currentPreviousCard()) }
        nextButton.addActionListener { onNext() }
        cancelButton.addActionListener { dispose() }
        launchButton.addActionListener { onFinish(launchAfterInstall = true) }
        closeButton.addActionListener { onFinish(launchAfterInstall = false) }
        return bar
    }

    /** Returns the card that "Back" should return to from the current one. */
    private fun currentPreviousCard(): String = when (currentCardName) {
        CARD_DIRECTORY -> CARD_WELCOME
        CARD_INSTALL -> CARD_DIRECTORY
        else -> CARD_WELCOME
    }

    private fun showCard(cardName: String) {
        currentCardName = cardName
        (cardsPanel.layout as CardLayout).show(cardsPanel, cardName)
        refreshButtons()
    }

    /**
     * Recomputes the visible buttons and the current step button label.
     * During an install run all navigation is disabled.
     */
    private fun refreshButtons() {
        val running = installRunning
        backButton.isVisible = currentCardName == CARD_DIRECTORY || currentCardName == CARD_INSTALL
        nextButton.isVisible = currentCardName != CARD_FINISH
        cancelButton.isVisible = currentCardName != CARD_FINISH
        launchButton.isVisible = currentCardName == CARD_FINISH
        closeButton.isVisible = currentCardName == CARD_FINISH

        nextButton.text = if (currentCardName == CARD_INSTALL) I18n.t("install.step.install") else I18n.t("install.step.next")

        backButton.isEnabled = !running
        nextButton.isEnabled = !running
        cancelButton.isEnabled = !running
    }

    private fun onNext() = when (currentCardName) {
        CARD_WELCOME -> showCard(CARD_DIRECTORY)
        CARD_DIRECTORY -> if (validateDirectory()) showCard(CARD_INSTALL) else Unit
        CARD_INSTALL -> startInstall()
        else -> Unit
    }

    private fun browseForDirectory() {
        val chooser = JFileChooser()
        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
        chooser.dialogTitle = I18n.t("install.directory.browse.title")
        chooser.selectedFile = targetDirectory.toFile()
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            targetDirectory = chooser.selectedFile.toPath().toAbsolutePath().normalize()
            directoryField.text = targetDirectory.toString()
        }
    }

    /** Checks the chosen directory: non-blank and not a system directory (УСТ-21). */
    private fun validateDirectory(): Boolean {
        val text = directoryField.text.trim()
        if (text.isBlank()) {
            showError(I18n.t("install.error.emptyDirectory"))
            return false
        }
        val candidate = Paths.get(text).toAbsolutePath().normalize()
        if (InstallPaths.isBlockedSystemDirectory(candidate)) {
            showError(I18n.t("install.error.systemDirectory"))
            return false
        }
        targetDirectory = candidate
        return true
    }

    /** Runs the installation in a background worker and reports the outcome. */
    private fun startInstall() {
        val sourceJar = InstallPaths.runningJarPath()
        if (sourceJar == null || !Files.isRegularFile(sourceJar)) {
            showError(I18n.t("install.jar.missing"))
            return
        }

        installRunning = true
        installStatusLabel.text = I18n.t("install.going.text")
        installOutputPane.text = ""
        refreshButtons()

        val directory = targetDirectory
        val jar = sourceJar
        object : SwingWorker<InstallResult, Unit>() {
            override fun doInBackground(): InstallResult =
                Installer(jar, directory, OsKind.detect()).install()

            override fun done() {
                installRunning = false
                val result = try {
                    get()
                } catch (exception: Exception) {
                    InstallResult(success = false, logLines = emptyList(), errorMessage = exception.message.orEmpty())
                }
                installOutputPane.append(result.logLines.joinToString("\n"))
                installOutputPane.append(System.lineSeparator())
                if (result.success) {
                    installStatusLabel.text = " "
                    showCard(CARD_FINISH)
                } else {
                    installStatusLabel.text = I18n.t("install.error.title")
                    refreshButtons()
                    showError(String.format(I18n.t("install.error.start"), result.errorMessage))
                }
            }
        }.execute()
    }

    private fun onFinish(launchAfterInstall: Boolean) {
        createShortcutIfRequested()
        if (launchAfterInstall) {
            runCatching { AppLauncher.launchInstalledCopy(targetDirectory) }
                .onFailure { showError(I18n.t("install.launch.failed")) }
        }
        dispose()
    }

    /**
     * Creates the Linux desktop shortcut only when the user explicitly opted
     * in (УСТ-24). Failures are silent because the shortcut is optional.
     */
    private fun createShortcutIfRequested() {
        if (!desktopShortcutCheckbox.isSelected) return
        runCatching { DesktopShortcut.createOnDesktop(targetDirectory, OsKind.detect()) }
    }

    private fun switchLanguage(locale: Locale) {
        I18n.setLocale(locale)
        applyTexts()
    }

    private fun applyTexts() {
        title = I18n.t("install.window.title")
        langRuButton.text = I18n.t("lang.ru")
        langEnButton.text = I18n.t("lang.en")

        welcomeTitleLabel.text = I18n.t("install.welcome.title")
        welcomeDescriptionArea.text = I18n.t("install.welcome.description")
        licenseTitleLabel.text = "${I18n.t("install.license.title")} — ${I18n.t("install.license.notice")}"

        directoryPromptLabel.text = I18n.t("install.directory.prompt")
        directoryField.text = targetDirectory.toString()
        browseButton.text = I18n.t("install.directory.browse")
        directoryHintLabel.text = I18n.t("install.directory.hint")

        installStatusLabel.text = if (installRunning) I18n.t("install.going.text") else " "

        finishTextLabel.text = I18n.t("install.done.text")
        finishDirectoryLabel.text = targetDirectory.toString()
        desktopShortcutCheckbox.text = I18n.t("install.desktop.checkbox")

        backButton.text = I18n.t("install.step.back")
        cancelButton.text = I18n.t("install.step.cancel")
        launchButton.text = I18n.t("install.step.launch")
        closeButton.text = I18n.t("install.step.close")
        refreshButtons()
    }

    private fun showError(message: String) {
        JOptionPane.showMessageDialog(
            this,
            message,
            I18n.t("install.error.title"),
            JOptionPane.ERROR_MESSAGE,
        )
    }

    private fun loadLicenseText(): String {
        val licenseStream = javaClass.getResourceAsStream(MIT_LICENSE_RESOURCE)
        if (licenseStream == null) return I18n.t("install.license.missing")
        return licenseStream.bufferedReader(UTF_8).use { reader -> reader.readText() }
    }

    private fun isLinux(): Boolean = OsKind.detect() == OsKind.LINUX
}