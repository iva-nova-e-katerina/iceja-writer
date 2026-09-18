package icejawriter.install

import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path

/**
 * Optional Linux .desktop shortcut (УСТ-24). Created only on explicit user
 * consent (a checked box in the wizard) and only when a desktop directory
 * exists; the user can always refuse by leaving the checkbox unchecked.
 */
object DesktopShortcut {

    private const val SHORTCUT_FILE_NAME = "iceja-writer.desktop"

    /** Desktop entry placed next to the install directory. */
    private val SHORTCUT_CONTENT = arrayOf(
        "[Desktop Entry]",
        "Type=Application",
        "Name=IceJA Writer",
        "Comment=Novel Writing Assistant",
        "Terminal=false",
        "Categories=Office;",
    )

    /**
     * Writes the .desktop file that launches the installed shell script.
     * Returns the created file, or null when no desktop directory exists or
     * the launcher script is missing.
     */
    fun createOnDesktop(installDir: Path, osKind: OsKind): Path? {
        if (osKind != OsKind.LINUX) return null

        val desktopDirectory = InstallPaths.desktopDirectory() ?: return null
        val launcher = installDir.resolve(InstallDefaults.SHELL_SCRIPT_NAME)
        if (!Files.isRegularFile(launcher)) return null

        val content = buildString {
            SHORTCUT_CONTENT.forEach { appendLine(it) }
            appendLine("Exec=$launcher")
        }

        val shortcut = desktopDirectory.resolve(SHORTCUT_FILE_NAME)
        Files.writeString(shortcut, content, UTF_8)
        return shortcut
    }
}