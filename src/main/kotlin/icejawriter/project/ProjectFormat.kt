package icejawriter.project

import java.nio.file.Path

/**
 * Central constants and file-name helpers of the portable .ijw format
 * (ТЗ spec section 4). The archive is a plain DEFLATE ZIP whose entries are
 * UTF-8 text files only: JSON for metadata and cards, Markdown for scene texts
 * (ФРМ-01).
 */
object ProjectFormat {

    /**
     * Format version understood by the current application build (ФРМ-02).
     * The reader accepts any manifest with formatVersion <= FORMAT_VERSION and
     * refuses a newer format with an explicit message.
     */
    const val FORMAT_VERSION = 1

    /** File extension of a project archive. */
    const val FILE_EXTENSION = ".ijw"

    /** Archival capacity: scene numbers run from 1 up to 9999 (ФРМ-04, §4.9). */
    const val MAX_SCENE_COUNT = 9999

    /** Hard byte limit of the serialized novel.json in UTF-8 (ФРМ-05). */
    const val NOVEL_DESCRIPTION_BYTES_LIMIT = 2048

    /** Default scene drafts word target, stored in every new card (ФРМ-13). */
    const val DEFAULT_WORD_TARGET_MIN = 800

    /** Default scene drafts word target upper bound (ФРМ-13). */
    const val DEFAULT_WORD_TARGET_MAX = 1500

    /** Number of rotated backups by default, configurable 0..9 (ФРМ-31). */
    const val DEFAULT_BACKUP_COUNT = 3

    /** Maximum number of rotated backups (ФРМ-31). */
    const val MAX_BACKUP_COUNT = 9

    /** Suffix of the sidecar lock file next to a project (ФРМ-32). */
    const val LOCK_SUFFIX = ".lock"

    /** Zip entry names that form the canonical archive structure. */
    const val MANIFEST_ENTRY = "manifest.json"
    const val NOVEL_ENTRY = "novel.json"
    const val WORLD_ENTRY = "bible/world.json"
    const val CHARACTERS_ENTRY = "bible/characters.json"
    const val CHRONOLOGY_ENTRY = "bible/chronology.json"
    const val RULES_ENTRY = "bible/rules.json"
    const val STORYLINES_ENTRY = "bible/storylines.json"
    const val GLOSSARY_ENTRY = "bible/glossary.json"
    const val STYLE_ENTRY = "bible/style.json"

    /** Prefix of the scenes directory inside the archive (ФРМ-04). */
    const val SCENES_DIRECTORY = "scenes/"

    private val SCENE_ENTRY_REGEX = Regex("""^scene-(\d{4})\.(json|md)$""")

    /** All bible entries expected in a valid archive (ФРМ-06..ФРМ-12). */
    val BIBLE_ENTRIES: List<String> = listOf(
        WORLD_ENTRY,
        CHARACTERS_ENTRY,
        CHRONOLOGY_ENTRY,
        RULES_ENTRY,
        STORYLINES_ENTRY,
        GLOSSARY_ENTRY,
        STYLE_ENTRY,
    )

    /**
     * Four-digit zero-padded scene base name, e.g. 1 -> "scene-0001",
     * 12345 -> "scene-12345"? No: scene numbers are limited to four digits by
     * the pattern, so the caller must already have clamped the value to
     * [MAX_SCENE_COUNT]. Returns the base name WITHOUT the extension.
     */
    fun sceneFileBase(number: Int): String = "scene-" + number.toString().padStart(4, '0')

    /** Zip entry of the scene card, e.g. "scenes/scene-0001.json" (ФРМ-04). */
    fun sceneCardEntry(number: Int): String = SCENES_DIRECTORY + sceneFileBase(number) + ".json"

    /** Zip entry of the scene text, e.g. "scenes/scene-0001.md" (ФРМ-04). */
    fun sceneTextEntry(number: Int): String = SCENES_DIRECTORY + sceneFileBase(number) + ".md"

    /**
     * Parses a scene file name (WITHOUT the "scenes/" directory prefix) into
     * its scene number, or null when the name does not match the canonical
     * scene-NNNN.{json,md} pattern (e.g. a stray file placed there by another
     * text editor, which the loader tolerates and skips).
     */
    fun parseSceneNumber(fileName: String): Int? =
        SCENE_ENTRY_REGEX.matchEntire(fileName)?.groupValues?.get(1)?.toIntOrNull()

    /** Sidecar lock file path for the given project, "project.ijw.lock" (ФРМ-32). */
    fun lockFile(projectFile: Path): Path =
        projectFile.resolveSibling(projectFile.fileName.toString() + LOCK_SUFFIX)

    /** Rotated backup path "project.ijw.bak-N" for the given project (ФРМ-31). */
    fun backupFile(projectFile: Path, index: Int): Path =
        projectFile.resolveSibling(projectFile.fileName.toString() + ".bak-" + index)
}
