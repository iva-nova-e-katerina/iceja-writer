package icejawriter.config

import icejawriter.log.AppLog
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions

/**
 * Loads and saves `config/app.json` (АРХ-11). A missing or damaged file never
 * blocks the application: the defaults are used instead. The API key is
 * protected with POSIX permissions 600 where the file system supports it
 * (НФР-05).
 */
class SettingsStore(private val configFile: Path = AppPaths.configFile()) {

    /** Reads the settings; missing or corrupt content yields defaults. */
    fun load(): AppSettings {
        if (!Files.exists(configFile)) return AppSettings()
        return try {
            val text = Files.readString(configFile, StandardCharsets.UTF_8)
            SettingsJson.fromJson(text) ?: AppSettings().also {
                AppLog.warn("config/app.json is not valid JSON; defaults are used")
            }
        } catch (e: IOException) {
            AppLog.error("Could not read config/app.json", e)
            AppSettings()
        }
    }

    /**
     * Writes the settings atomically and restricts the file permissions to the
     * owner (600). Returns false when the write failed.
     */
    fun save(settings: AppSettings): Boolean = try {
        val directory = configFile.parent
        if (directory != null) Files.createDirectories(directory)
        val temp = configFile.resolveSibling(configFile.fileName.toString() + ".tmp")
        Files.writeString(temp, SettingsJson.toJson(settings).toString(2), StandardCharsets.UTF_8)
        restrictToOwner(temp)
        try {
            Files.move(temp, configFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE)
        } catch (e: java.nio.file.AtomicMoveNotSupportedException) {
            Files.move(temp, configFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        restrictToOwner(configFile)
        true
    } catch (e: IOException) {
        AppLog.error("Could not write config/app.json", e)
        false
    }

    /**
     * Applies POSIX permissions 600 (owner read/write only) so the API key is
     * not world readable (НФР-05). On file systems without POSIX support
     * (Windows) the operation is skipped silently.
     */
    private fun restrictToOwner(file: Path) {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"))
        } catch (_: UnsupportedOperationException) {
            // Windows: ACLs are inherited; nothing to do.
        } catch (_: IOException) {
            // Best effort; the settings still work.
        }
    }
}