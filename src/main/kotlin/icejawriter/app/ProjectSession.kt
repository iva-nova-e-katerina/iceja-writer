package icejawriter.app

import icejawriter.model.ProjectDocument
import icejawriter.project.ProjectIo
import icejawriter.project.ProjectLock
import java.nio.file.Path
import java.time.Instant

/**
 * Holds the project that is currently open and coordinates saving (ФР-03,
 * ФР-04, ФР-80, ФР-81). The UI panels write their values into
 * [document] through [update] and mark the session dirty; saving is atomic and
 * goes through [ProjectIo] with the configured backup count (ФРМ-30, ФРМ-31).
 *
 * A sidecar lock file with the PID of this process is acquired on open so that
 * a second instance refuses to overwrite the project (ФРМ-32).
 */
class ProjectSession {

    /** Path of the open .ijw file, or null when no project is open. */
    var file: Path? = null
        private set

    /** The in-memory project, or null when no project is open. */
    var document: ProjectDocument? = null
        private set

    /** True when there are unsaved changes (ФР-03). */
    var dirty: Boolean = false
        private set

    private var lock: ProjectLock? = null
    private val listeners = mutableListOf<() -> Unit>()

    /** Registers a listener notified on every session change. */
    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    /** Removes a previously registered listener. */
    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

    /** True when a project is currently open. */
    fun isOpen(): Boolean = document != null

    /**
     * Opens an already parsed project: takes the sidecar lock and resets the
     * dirty flag. A stale lock of a crashed process is taken over
     * automatically by [ProjectLock].
     */
    fun open(file: Path, document: ProjectDocument) {
        releaseLock()
        this.file = file
        this.document = document
        this.lock = ProjectLock.acquire(file)
        this.dirty = false
        notifyListeners()
    }

    /**
     * Applies a transformation to the open document and marks the session
     * dirty. No-op when no project is open.
     */
    fun update(transform: (ProjectDocument) -> ProjectDocument) {
        val current = document ?: return
        document = transform(current)
        dirty = true
        notifyListeners()
    }

    /**
     * Applies a transformation only when it actually changes the document.
     * Used by editor panels during commit so that merely switching panels does
     * not mark the project dirty.
     */
    fun updateIfChanged(transform: (ProjectDocument) -> ProjectDocument) {
        val current = document ?: return
        val next = transform(current)
        if (next == current) return
        document = next
        dirty = true
        notifyListeners()
    }

    /** Marks the session dirty without changing the document (text editors). */
    fun markDirty() {
        if (document == null || dirty) return
        dirty = true
        notifyListeners()
    }

    /**
     * Saves the project atomically with the configured backup count and stamps
     * `manifest.updatedAt` (ФРМ-31, §4.3). Returns false when no project is
     * open; write failures propagate as [icejawriter.project.ProjectWriteException].
     */
    fun save(backupCount: Int): Boolean {
        val target = file ?: return false
        val current = document ?: return false
        val stamped = current.copy(manifest = current.manifest.copy(updatedAt = Instant.now().toString()))
        ProjectIo.write(target, stamped, backupCount)
        document = stamped
        dirty = false
        notifyListeners()
        return true
    }

    /** Saves to a new path ("Save as", ФР-03) and re-acquires the lock. */
    fun saveAs(target: Path, backupCount: Int): Boolean {
        val current = document ?: return false
        val stamped = current.copy(manifest = current.manifest.copy(updatedAt = Instant.now().toString()))
        ProjectIo.write(target, stamped, backupCount)
        releaseLock()
        file = target
        document = stamped
        lock = ProjectLock.acquire(target)
        dirty = false
        notifyListeners()
        return true
    }

    /** Closes the project and releases the lock (ФР-04). */
    fun close() {
        releaseLock()
        file = null
        document = null
        dirty = false
        notifyListeners()
    }

    /** True when another live process holds the project lock (ФРМ-32). */
    fun lockedByOtherProcess(): Boolean {
        val target = file ?: return false
        return ProjectLock.isHeldByOtherLiveProcess(target)
    }

    private fun releaseLock() {
        lock?.release()
        lock = null
    }

    private fun notifyListeners() {
        listeners.toList().forEach { it() }
    }
}