package icejawriter.install

/**
 * Operating system family detected at runtime. Linux and macOS share the
 * POSIX launcher script; Windows uses a batch file (УСТ-22.3).
 */
enum class OsKind {
    LINUX, MACOS, WINDOWS;

    companion object {
        /**
         * Detects the OS family from the operating system name reported by
         * the JVM; unknown/other names default to LINUX.
         */
        fun detect(osName: String = System.getProperty("os.name").orEmpty()): OsKind {
            val folded = osName.lowercase()
            return when {
                folded.contains("windows") -> WINDOWS
                folded.contains("mac") || folded.contains("darwin") -> MACOS
                else -> LINUX
            }
        }
    }
}