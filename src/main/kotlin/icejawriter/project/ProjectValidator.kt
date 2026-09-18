package icejawriter.project

import icejawriter.model.Character
import icejawriter.model.ProjectDocument
import icejawriter.model.Storyline

/**
 * A single problem found when opening a project (ФРМ-20, ФРМ-21). Problems are
 * never fatal: the project is still loaded and shown, and the report is
 * displayed in the "Project check" dialog for the author to fix.
 *
 * @property file archive entry (or file name) the problem belongs to
 * @property problem human-readable description of the problem in English
 */
data class ValidationIssue(
    val file: String,
    val problem: String,
)

/**
 * The result of opening and validating a .ijw archive.
 *
 * @property issues findable-and-fixable problems (broken entry, bad reference,
 *   missed pairing and so on); the project is still usable
 * @property fatalMessage describes an unrecoverable defect (`manifest.json`
 *   missing/corrupt, future format not supported, the file is not a ZIP at
 *   all). When set, the project could not be loaded at all (ФРМ-02, ФРМ-22).
 */
data class ValidationReport(
    val issues: List<ValidationIssue> = emptyList(),
    val fatalMessage: String? = null,
) {
    /** True when the project has any issue or a fatal defect. */
    val hasProblems: Boolean get() = fatalMessage != null || issues.isNotEmpty()
}

/**
 * Model-level validation of an already parsed project (ФРМ-20): reference
 * integrity (relationships.withId, scene pov) and uniqueness of structured ids.
 * Structural problems (JSON parse failures and scene card/text pairing) are
 * collected by the reader itself; this validator covers the "semantic" rules.
 */
object ProjectValidator {

    /**
     * Validates the parsed [project].
     *
     * @param novelByteCount size of novel.json in UTF-8 bytes exactly as stored
     *   in the archive; over-limit files produce a validation issue (ФРМ-05).
     */
    fun validate(project: ProjectDocument, novelByteCount: Int): ValidationReport {
        val issues = mutableListOf<ValidationIssue>()

        if (novelByteCount > ProjectFormat.NOVEL_DESCRIPTION_BYTES_LIMIT) {
            issues += ValidationIssue(
                file = ProjectFormat.NOVEL_ENTRY,
                problem = "novel.json is $novelByteCount bytes, exceeding the " +
                    "${ProjectFormat.NOVEL_DESCRIPTION_BYTES_LIMIT} byte limit (ФРМ-05).",
            )
        }

        val characterIds = project.bible.characters.map(Character::id).toSet()
        validateCharacters(project.bible.characters, issues)
        validateStorylines(project.bible.storylines, issues)
        validateScenes(project, characterIds, issues)

        return ValidationReport(issues = issues)
    }

    /**
     * Checks uniqueness of each character id and that every relationship
     * reference points to an existing character (ФРМ-07, ФРМ-20).
     */
    private fun validateCharacters(characters: List<Character>, issues: MutableList<ValidationIssue>) {
        val knownIds = characters.map(Character::id).toSet()
        val seenIds = HashSet<String>()
        for (character in characters) {
            if (character.id.isNotEmpty() && !seenIds.add(character.id)) {
                issues += ValidationIssue(
                    file = ProjectFormat.CHARACTERS_ENTRY,
                    problem = "duplicate character id \"${character.id}\"; each character must have a unique id",
                )
            }
            for (ref in character.relationships) {
                if (ref.withId.isNotEmpty() && ref.withId !in knownIds) {
                    issues += ValidationIssue(
                        file = ProjectFormat.CHARACTERS_ENTRY,
                        problem = "character \"${character.id}\" references " +
                            "unknown character \"${ref.withId}\" in relationships",
                    )
                }
            }
        }
    }

    /**
     * Checks that storyline ids A/B/C are unique among themselves (ФРМ-10,
     * ФРМ-20 "уникальность id").
     */
    private fun validateStorylines(storylines: List<Storyline>, issues: MutableList<ValidationIssue>) {
        val seenIds = HashSet<String>()
        for (storyline in storylines) {
            if (storyline.id.isNotEmpty() && !seenIds.add(storyline.id)) {
                issues += ValidationIssue(
                    file = ProjectFormat.STORYLINES_ENTRY,
                    problem = "duplicate storyline id \"${storyline.id}\"; each plot line must have a unique id",
                )
            }
        }
    }

    /**
     * Checks scene invariants (ФРМ-13): unique ids, unique numbers, a valid
     * number range 1..9999 and that the pov reference points to an existing
     * character (an empty pov is allowed).
     */
    private fun validateScenes(
        project: ProjectDocument,
        characterIds: Set<String>,
        issues: MutableList<ValidationIssue>,
    ) {
        val seenIds = HashSet<String>()
        val seenNumbers = HashSet<Int>()
        for (scene in project.scenes) {
            val card = scene.card
            val file = sceneEntryNameOrUnknown(card.number)

            if (card.id.isNotEmpty() && !seenIds.add(card.id)) {
                issues += ValidationIssue(
                    file = file,
                    problem = "duplicate scene id \"${card.id}\"; each scene must have a unique id",
                )
            }
            if (!seenNumbers.add(card.number)) {
                issues += ValidationIssue(
                    file = file,
                    problem = "duplicate scene number ${card.number}; each scene must have a unique number",
                )
            }
            if (card.number < 1 || card.number > ProjectFormat.MAX_SCENE_COUNT) {
                issues += ValidationIssue(
                    file = file,
                    problem = "scene number ${card.number} is outside the allowed range 1..${ProjectFormat.MAX_SCENE_COUNT}",
                )
            }
            // The pov field of a card references a character from the bible;
            // an empty value means "not assigned yet" (ФРМ-13).
            if (card.pov.isNotEmpty() && card.pov !in characterIds) {
                issues += ValidationIssue(
                    file = file,
                    problem = "scene pov \"${card.pov}\" does not reference any character from the bible",
                )
            }
        }
    }

    /**
     * Returns the canonical scene card entry name for the given number or an
     * "[unknown scene]" placeholder when the number is outside the valid range
     * and no entry name can be derived.
     */
    private fun sceneEntryNameOrUnknown(number: Int): String =
        if (number in 1..ProjectFormat.MAX_SCENE_COUNT) {
            ProjectFormat.sceneCardEntry(number)
        } else {
            "scenes/[unknown scene number $number]"
        }
}
