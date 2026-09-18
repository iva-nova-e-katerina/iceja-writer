package icejawriter.install

import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Result of an installation attempt. On failure [success] is false and
 * [errorMessage] carries the internal reason (the GUI translates it).
 */
class InstallResult(
    val success: Boolean,
    val logLines: List<String>,
    val errorMessage: String = "",
)

/**
 * Portable installer (УСТ-22, УСТ-40). Writes only inside the chosen install
 * directory: creates config/projects/logs, copies the JAR, writes the OS
 * launcher script, writes a default config/app.json and records the
 * logs/install.log journal. During an update (jar already present) the old
 * JAR is backed up and config/app.json plus projects/ are left untouched.
 */
class Installer(
    private val sourceJar: Path,
    private val installDir: Path,
    private val osKind: OsKind,
) {

    companion object {
        private val INSTALL_TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    }

    /**
     * Runs the portable installation. All failures are returned inside the
     * result instead of being thrown, so the wizard can always present a
     * readable outcome.
     */
    fun install(): InstallResult {
        val log = mutableListOf<String>()

        if (InstallPaths.isBlockedSystemDirectory(installDir)) {
            log += "refused: system directory is not an allowed install target: $installDir"
            return InstallResult(success = false, logLines = log, errorMessage = "blocked system directory: $installDir")
        }
        if (!Files.isRegularFile(sourceJar)) {
            log += "refused: source jar is not a regular file: $sourceJar"
            return InstallResult(success = false, logLines = log, errorMessage = "source jar missing: $sourceJar")
        }

        return try {
            Files.createDirectories(installDir)
            logEvent(log, "created install directory: $installDir")

            val configDir = installDir.resolve(InstallDefaults.CONFIG_DIR_NAME)
            val projectsDir = installDir.resolve(InstallDefaults.PROJECTS_DIR_NAME)
            val logsDir = installDir.resolve(InstallDefaults.LOGS_DIR_NAME)
            listOf(configDir, projectsDir, logsDir).forEach { directory ->
                Files.createDirectories(directory)
            }
            logEvent(log, "created config/, projects/, logs/")

            val targetJar = installDir.resolve(InstallDefaults.JAR_FILE_NAME)
            if (Files.exists(targetJar)) {
                val backup = installDir.resolve(InstallDefaults.JAR_FILE_NAME + InstallDefaults.JAR_BACKUP_SUFFIX)
                Files.copy(targetJar, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                logEvent(log, "update: existing jar preserved as $backup")
            }
            Files.copy(
                sourceJar,
                targetJar,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.COPY_ATTRIBUTES,
            )
            logEvent(log, "copied $sourceJar -> $targetJar")

            val launcherName = when (osKind) {
                OsKind.WINDOWS -> InstallDefaults.BATCH_SCRIPT_NAME
                OsKind.LINUX, OsKind.MACOS -> InstallDefaults.SHELL_SCRIPT_NAME
            }
            val launcher = installDir.resolve(launcherName)
            Files.writeString(launcher, LauncherScripts.scriptFor(osKind), UTF_8)
            makeExecutable(launcher)
            logEvent(log, "wrote launcher script $launcher")

            val appConfig = configDir.resolve(InstallDefaults.APP_CONFIG_FILE_NAME)
            if (Files.exists(appConfig)) {
                logEvent(log, "config preserved: $appConfig")
            } else {
                Files.writeString(appConfig, InstallDefaults.DEFAULT_APP_JSON, UTF_8)
                logEvent(log, "wrote default config $appConfig")
            }

            writeInstallLog(logsDir, log)
            InstallResult(success = true, logLines = log)
        } catch (exception: Exception) {
            logEvent(log, "installation FAILED: ${exception.message ?: exception.javaClass.simpleName}")
            writeInstallLogQuietly(log)
            InstallResult(success = false, logLines = log, errorMessage = exception.message ?: exception.javaClass.simpleName)
        }
    }

    /**
     * Marks the launcher as executable on POSIX systems; a no-op elsewhere.
     */
    private fun makeExecutable(launcher: Path) {
        if (osKind != OsKind.WINDOWS) {
            launcher.toFile().setExecutable(true, false)
        }
    }

    /** Appends a timestamped entry to the in-memory install log. */
    private fun logEvent(log: MutableList<String>, message: String) {
        log += LocalDateTime.now().format(INSTALL_TIMESTAMP_FORMAT) + "  " + message
    }

    /** Persists the collected log lines into logs/install.log. */
    private fun writeInstallLog(logsDir: Path, log: List<String>) {
        val installLog = logsDir.resolve(InstallDefaults.INSTALL_LOG_FILE_NAME)
        Files.write(installLog, log.map { entry -> "$entry${System.lineSeparator()}" }, UTF_8)
    }

    /**
     * Best-effort persistence of a failed install log; ignores errors so the
     * original failure is never masked by the logging attempt.
     */
    private fun writeInstallLogQuietly(log: List<String>) {
        runCatching {
            val logsDir = installDir.resolve(InstallDefaults.LOGS_DIR_NAME)
            if (Files.isDirectory(logsDir)) writeInstallLog(logsDir, log)
        }
    }
}