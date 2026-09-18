package icejawriter.install

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

/**
 * Result of an uninstall attempt. On failure [remaining] lists the paths that
 * could not be removed (for example because the program runs from the install
 * directory and locks them on Windows).
 */
class UninstallResult(
    val success: Boolean,
    val remaining: List<Path>,
)

/**
 * Portable uninstaller (УСТ-30..УСТ-32, АРХ-05): full removal happens by
 * deleting the whole install directory. Project files (*.ijw) inside the
 * directory are discovered up front so the user can be warned and confirm.
 */
object Uninstaller {

    /**
     * True when the directory looks like an IceJA Writer install: the config
     * directory created by the installer exists inside it.
     */
    fun isInstalledDirectory(directory: Path): Boolean =
        Files.isDirectory(directory) && Files.isDirectory(directory.resolve(InstallDefaults.CONFIG_DIR_NAME))

    /**
     * Recursively lists every *.ijw project file inside the install directory
     * (УСТ-31), sorted by path for a deterministic report.
     */
    fun findProjectFiles(directory: Path): List<Path> {
        if (!Files.isDirectory(directory)) return emptyList()
        return Files.walk(directory).use { stream ->
            stream
                .filter { path -> Files.isRegularFile(path) && path.fileName.toString().endsWith(IJW_EXTENSION) }
                .sorted()
                .collect(Collectors.toList())
        }
    }

    /**
     * Deletes the whole directory tree (УСТ-32). Children are removed before
     * their parents: the walked paths are collected into a list and traversed
     * in reversed order. Returns the list of paths that could not be removed.
     */
    fun deleteRecursively(directory: Path): UninstallResult {
        if (!Files.exists(directory)) return UninstallResult(success = true, remaining = emptyList())

        val failedPaths = mutableListOf<Path>()
        try {
            val walkedPaths: List<Path> = Files.walk(directory).use { stream ->
                stream.sorted().collect(Collectors.toList())
            }
            walkedPaths.reversed().forEach { path ->
                try {
                    Files.deleteIfExists(path)
                } catch (exception: IOException) {
                    failedPaths.add(path)
                }
            }
        } catch (exception: IOException) {
            failedPaths.add(directory)
        }
        return UninstallResult(success = failedPaths.isEmpty(), remaining = failedPaths)
    }

    private const val IJW_EXTENSION = ".ijw"
}