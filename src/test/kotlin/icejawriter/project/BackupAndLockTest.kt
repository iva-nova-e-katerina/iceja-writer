package icejawriter.project

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Tests the rotated backup chain (ФРМ-31) and the sidecar .lock file
 * (ФРМ-32): refusal to overwrite a project held by another live process,
 * stale-lock takeover, and bounded rotation 0..9.
 */
class BackupAndLockTest {

    private fun readTitle(path: Path): String =
        ProjectIo.read(path).project!!.manifest.title

    @Test
    fun backupChainRotatesOldVersions(@TempDir dir: Path) {
        val projectFile = dir.resolve("novel.ijw")
        // Save four versions of the same file: v1 -> v2 -> v3 -> v4.
        for (version in listOf("v1", "v2", "v3", "v4")) {
            ProjectIo.write(projectFile, TestProjects.sampleProject(version), backupCount = 3)
        }

        assertEquals("v4", readTitle(projectFile))
        assertEquals("v3", readTitle(ProjectFormat.backupFile(projectFile, 1)))
        assertEquals("v2", readTitle(ProjectFormat.backupFile(projectFile, 2)))
        assertEquals("v1", readTitle(ProjectFormat.backupFile(projectFile, 3)))
        assertFalse(Files.exists(ProjectFormat.backupFile(projectFile, 4)))
    }

    @Test
    fun backupCountZeroDisablesBackups(@TempDir dir: Path) {
        val projectFile = dir.resolve("novel.ijw")
        for (version in listOf("v1", "v2", "v3")) {
            ProjectIo.write(projectFile, TestProjects.sampleProject(version), backupCount = 0)
        }

        assertEquals("v3", readTitle(projectFile))
        for (index in 1..ProjectFormat.MAX_BACKUP_COUNT) {
            assertFalse(Files.exists(ProjectFormat.backupFile(projectFile, index)))
        }
    }

    @Test
    fun backupCountIsClampedToNine(@TempDir dir: Path) {
        val projectFile = dir.resolve("novel.ijw")
        for (version in 1..12) {
            ProjectIo.write(projectFile, TestProjects.sampleProject("v$version"), backupCount = 99)
        }

        for (index in 1..ProjectFormat.MAX_BACKUP_COUNT) {
            assertTrue(Files.exists(ProjectFormat.backupFile(projectFile, index)))
        }
        assertFalse(Files.exists(ProjectFormat.backupFile(projectFile, ProjectFormat.MAX_BACKUP_COUNT + 1)))
    }

    @Test
    fun firstWriteCreatesNoBackup(@TempDir dir: Path) {
        val projectFile = dir.resolve("novel.ijw")
        ProjectIo.write(projectFile, TestProjects.sampleProject("first"), backupCount = 3)
        assertFalse(Files.exists(ProjectFormat.backupFile(projectFile, 1)))
    }

    @Test
    fun lockAcquireAndRelease(@TempDir dir: Path) {
        val projectFile = dir.resolve("novel.ijw")
        val lock = ProjectLock.acquire(projectFile)
        assertNotNull(lock)
        assertEquals(ProcessHandle.current().pid(), ProjectLock.holderPid(projectFile))
        assertTrue(lock!!.isOwner())

        // A second acquire from the same process is allowed (harmless re-entry).
        val second = ProjectLock.acquire(projectFile)
        assertNotNull(second)

        lock.release()
        second!!.release()
        assertNull(ProjectLock.holderPid(projectFile))
        assertFalse(lock.isOwner())
    }

    @Test
    fun acquireTakesOverStaleLock(@TempDir dir: Path) {
        val projectFile = dir.resolve("novel.ijw")
        // A lock whose PID cannot belong to a live process is stale.
        Files.writeString(ProjectFormat.lockFile(projectFile), "99999999")
        assertFalse(ProjectLock.isHeldByOtherLiveProcess(projectFile))

        val lock = ProjectLock.acquire(projectFile)
        assertNotNull(lock)
        lock!!.release()
    }

    @Test
    fun acquireRefusesForeignLiveLock() {
        val foreignPid = otherLiveProcessId() ?: return // nowhere to run this assertion
        val dir = Files.createTempDirectory("lock-foreign-")
        try {
            val projectFile = dir.resolve("novel.ijw")
            Files.writeString(ProjectFormat.lockFile(projectFile), foreignPid.toString())

            assertTrue(ProjectLock.isHeldByOtherLiveProcess(projectFile))
            assertNull(ProjectLock.acquire(projectFile))
        } finally {
            dir.toFile().deleteRecursively()
        }
    }

    @Test
    fun writerRefusesForeignLiveLock(@TempDir dir: Path) {
        val foreignPid = otherLiveProcessId() ?: return // nowhere to run this assertion
        val projectFile = dir.resolve("novel.ijw")
        Files.writeString(ProjectFormat.lockFile(projectFile), foreignPid.toString())

        assertThrows(ProjectLockedException::class.java) {
            ProjectIo.write(projectFile, TestProjects.sampleProject())
        }
    }

    @Test
    fun writerProceedsUnderOwnLock(@TempDir dir: Path) {
        val projectFile = dir.resolve("novel.ijw")
        val lock = ProjectLock.acquire(projectFile)
        assertNotNull(lock)
        try {
            ProjectIo.write(projectFile, TestProjects.sampleProject(), backupCount = 2)
        } finally {
            lock!!.release()
        }
        assertEquals("\u0420\u043e\u043c\u0430\u043d", readTitle(projectFile))
    }

    /**
     * Returns the PID of any other currently live process, or null when no
     * other process exists (practically impossible, but the test gets skipped
     * gracefully then). A stable process (pid 1, the OS init) is preferred to
     * avoid flaky deaths mid-test.
     */
    private fun otherLiveProcessId(): Long? {
        val ownPid = ProcessHandle.current().pid()
        val allPids = ProcessHandle.allProcesses().map { it.pid() }.toList()
        if (1L != ownPid && 1L in allPids) return 1L
        return allPids.firstOrNull { it != ownPid }
    }
}
