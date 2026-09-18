package icejawriter.install

import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Path resolution and safety rules of the portable installation (УСТ-21,
 * АРХ-05). System directories (for example "C:\Windows" or "/usr") are
 * forbidden as install targets. The blocking check is split into pure
 * POSIX/Windows helpers so both rule sets are unit-testable on any OS.
 */
object InstallPaths {

    /** Known system prefixes on POSIX systems (filesystem root handled separately). */
    private val POSIX_SYSTEM_PREFIXES = listOf(
        "/usr", "/bin", "/sbin", "/etc", "/lib", "/var", "/opt",
        "/System", "/Windows",
    )

    /** Known system prefixes on Windows, matched case-insensitively. */
    private val WINDOWS_SYSTEM_PREFIXES = listOf(
        "c:\\windows", "c:\\program files",
    )

    /**
     * True when the directory is a system location where installing is
     * forbidden (УСТ-21). Uses the rule set of the current operating system
     * and normalizes the path before comparison.
     */
    fun isBlockedSystemDirectory(directory: Path): Boolean {
        val normalized = directory.toAbsolutePath().normalize().toString()
        return if (java.io.File.separator == "\\") isBlockedWindowsPath(normalized) else isBlockedPosixPath(normalized)
    }

    /** Pure check on a normalized absolute POSIX path string. */
    fun isBlockedPosixPath(normalizedPath: String): Boolean {
        if (normalizedPath == "/") return true
        return POSIX_SYSTEM_PREFIXES.any { prefix -> normalizedPath.startsWith(prefix) }
    }

    /** Pure check on a normalized absolute Windows path string (case-insensitive). */
    fun isBlockedWindowsPath(normalizedPath: String): Boolean {
        val folded = normalizedPath.lowercase()
        if (folded == "c:\\") return true
        return WINDOWS_SYSTEM_PREFIXES.any { prefix -> folded.startsWith(prefix) }
    }

    /**
     * Location of the JAR that is currently running this code, or null when
     * launched without the JAR on the classpath (for example in an IDE).
     */
    fun runningJarPath(): Path? =
        InstallPaths::class.java.protectionDomain.codeSource?.location?.let { location ->
            URI.create(location.toString()).let { uri ->
                if (uri.scheme == "file") Paths.get(uri) else null
            }
        }

    /**
     * Default install directory (УСТ-21): the directory next to the running
     * JAR when it is not a system location and is writable, otherwise
     * $HOME/IceJAWriter.
     */
    fun defaultInstallDirectory(): Path {
        val parent = runningJarPath()?.parent
        if (parent != null && !isBlockedSystemDirectory(parent) && Files.isWritable(parent)) {
            return parent
        }
        return Paths.get(System.getProperty("user.home").orEmpty(), "IceJAWriter")
    }

    /**
     * The desktop directory used for the optional Linux .desktop shortcut
     * (УСТ-24). Honors XDG_DESKTOP_DIR when set; falls back to $HOME/Desktop.
     * Returns null when no desktop directory exists on the machine.
     */
    fun desktopDirectory(): Path? {
        val xdgDesktopDir = System.getenv("XDG_DESKTOP_DIR").orEmpty()
        if (xdgDesktopDir.isNotBlank()) {
            val xdg = Paths.get(xdgDesktopDir)
            if (Files.isDirectory(xdg)) return xdg
        }
        return Paths.get(System.getProperty("user.home").orEmpty(), "Desktop")
            .takeIf { desktop -> Files.isDirectory(desktop) }
    }
}