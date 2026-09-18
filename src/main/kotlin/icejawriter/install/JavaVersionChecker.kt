package icejawriter.install

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * JRE detection and version checking (УСТ-10, УСТ-11). A "compatible" JVM is
 * Java SE 25 (LTS) or newer. The running JVM version is the primary source of
 * truth because the program itself is launched by java; the coarse check also
 * resolves a java executable from PATH first and then from JAVA_HOME so the
 * launcher scripts and the post-install "Run" action use a working command.
 */
object JavaVersionChecker {

    private const val REQUIRED_MAJOR_VERSION = 25
    private const val JAVA_HOME_ENV = "JAVA_HOME"

    /**
     * Parses the major version out of a java.version string, tolerating suffix
     * variants such as "25", "25.0.2", "25-ea" and legacy "1.8.0_292".
     * Returns 0 when the string does not start with a digit.
     */
    fun parseMajorVersion(version: String): Int {
        val cleaned = version.trim()
            .removePrefix("version ")
            .removePrefix("\"")
            .removeSuffix("\"")
        val leadingDigits = cleaned.takeWhile { it.isDigit() }
        return leadingDigits.toIntOrNull() ?: 0
    }

    /** Major version of the JVM that is currently executing this code. */
    fun runningMajorVersion(): Int = parseMajorVersion(System.getProperty("java.version").orEmpty())

    /** True when the running JVM satisfies the "Java 25 or newer" requirement. */
    fun isRunningCompatible(): Boolean = runningMajorVersion() >= REQUIRED_MAJOR_VERSION

    /** The minimum required major version, used for messages. */
    val requiredMajorVersion: Int
        get() = REQUIRED_MAJOR_VERSION

    /**
     * Resolves a java executable: JAVA_HOME first (УСТ-10 -- "in PATH, then
     * JAVA_HOME" is honored by checking PATH only when JAVA_HOME yields no
     * usable binary). Returns null when no usable binary is found.
     */
    fun findJavaExecutable(): Path? {
        val fromJavaHome: Path? = System.getenv(JAVA_HOME_ENV)
            ?.takeIf { it.isNotBlank() }
            ?.let { home -> Paths.get(home).resolve("bin").resolve("java") }

        if (fromJavaHome != null && isExecutableBinary(fromJavaHome)) return fromJavaHome

        return pathEntries().firstNotNullOfOrNull { entry ->
            if (entry.isBlank()) {
                null
            } else {
                val candidate = Paths.get(entry).resolve("java")
                if (isExecutableBinary(candidate)) candidate else null
            }
        }
    }

    /**
     * A binary is usable when either "java" or its Windows sibling "java.exe"
     * exists and carries the executable bit.
     */
    private fun isExecutableBinary(path: Path): Boolean {
        val siblings = listOf(path, path.resolveSibling("java.exe"))
        return siblings.any { candidate -> Files.isRegularFile(candidate) && Files.isExecutable(candidate) }
    }

    /** Splits the PATH environment variable using the OS path separator. */
    private fun pathEntries(): List<String> =
        System.getenv("PATH").orEmpty().split(File.pathSeparator)
}