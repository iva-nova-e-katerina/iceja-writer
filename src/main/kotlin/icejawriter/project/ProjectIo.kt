package icejawriter.project

import icejawriter.model.Bible
import icejawriter.model.NovelDescription
import icejawriter.model.ProjectDocument
import icejawriter.model.Rules
import icejawriter.model.Scene
import icejawriter.model.SceneCard
import icejawriter.model.Style
import icejawriter.model.World
import java.io.BufferedOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.TreeMap
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

/**
 * Result of reading a .ijw archive.
 *
 * @property project the parsed project; null when the archive was unreadable
 *   in a way that forbids further work (ФРМ-22)
 * @property report validation report; [ValidationReport.fatalMessage] is set
 *   exactly when [project] is null
 */
data class ProjectReadResult(
    val project: ProjectDocument?,
    val report: ValidationReport,
)

/**
 * Raised when a write is refused because another running instance holds the
 * project lock (ФРМ-32).
 */
class ProjectLockedException(val projectFile: Path) :
    IllegalStateException("Project file is locked by another running instance: $projectFile")

/** Base class for every condition that forbids writing the archive. */
open class ProjectWriteException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Raised when the serialized novel.json exceeds the byte limit, i.e. the
 * editor's "block save" enforcement behind the visible counter (ФРМ-05).
 */
class NovelDescriptionTooLargeException(val byteSize: Int) :
    ProjectWriteException(
        "novel.json is $byteSize bytes, exceeding the " +
            "${ProjectFormat.NOVEL_DESCRIPTION_BYTES_LIMIT} byte limit (ФРМ-05).",
    )

/**
 * Reads and writes the portable .ijw archive (ТЗ spec section 4).
 *
 * Writing is atomic (ФРМ-30): the new archive goes to a temp file next to the
 * project and then replaces it with a rename. Before the overwrite the old
 * version is shifted into the rotation chain `project.ijw.bak-1..N` (ФРМ-31).
 * A project locked by another running instance is never overwritten (ФРМ-32).
 */
object ProjectIo {

    // ------------------------------------------------------------------
    // reading
    // ------------------------------------------------------------------

    /**
     * Reads and validates a .ijw archive (ФРМ-20, ФРМ-21, ФРМ-22). The method
     * never throws for a broken archive — the failure is returned inside
     * [ProjectReadResult]. Broken *elements* produce validation issues while
     * the rest of the project remains loadable.
     */
    fun read(projectFile: Path): ProjectReadResult = try {
        ZipFile(projectFile.toFile()).use { zip ->
            val issues = mutableListOf<ValidationIssue>()
            when (val outcome = readZipContent(zip, issues)) {
                is ReadOutcome.Success -> {
                    val validation = ProjectValidator.validate(outcome.project, outcome.novelByteCount)
                    ProjectReadResult(
                        project = outcome.project,
                        report = ValidationReport(issues = issues + validation.issues),
                    )
                }
                is ReadOutcome.Failure -> ProjectReadResult(
                    project = null,
                    report = ValidationReport(issues = issues, fatalMessage = outcome.fatalMessage),
                )
            }
        }
    } catch (e: IOException) {
        ProjectReadResult(
            project = null,
            report = ValidationReport(
                issues = emptyList(),
                fatalMessage = "The file \"${projectFile.fileName}\" is not a readable .ijw archive: ${e.message}",
            ),
        )
    }

    /** Distinguishes "fully loaded" from "cannot be used at all" (ФРМ-22). */
    private sealed interface ReadOutcome {

        /** The archive was parsed; [novelByteCount] is the stored size of novel.json. */
        data class Success(val project: ProjectDocument, val novelByteCount: Int) : ReadOutcome

        /** The archive cannot be used (missing/corrupt manifest, future format). */
        data class Failure(val fatalMessage: String) : ReadOutcome
    }

    /**
     * Parses every entry of an open ZIP. Returns [ReadOutcome.Failure] only
     * for defects that make the whole project unreadable; everything else is
     * downgraded to a validation issue so the survivor data stays available
     * (ФРМ-21).
     */
    private fun readZipContent(zip: ZipFile, issues: MutableList<ValidationIssue>): ReadOutcome {

        // manifest.json — mandatory and authoritative (ФРМ-02, ФРМ-20).
        val manifestBytes = readEntryOrNull(zip, ProjectFormat.MANIFEST_ENTRY)
        if (manifestBytes == null) {
            return failure("manifest.json is missing — the file is not an IceJA Writer project.", issues)
        }
        val manifest = ProjectJsonCodec.manifestFromJson(String(manifestBytes, StandardCharsets.UTF_8))
        if (manifest == null) {
            return failure("manifest.json is not valid JSON.", issues)
        }
        if (manifest.formatVersion > ProjectFormat.FORMAT_VERSION) {
            return failure(
                "The project was saved by a newer IceJA Writer (format " +
                    "${manifest.formatVersion}); this build supports formats up to " +
                    "${ProjectFormat.FORMAT_VERSION}.",
                issues,
            )
        }
        if (manifest.formatVersion <= 0) {
            return failure("manifest.json contains an invalid formatVersion: ${manifest.formatVersion}", issues)
        }

        // novel.json — required, limited to 2 KiB (ФРМ-05); a defect is an
        // issue, not a fatal error.
        val novel = readJsonSection(
            zip = zip,
            entry = ProjectFormat.NOVEL_ENTRY,
            parse = ProjectJsonCodec::novelFromJson,
            issues = issues,
            fallback = NovelDescription(),
        )
        val novelByteCount = readEntryOrNull(zip, ProjectFormat.NOVEL_ENTRY)?.size ?: 0

        // bible/ — seven sections (ФРМ-06..ФРМ-12); a broken section is
        // reported and read as empty, everything else survives.
        val world = readJsonSection(zip, ProjectFormat.WORLD_ENTRY, ProjectJsonCodec::worldFromJson, issues, World())
        val characters = readArraySection(zip, ProjectFormat.CHARACTERS_ENTRY, ProjectJsonCodec::charactersFromJson, issues)
        val chronology = readArraySection(zip, ProjectFormat.CHRONOLOGY_ENTRY, ProjectJsonCodec::chronologyFromJson, issues)
        val rules = readJsonSection(zip, ProjectFormat.RULES_ENTRY, ProjectJsonCodec::rulesFromJson, issues, Rules())
        val storylines = readArraySection(zip, ProjectFormat.STORYLINES_ENTRY, ProjectJsonCodec::storylinesFromJson, issues)
        val glossary = readArraySection(zip, ProjectFormat.GLOSSARY_ENTRY, ProjectJsonCodec::glossaryFromJson, issues)
        val style = readJsonSection(zip, ProjectFormat.STYLE_ENTRY, ProjectJsonCodec::styleFromJson, issues, Style())

        val scenes = readScenes(zip, issues)

        val project = ProjectDocument(
            manifest = manifest,
            novel = novel,
            bible = Bible(
                world = world,
                characters = characters,
                chronology = chronology,
                rules = rules,
                storylines = storylines,
                glossary = glossary,
                style = style,
            ),
            scenes = scenes,
        )
        return ReadOutcome.Success(project = project, novelByteCount = novelByteCount)
    }

    /**
     * Builds a [ReadOutcome.Failure] and records a matching validation issue.
     */
    private fun failure(message: String, issues: MutableList<ValidationIssue>): ReadOutcome.Failure {
        issues += ValidationIssue(file = ProjectFormat.MANIFEST_ENTRY, problem = message)
        return ReadOutcome.Failure(fatalMessage = message)
    }

    /**
     * Reads a single-object JSON section with graceful degradation: a missing
     * entry or invalid JSON is reported as an issue and the [fallback] value
     * is used so the rest of the project survives (ФРМ-21).
     */
    private fun <T> readJsonSection(
        zip: ZipFile,
        entry: String,
        parse: (String) -> T?,
        issues: MutableList<ValidationIssue>,
        fallback: T,
    ): T {
        val bytes = readEntryOrNull(zip, entry)
        if (bytes == null) {
            issues += ValidationIssue(file = entry, problem = "entry is missing in the archive")
            return fallback
        }
        val parsed = parse(String(bytes, StandardCharsets.UTF_8))
        if (parsed == null) {
            issues += ValidationIssue(file = entry, problem = "entry is not valid JSON; it is read as empty")
            return fallback
        }
        return parsed
    }

    /**
     * Reads a JSON array section (characters/chronology/storylines/glossary).
     * Skipped non-object elements are reported individually so that a damaged
     * element never disappears silently (ФРМ-21).
     */
    private fun <T> readArraySection(
        zip: ZipFile,
        entry: String,
        parse: (String) -> ObjectListResult<T>?,
        issues: MutableList<ValidationIssue>,
    ): List<T> {
        val bytes = readEntryOrNull(zip, entry)
        if (bytes == null) {
            issues += ValidationIssue(file = entry, problem = "entry is missing in the archive")
            return emptyList()
        }
        val parsed = parse(String(bytes, StandardCharsets.UTF_8))
        if (parsed == null) {
            issues += ValidationIssue(file = entry, problem = "entry is not a valid JSON array; it is read as empty")
            return emptyList()
        }
        for (index in parsed.skippedElementIndices) {
            issues += ValidationIssue(file = entry, problem = "array element [$index] is not an object and was skipped")
        }
        return parsed.items
    }

    /**
     * Reads all scene cards and texts from `scenes/`, checks the pairing
     * `scene-NNNN.json` <-> `scene-NNNN.md` (ФРМ-20) and the consistency of
     * the card fields with the file name (ФРМ-04). Scenes are returned ordered
     * by their four-digit number.
     */
    private fun readScenes(zip: ZipFile, issues: MutableList<ValidationIssue>): List<Scene> {
        val cardByNumber = TreeMap<Int, SceneCard>()
        val textByNumber = TreeMap<Int, String>()
        val cardEntryName = HashMap<Int, String>()
        val textEntryName = HashMap<Int, String>()

        val entries = zip.entries()
        while (entries.hasMoreElements()) {
            val entry = entries.nextElement()
            if (entry.isDirectory) continue
            val name = entry.name
            if (!name.startsWith(ProjectFormat.SCENES_DIRECTORY)) continue
            val fileName = name.removePrefix(ProjectFormat.SCENES_DIRECTORY)
            val number = ProjectFormat.parseSceneNumber(fileName) ?: continue
            val content = String(readEntry(zip, entry), StandardCharsets.UTF_8)

            if (fileName.endsWith(".json")) {
                cardEntryName[number] = name
                val card = ProjectJsonCodec.sceneCardFromJson(content)
                if (card == null) {
                    issues += ValidationIssue(file = name, problem = "scene card is not valid JSON; it is read as empty")
                    cardByNumber[number] = SceneCard(
                        id = ProjectFormat.sceneFileBase(number),
                        number = number,
                    )
                } else {
                    cardByNumber[number] = card
                }
            } else {
                textEntryName[number] = name
                textByNumber[number] = normalizeMarkdown(content)
            }
        }

        // Pairing (ФРМ-20): a card without its text (or a stray text without
        // its card) is a problem to fix, not a reason to drop data.
        for ((number, name) in cardEntryName) {
            if (number !in textEntryName) {
                issues += ValidationIssue(file = name, problem = "the scene text entry scene text is missing in the archive")
            }
        }
        for ((number, name) in textEntryName) {
            if (number !in cardEntryName) {
                issues += ValidationIssue(file = name, problem = "the scene card entry is missing in the archive")
            }
        }

        val scenes = mutableListOf<Scene>()
        for (number in cardByNumber.keys) {
            val card = cardByNumber[number] ?: continue
            val entryName = cardEntryName[number] ?: ProjectFormat.sceneCardEntry(number)
            val expectedId = ProjectFormat.sceneFileBase(number)
            if (card.number != number) {
                issues += ValidationIssue(
                    file = entryName,
                    problem = "scene number ${card.number} does not match the entry name \"$expectedId\"",
                )
            }
            if (card.id != expectedId) {
                issues += ValidationIssue(
                    file = entryName,
                    problem = "scene id \"${card.id}\" does not match the entry name \"$expectedId\"",
                )
            }
            scenes += Scene(card = cardByNumber[number]!!, text = textByNumber[number] ?: "")
        }
        return scenes
    }

    /** Reads an open entry fully into memory. */
    private fun readEntry(zip: ZipFile, entry: ZipEntry): ByteArray =
        zip.getInputStream(entry).use { it.readBytes() }

    /** Reads an entry by name, or null when the archive has no such entry. */
    private fun readEntryOrNull(zip: ZipFile, name: String): ByteArray? {
        val entry = zip.getEntry(name) ?: return null
        return readEntry(zip, entry)
    }

    /**
     * Normalizes a scene text to the canonical .ijw form: UTF-8 LF line
     * endings (ФРМ-03) and no leading BOM (a leftover from some external text
     * editors).
     */
    private fun normalizeMarkdown(text: String): String =
        text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')

    // ------------------------------------------------------------------
    // writing
    // ------------------------------------------------------------------

    /**
     * Atomically writes the project into the archive (ФРМ-30) after rotating
     * the previous version into the backup chain (ФРМ-31). Refuses to write
     * when the project is locked by another running instance (ФРМ-32) or when
     * novel.json exceeds its byte limit (ФРМ-05).
     *
     * @param projectFile target .ijw path; its parent directory must exist
     * @param project the in-memory project to serialize
     * @param backupCount number of rotated backups 0..9 (clamped); 0 disables
     *   backups entirely
     * @throws ProjectLockedException when another live instance holds the lock
     * @throws NovelDescriptionTooLargeException when the serialized novel.json
     *   is bigger than 2048 bytes
     * @throws ProjectWriteException for any other defect or I/O failure
     */
    fun write(projectFile: Path, project: ProjectDocument, backupCount: Int = ProjectFormat.DEFAULT_BACKUP_COUNT) {
        val absoluteFile = projectFile.toAbsolutePath()

        if (ProjectLock.isHeldByOtherLiveProcess(absoluteFile)) {
            throw ProjectLockedException(absoluteFile)
        }
        if (project.manifest.formatVersion > ProjectFormat.FORMAT_VERSION) {
            throw ProjectWriteException(
                "Refusing to write: manifest format ${project.manifest.formatVersion} is newer than the " +
                    "supported format ${ProjectFormat.FORMAT_VERSION}.",
            )
        }

        val sceneNumbers = project.scenes.map { it.card.number }
        if (sceneNumbers.size != sceneNumbers.toSet().size) {
            throw ProjectWriteException("Each scene must have a unique number; found duplicate numbers in the project.")
        }
        for (number in sceneNumbers) {
            if (number < 1 || number > ProjectFormat.MAX_SCENE_COUNT) {
                throw ProjectWriteException(
                    "Scene number $number is outside the allowed range 1..${ProjectFormat.MAX_SCENE_COUNT}.",
                )
            }
        }
        val novelByteSize = ProjectJsonCodec.novelByteCount(project.novel)
        if (novelByteSize > ProjectFormat.NOVEL_DESCRIPTION_BYTES_LIMIT) {
            throw NovelDescriptionTooLargeException(novelByteSize)
        }

        // Temp file lives next to the project so the rename stays on the same
        // file system and can be atomic (ФРМ-30).
        val directory = absoluteFile.parent
        val tempFile = directory.resolve(absoluteFile.fileName.toString() + ".tmp-" + UUID.randomUUID())
        try {
            writeArchive(tempFile, project)
            rotateBackups(absoluteFile, backupCount)
            try {
                Files.move(tempFile, absoluteFile, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: AtomicMoveNotSupportedException) {
                // Some file systems do not support atomic replacement.
                Files.move(tempFile, absoluteFile, StandardCopyOption.REPLACE_EXISTING)
            }
        } catch (e: IOException) {
            throw ProjectWriteException("Failed to write the project archive \"${absoluteFile.fileName}\": ${e.message}", e)
        } finally {
            try {
                Files.deleteIfExists(tempFile)
            } catch (e: IOException) {
                // Best effort: a leftover temp file must not mask the original
                // failure (or success).
            }
        }
    }

    /**
     * Serializes the whole project into a DEFLATE ZIP at [tempPath] (ФРМ-01):
     * manifest, novel description, the seven bible sections and one card + one
     * Markdown text per scene. Scene texts are normalized to LF.
     */
    private fun writeArchive(tempPath: Path, project: ProjectDocument) {
        Files.newOutputStream(tempPath).use { raw ->
            ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                putTextEntry(zip, ProjectFormat.MANIFEST_ENTRY, ProjectJsonCodec.manifestToJson(project.manifest).toString(2))
                putTextEntry(zip, ProjectFormat.NOVEL_ENTRY, ProjectJsonCodec.novelJsonString(project.novel))

                for (entry in ProjectFormat.BIBLE_ENTRIES) {
                    when (entry) {
                        ProjectFormat.WORLD_ENTRY -> putTextEntry(zip, entry, ProjectJsonCodec.worldToJson(project.bible.world).toString(2))
                        ProjectFormat.CHARACTERS_ENTRY -> putTextEntry(zip, entry, ProjectJsonCodec.charactersToJsonArray(project.bible.characters).toString(2))
                        ProjectFormat.CHRONOLOGY_ENTRY -> putTextEntry(zip, entry, ProjectJsonCodec.chronologyToJsonArray(project.bible.chronology).toString(2))
                        ProjectFormat.RULES_ENTRY -> putTextEntry(zip, entry, ProjectJsonCodec.rulesToJson(project.bible.rules).toString(2))
                        ProjectFormat.STORYLINES_ENTRY -> putTextEntry(zip, entry, ProjectJsonCodec.storylinesToJsonArray(project.bible.storylines).toString(2))
                        ProjectFormat.GLOSSARY_ENTRY -> putTextEntry(zip, entry, ProjectJsonCodec.glossaryToJsonArray(project.bible.glossary).toString(2))
                        ProjectFormat.STYLE_ENTRY -> putTextEntry(zip, entry, ProjectJsonCodec.styleToJson(project.bible.style).toString(2))
                    }
                }

                for (scene in project.scenes.sortedBy { it.card.number }) {
                    putTextEntry(zip, ProjectFormat.sceneCardEntry(scene.card.number), ProjectJsonCodec.sceneCardToJson(scene.card).toString(2))
                    putTextEntry(zip, ProjectFormat.sceneTextEntry(scene.card.number), normalizeMarkdown(scene.text))
                }
            }
        }
    }

    /** Appends one UTF-8 text entry to the ZIP (zip the JSON strings as text). */
    private fun putTextEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }

    // ------------------------------------------------------------------
    // backups (ФРМ-31)
    // ------------------------------------------------------------------

    /**
     * Rotates the backup chain BEFORE the project file is overwritten:
     * `project.ijw.bak-1` (the newest backup = the previous version) ... up to
     * `project.ijw.bak-N`. Older backups shift one step to `N+1` and the
     * oldest is dropped. When the project file does not exist yet (first ever
     * save) there is nothing to back up and the rotation is a no-op.
     */
    fun rotateBackups(projectFile: Path, backupCount: Int) {
        val count = backupCount.coerceIn(0, ProjectFormat.MAX_BACKUP_COUNT)
        if (count <= 0) return
        if (!Files.exists(projectFile)) return

        // Shift backups one step older, from the oldest slot down to the newest.
        for (index in count downTo 2) {
            val source = ProjectFormat.backupFile(projectFile, index - 1)
            val target = ProjectFormat.backupFile(projectFile, index)
            Files.deleteIfExists(target)
            if (Files.exists(source)) {
                Files.move(source, target)
            }
        }
        // The current file becomes the newest backup before it is replaced.
        Files.copy(projectFile, ProjectFormat.backupFile(projectFile, 1), StandardCopyOption.REPLACE_EXISTING)
    }
}
