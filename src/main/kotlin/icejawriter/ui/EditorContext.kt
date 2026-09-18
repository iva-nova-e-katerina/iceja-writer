package icejawriter.ui

import icejawriter.app.ProjectSession
import icejawriter.config.AppSettings
import icejawriter.model.Character

/**
 * Bridge between the editor panels and the main window. Panels never talk to
 * the main frame directly; they report edits and ask for navigation through
 * this interface (loose coupling, SOLID).
 */
interface EditorContext {

    /** The currently open project session. */
    val session: ProjectSession

    /** Current application settings (language, autosave, LLM, ...). */
    val settings: AppSettings

    /** Registers a content edit: marks the session dirty and refreshes title/status. */
    fun documentEdited()

    /** Registers a structural edit (add/delete/move scenes): dirty + tree rebuild. */
    fun structureEdited()

    /** Opens the scene with the given id in the editor area. */
    fun openScene(sceneId: String)

    /**
     * Reloads the scene editor from the document without committing the
     * current widget values first. Used after the draft panel writes a
     * generated draft directly into the scene text.
     */
    fun reloadScene(sceneId: String)

    /** Selects the bible section with the given tree key (e.g. "world"). */
    fun openBibleSection(key: String)

    /** Shows a transient message in the status bar. */
    fun showStatus(message: String)

    /** Current character list used for POV/relationship choices (ФР-31, ФР-40). */
    fun characterChoices(): List<Character>

    /** Updates the LLM state indicator in the status bar (ИНТ-03, ИНТ-40). */
    fun setLlmState(stateKey: String)

    /** Shows an error dialog localized for the user (ИНТ-31). */
    fun showError(titleKey: String, message: String)
}