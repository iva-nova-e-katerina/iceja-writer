package icejawriter.project

import icejawriter.model.Bible
import icejawriter.model.Character
import icejawriter.model.ChronologyEvent
import icejawriter.model.GlossaryTerm
import icejawriter.model.ProjectDocument
import icejawriter.model.RelationshipRef
import icejawriter.model.Rules
import icejawriter.model.Scene
import icejawriter.model.SceneCard
import icejawriter.model.SceneStatus
import icejawriter.model.Storyline
import icejawriter.model.Style
import icejawriter.model.World
import java.io.BufferedOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Shared fixtures for the .ijw format tests: a fully populated sample project
 * (all seven bible sections + two paired scenes) and a helper that writes a
 * raw ZIP with arbitrary text entries (used to craft broken archives in the
 * validation tests).
 */
internal object TestProjects {

    /**
     * A realistic project with everything filled in. All references are valid:
     * both scenes point their pov to char-001 and the relationship of char-002
     * points to char-001, so the project must validate cleanly.
     */
    fun sampleProject(title: String = "\u0420\u043e\u043c\u0430\u043d"): ProjectDocument {
        val project = ProjectDocument.newProject(title, "0.1.0")
        return project.copy(
            bible = Bible(
                world = World(geography = "\u0417\u0435\u043c\u043b\u044f", notes = "\u201c\u0441\u0438\u043d\u0442\u0435\u0442\u0438\u043a\u0430\u201d"),
                characters = listOf(
                    Character(
                        id = "char-001",
                        name = "\u041c\u0430\u0440\u0438\u044f",
                        aliases = listOf("\u041c\u0430\u0440\u0430"),
                        role = "\u0413\u043b\u0430\u0432\u043d\u0430\u044f \u0433\u0435\u0440\u043e\u0438\u043d\u044f",
                        ageByYear = mapOf("1918" to 25),
                        knows = listOf("\u0442\u0430\u0439\u043d\u0443"),
                    ),
                    Character(
                        id = "char-002",
                        name = "\u041f\u0451\u0442\u0440",
                        relationships = listOf(RelationshipRef(withId = "char-001", type = "\u0421\u043e\u043f\u0435\u0440\u043d\u0438\u043a")),
                        traumas = listOf("\u0432\u043e\u0439\u043d\u0430"),
                    ),
                ),
                chronology = listOf(ChronologyEvent(date = "1939", event = "\u041d\u0430\u0447\u0430\u043b\u043e \u0432\u043e\u0439\u043d\u044b")),
                rules = Rules(possible = "\u043c\u0430\u0433\u0438\u044f \u043e\u0433\u0440\u0430\u043d\u0438\u0447\u0435\u043d\u0430"),
                storylines = listOf(Storyline(id = "A", name = "\u041b\u044e\u0431\u043e\u0432\u044c", secrets = listOf("\u0441\u0435\u043a\u0440\u0435\u0442 A"))),
                glossary = listOf(GlossaryTerm(term = "\u0421\u0438\u043b\u0430", definition = "\u041c\u0430\u0433\u0438\u0447\u0435\u0441\u043a\u0430\u044f \u0441\u0438\u043b\u0430")),
                style = Style(pov = "char-001", tense = "past", forbiddenWords = listOf("\u0432\u0434\u0440\u0443\u0433")),
            ),
            scenes = listOf(
                Scene(
                    card = SceneCard(
                        id = "scene-0001",
                        number = 1,
                        title = "\u0412\u0441\u0442\u0440\u0435\u0447\u0430",
                        pov = "char-001",
                        status = SceneStatus.DRAFT,
                    ),
                    text = "\u0413\u043b\u0430\u0432\u0430 1.\n\u041e\u043d\u0430 \u043f\u0440\u0438\u0448\u043b\u0430 [NEW].\n\u041e\u043d \u043c\u043e\u043b\u0447\u0430\u043b.",
                ),
                Scene(
                    card = SceneCard(
                        id = "scene-0002",
                        number = 2,
                        act = 2,
                        chapter = 1,
                        title = "\u0420\u0430\u0437\u0433\u043e\u0432\u043e\u0440",
                        pov = "char-001",
                        status = SceneStatus.APPROVED,
                        summary = "\u0440\u0435\u0437\u044e\u043c\u0435",
                        wordCount = 1200,
                    ),
                    text = "\u041e\u043d \u043e\u0442\u0432\u0435\u0442\u0438\u043b.\n[NEW] \u041e\u043d\u0430 \u0443\u0437\u043d\u0430\u043b\u0430 \u043f\u0440\u0430\u0432\u0434\u0443.",
                ),
            ),
        )
    }

    /**
     * Writes a ZIP archive with the given text entries (UTF-8). Used both for
     * crafting valid archives and for crafting deliberately broken ones.
     */
    fun writeZip(path: Path, entries: Map<String, String>) {
        Files.newOutputStream(path).use { raw ->
            ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                for ((name, content) in entries) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(content.toByteArray(StandardCharsets.UTF_8))
                    zip.closeEntry()
                }
            }
        }
    }
}
