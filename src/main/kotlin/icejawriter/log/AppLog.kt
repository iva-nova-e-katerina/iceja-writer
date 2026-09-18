package icejawriter.log

import java.io.IOException
import java.io.PrintWriter
import java.io.StringWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Minimal application journal writing to `logs/app.log` with size based
 * rotation (АРХ-30, НФР-04). The journal must never contain API keys or prompt
 * contents — callers log only statuses and error messages.
 *
 * Before [init] all calls are silently ignored, so the logger is safe to use
 * from any component.
 */
object AppLog {

    /** Rotate once the log file reaches this size. */
    private const val MAX_BYTES: Long = 1_000_000

    private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    private var logFile: Path? = null

    /** Initializes the journal file, creating the directory and rotating if needed. */
    @Synchronized
    fun init(file: Path) {
        logFile = file
        try {
            val directory = file.parent
            if (directory != null) Files.createDirectories(directory)
            if (Files.exists(file) && Files.size(file) >= MAX_BYTES) {
                Files.move(file, file.resolveSibling(file.fileName.toString() + ".1"), StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: IOException) {
            logFile = null
        }
    }

    /** Writes an informational message. */
    @Synchronized
    fun info(message: String) = write("INFO", message, null)

    /** Writes a warning message. */
    @Synchronized
    fun warn(message: String) = write("WARN", message, null)

    /** Writes an error message with an optional stack trace. */
    @Synchronized
    fun error(message: String, throwable: Throwable? = null) = write("ERROR", message, throwable)

    private fun write(level: String, message: String, throwable: Throwable?) {
        val file = logFile ?: return
        val line = buildString {
            append(LocalDateTime.now().format(timestampFormat))
            append(' ')
            append(level)
            append(' ')
            append(message)
            if (throwable != null) {
                append(System.lineSeparator())
                append(stackTraceOf(throwable))
            }
            append(System.lineSeparator())
        }
        try {
            Files.writeString(
                file,
                line,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND,
            )
        } catch (_: IOException) {
            // Logging must never break the application.
        }
    }

    private fun stackTraceOf(throwable: Throwable): String {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        return writer.toString()
    }
}