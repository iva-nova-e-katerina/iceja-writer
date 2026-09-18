package icejawriter

import icejawriter.config.AppPaths
import icejawriter.install.InstallPaths
import icejawriter.install.InstallWizard
import icejawriter.install.JavaVersionChecker
import icejawriter.install.UninstallUi
import icejawriter.install.Uninstaller
import icejawriter.log.AppLog
import icejawriter.ui.MainFrame
import java.util.Locale
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import javax.swing.UIManager

/**
 * Entry point of IceJA Writer. The same self-contained JAR serves three roles
 * (АРХ-01): launched without arguments it opens the main window; with
 * --install it runs the portable install wizard (УСТ-02); with --uninstall it
 * performs the removal (УСТ-03). Before installing or launching the app the
 * Java version is verified (УСТ-10, УСТ-11).
 */
object Main {

    private enum class LaunchMode {
        APP, INSTALL, UNINSTALL,
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val mode = when {
            args.any { argument -> argument == "--install" } -> LaunchMode.INSTALL
            args.any { argument -> argument == "--uninstall" } -> LaunchMode.UNINSTALL
            else -> LaunchMode.APP
        }

        SwingUtilities.invokeLater {
            // Prefer the system look and feel for a native light theme.
            runCatching {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
            }

            // Allow forcing a language through -Dapp.lang=ru|en.
            System.getProperty("app.lang")?.lowercase()?.let { lang ->
                when (lang) {
                    "ru" -> I18n.setLocale(Locale("ru"))
                    "en" -> I18n.setLocale(Locale.ENGLISH)
                }
            }

            // Capture base Look & Feel fonts before any zoom is applied.
            Zoom.captureBaseFonts()

            when (mode) {
                LaunchMode.APP -> launchApplication()
                LaunchMode.INSTALL -> launchInstaller()
                LaunchMode.UNINSTALL -> launchUninstaller()
            }
        }
    }

    private fun launchApplication() {
        if (!requireCompatibleJava()) {
            System.exit(1)
            return
        }
        AppLog.init(AppPaths.logFile())
        AppLog.info("IceJA Writer started")
        val frame = MainFrame()
        frame.isVisible = true
    }

    private fun launchInstaller() {
        if (!requireCompatibleJava()) {
            System.exit(1)
            return
        }
        val wizard = InstallWizard(null)
        wizard.isVisible = true
        System.exit(0)
    }

    private fun launchUninstaller() {
        if (!requireCompatibleJava()) {
            System.exit(1)
            return
        }
        val installDirectory = InstallPaths.runningJarPath()?.parent
        if (installDirectory == null || !Uninstaller.isInstalledDirectory(installDirectory)) {
            JOptionPane.showMessageDialog(
                null,
                I18n.t("uninstall.notFound"),
                I18n.t("uninstall.title"),
                JOptionPane.INFORMATION_MESSAGE,
            )
        } else {
            UninstallUi.run(null, installDirectory)
        }
        System.exit(0)
    }

    /**
     * Checks that the running JVM satisfies the "Java 25 or newer" requirement
     * (УСТ-10). When it does not, a human-readable message explains what is
     * needed and where to get a JRE 25 LTS, and the operation is abandoned
     * without modifying the system (УСТ-11).
     */
    private fun requireCompatibleJava(): Boolean {
        if (JavaVersionChecker.isRunningCompatible()) return true

        val detected = JavaVersionChecker.runningMajorVersion()
        val versionDescription = if (detected > 0) "$detected" else I18n.t("install.jre.unknown")
        JOptionPane.showMessageDialog(
            null,
            String.format(I18n.t("install.jre.text"), versionDescription),
            I18n.t("install.jre.title"),
            JOptionPane.ERROR_MESSAGE,
        )
        return false
    }
}