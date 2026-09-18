package icejawriter.model

/**
 * Drafting status of a scene card, serialized as a string (ФРМ-13).
 */
enum class SceneStatus(val wireName: String) {

    /** The scene is being drafted. */
    DRAFT("draft"),

    /** The scene is being revised and polished. */
    REVISED("revised"),

    /** The scene is approved and belongs to the novel. */
    APPROVED("approved");

    companion object {
        /**
         * Parses the wire value of a scene status. Unknown or empty values
         * silently fall back to [DRAFT] so that a future status never breaks
         * the rest of a project (ФРМ-21).
         */
        fun parse(value: String): SceneStatus =
            entries.firstOrNull { it.wireName == value } ?: DRAFT
    }
}

/**
 * A scene card stored in `scenes/scene-NNNN.json` (ФРМ-13). The public fields
 * follow the methodology of `концепт.docx`: act, chapter, pov, location, time,
 * goal, conflict, twist, what the reader / characters learn, entry / exit
 * states, continuity notes and the summary produced after approval.
 */
data class SceneCard(
    val id: String = "",
    val number: Int = 0,
    val act: Int = 1,
    val chapter: Int = 1,
    val title: String = "",
    val status: SceneStatus = SceneStatus.DRAFT,
    val pov: String = "",
    val location: String = "",
    val time: String = "",
    val goal: String = "",
    val conflict: String = "",
    val twist: String = "",
    val readerLearns: String = "",
    val charactersLearn: String = "",
    val entry: String = "",
    val exit: String = "",
    val continuityNotes: List<String> = emptyList(),
    val summary: String = "",
    val wordTargetMin: Int = 800,
    val wordTargetMax: Int = 1500,
    val wordCount: Int = 0,
    val updatedAt: String = "",
)

/**
 * A scene: card plus Markdown text with the SAME four-digit file base name
 * (ФРМ-04, ФРМ-14). The text may contain [NEW] service marks that are removed
 * only at export time, never inside the .ijw archive.
 */
data class Scene(
    val card: SceneCard,
    val text: String = "",
) {
    /**
     * Shared short identifier of the card and its text, exactly the file base
     * name, e.g. "scene-0001".
     */
    val id: String get() = card.id
}
