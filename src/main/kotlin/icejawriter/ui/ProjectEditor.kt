package icejawriter.ui

/**
 * An editor panel that participates in the project document lifecycle: it
 * commits its widget values into the open document and reloads them when the
 * document changes (project opened, locale switched, structure rebuilt).
 */
interface ProjectEditor {

    /**
     * Writes the current widget values into the open project document. Must be
     * a no-op when nothing changed, so that simply switching panels does not
     * mark the project dirty.
     */
    fun commitToProject()

    /** Reloads widget values from the open document (or clears them). */
    fun refresh()
}