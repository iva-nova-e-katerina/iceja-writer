package icejawriter.project

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Sidecar `.lock` file with the PID of the process currently editing a
 * project. It prevents a second application instance from overwriting the same
 * .ijw (ФРМ-32): the writer refuses to write while another *live* process
 * holds the lock. A lock whose PID is dead (crash, reboot) is treated as stale
 * and can be taken over freely.
 *
 * The lock is best used in a try-with-resources style:
 *
 * ```
 * val lock = ProjectLock.acquire(projectFile)
 * try {
 *     ProjectIo.write(projectFile, project, backupCount)
 * } finally {
 *     lock?.release()
 * }
 * ```
 */
class ProjectLock private constructor(
    private val lockFile: Path,
    private val pid: Long,
) {
    private var released = false

    companion object {

        /**
         * Acquires the lock for [projectFile]. Returns null when the lock file
         * is currently held by another *live* process — in that case the
         * caller must not write to the project (ФРМ-32). A lock held by this
         * same process or by a dead process (stale) is taken over silently.
         */
        fun acquire(projectFile: Path): ProjectLock? {
            val lockFile = ProjectFormat.lockFile(projectFile)
            val pid = ProcessHandle.current().pid()
            val holder = readPid(lockFile)
            if (holder != null && holder != pid && isProcessAlive(holder)) {
                return null
            }
            Files.writeString(lockFile, pid.toString(), StandardCharsets.UTF_8)
            return ProjectLock(lockFile = lockFile, pid = pid)
        }

        /** PID stored in the project lock file, or null when there is no lock at all. */
        fun holderPid(projectFile: Path): Long? = readPid(ProjectFormat.lockFile(projectFile))

        /**
         * True when the project is locked by a *different* live process; the
         * writer refuses to overwrite such files (ФРМ-32).
         */
        fun isHeldByOtherLiveProcess(projectFile: Path): Boolean {
            val holder = holderPid(projectFile) ?: return false
            val ownPid = ProcessHandle.current().pid()
            return holder != ownPid && isProcessAlive(holder)
        }

        /**
         * Reads the PID from the lock file; null when the file does not exist
         * or does not contain a number (a damaged or externally created file is
         * treated as stale, not as a live holder).
         */
        private fun readPid(lockFile: Path): Long? {
            if (!Files.exists(lockFile)) return null
            return try {
                Files.readString(lockFile, StandardCharsets.UTF_8).trim().toLong()
            } catch (e: Exception) {
                null
            }
        }

        /** Tells whether the given PID belongs to a still running process. */
        private fun isProcessAlive(pid: Long): Boolean =
            ProcessHandle.of(pid).map { it.isAlive }.orElse(false)
    }

    /** True while this object still owns the lock file. */
    fun isOwner(): Boolean = !released && readPid(lockFile) == pid

    /** Releases the lock by deleting the .lock file (idempotent). */
    fun release() {
        if (released) return
        Files.deleteIfExists(lockFile)
        released = true
    }
}
