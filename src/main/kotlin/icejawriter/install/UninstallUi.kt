package icejawriter.install

import icejawriter.I18n
import java.awt.Window
import java.nio.file.Path
import javax.swing.JOptionPane

/**
 * Uninstall flow used both by the --uninstall mode and by the
 * "Помощь → Удалить программу" menu item (УСТ-03). Confirms the removal
 * (УСТ-30), warns when project files (*.ijw) are inside the install directory
 * and require explicit confirmation (УСТ-31), then deletes the whole tree
 * (УСТ-32). Returns true when the directory was actually removed.
 */
object UninstallUi {

    private const val PROJECT_LIST_LIMIT = 20

    fun run(owner: Window?, installDirectory: Path): Boolean {
        val confirmation = JOptionPane.showConfirmDialog(
            owner,
            String.format(I18n.t("uninstall.confirm.text"), installDirectory),
            I18n.t("uninstall.title"),
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE,
        )
        if (confirmation != JOptionPane.YES_OPTION) return false

        val projectFiles = Uninstaller.findProjectFiles(installDirectory)
        if (projectFiles.isNotEmpty()) {
            val visibleProjects = projectFiles.take(PROJECT_LIST_LIMIT)
            val listText = buildString {
                visibleProjects.forEach { path -> append(path.fileName).append('\n') }
                if (projectFiles.size > PROJECT_LIST_LIMIT) append("…")
            }
            val projectConfirmation = JOptionPane.showConfirmDialog(
                owner,
                String.format(I18n.t("uninstall.projects.text"), projectFiles.size, listText),
                I18n.t("uninstall.projects.title"),
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE,
            )
            if (projectConfirmation != JOptionPane.YES_OPTION) return false
        }

        val outcome = Uninstaller.deleteRecursively(installDirectory)
        showOutcome(owner, outcome)
        return outcome.success
    }

    private fun showOutcome(owner: Window?, outcome: UninstallResult) {
        if (outcome.success || outcome.remaining.isEmpty()) {
            JOptionPane.showMessageDialog(
                owner,
                I18n.t("uninstall.success"),
                I18n.t("uninstall.title"),
                JOptionPane.INFORMATION_MESSAGE,
            )
        } else {
            JOptionPane.showMessageDialog(
                owner,
                String.format(I18n.t("uninstall.failed"), outcome.remaining.first()),
                I18n.t("uninstall.title"),
                JOptionPane.ERROR_MESSAGE,
            )
        }
    }
}