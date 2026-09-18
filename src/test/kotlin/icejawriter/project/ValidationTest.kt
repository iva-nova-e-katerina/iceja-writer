package icejawriter.project

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Validation behavior of the reader over crafted archives (ФРМ-02, ФРМ-20,
 * ФРМ-21, ФРМ-22): a defect is either a fatal message (project unusable) or a
 * fixable validation issue (project still loaded, no data loss).
 */
class ValidationTest {

    private val validManifest =
        """{"formatVersion":1,"appVersion":"0.1.0","title":"\u0420\u043e\u043c\u0430\u043d","createdAt":"2026-09-18T10:00:00Z","updatedAt":"2026-09-18T10:00:00Z"}"""

    private val emptyNovel = """{"title":"","author":"","logline":"","theme":"","plot":""}"""

    /** A fully consistent archive: all entries present, references valid. */
    private fun validEntries(): Map<String, String> = linkedMapOf(
        "manifest.json" to validManifest,
        "novel.json" to emptyNovel,
        "bible/world.json" to """{"geography":"\u0417\u0435\u043c\u043b\u044f","politics":"","economy":"","techMagic":"","religion":"","languages":"","notes":""}""",
        "bible/characters.json" to """
            [
              {"id":"char-001","name":"\u041c\u0430\u0440\u0438\u044f","aliases":[],"role":"","ageByYear":{},"arcs":[],"relationships":[],"voice":"","knows":[],"doesNotKnow":[],"traumas":[],"goals":[],"weakness":"","notes":""},
              {"id":"char-002","name":"\u041f\u0451\u0442\u0440","aliases":[],"role":"","ageByYear":{},"arcs":[],"relationships":[{"withId":"char-001","type":"\u0421\u043e\u043f\u0435\u0440\u043d\u0438\u043a","notes":""}],"voice":"","knows":[],"doesNotKnow":[],"traumas":[],"goals":[],"weakness":"","notes":""}
            ]
        """.trimIndent(),
        "bible/chronology.json" to """[{"date":"1939","event":"\u0432\u043e\u0439\u043d\u0430","notes":""}]""",
        "bible/rules.json" to """{"possible":"","impossible":"","cost":"","limits":"","notes":""}""",
        "bible/storylines.json" to """[{"id":"A","name":"","setup":"","secrets":[],"foreshadowing":[],"resolution":"","notes":""}]""",
        "bible/glossary.json" to """[{"term":"\u0421\u0438\u043b\u0430","definition":""}]""",
        "bible/style.json" to """{"pov":"char-001","tense":"past","tone":"","forbiddenWords":[],"typicalPhrases":[],"notes":""}""",
        "scenes/scene-0001.json" to """
            {"id":"scene-0001","number":1,"act":1,"chapter":1,"title":"","status":"draft","pov":"char-001","location":"","time":"","goal":"","conflict":"","twist":"","readerLearns":"","charactersLearn":"","entry":"","exit":"","continuityNotes":[],"summary":"","wordTargetMin":800,"wordTargetMax":1500,"wordCount":0,"updatedAt":"2026-09-18T10:00:00Z"}
        """.trimIndent(),
        "scenes/scene-0001.md" to "\u0413\u043b\u0430\u0432\u0430 1.\n\u041e\u043d\u0430 \u043f\u0440\u0438\u0448\u043b\u0430.",
    )

    private fun sceneCardTemplate(
        id: String = "scene-0001",
        number: Int = 1,
        pov: String = "char-001",
        status: String = "draft",
    ): String =
        """{"id":"$id","number":$number,"act":1,"chapter":1,"title":"","status":"$status","pov":"$pov","location":"","time":"","goal":"","conflict":"","twist":"","readerLearns":"","charactersLearn":"","entry":"","exit":"","continuityNotes":[],"summary":"","wordTargetMin":800,"wordTargetMax":1500,"wordCount":0,"updatedAt":""}"""

    @Test
    fun validCraftedProjectHasNoIssues(@TempDir dir: Path) {
        val file = dir.resolve("crafted.ijw")
        TestProjects.writeZip(file, validEntries())

        val result = ProjectIo.read(file)
        assertNotNull(result.project)
        assertFalse(result.report.hasProblems)
        assertEquals(1, result.project!!.scenes.size)
    }

    @Test
    fun missingManifestIsFatal(@TempDir dir: Path) {
        val file = dir.resolve("broken.ijw")
        TestProjects.writeZip(file, mapOf("novel.json" to emptyNovel))

        val result = ProjectIo.read(file)
        assertNull(result.project)
        assertNotNull(result.report.fatalMessage)
    }

    @Test
    fun futureFormatVersionIsFatal(@TempDir dir: Path) {
        val file = dir.resolve("future.ijw")
        val futureManifest = validManifest.replace("\"formatVersion\":1", "\"formatVersion\":2")
        TestProjects.writeZip(file, validEntries().plus("manifest.json" to futureManifest))

        val result = ProjectIo.read(file)
        assertNull(result.project)
        assertTrue(result.report.fatalMessage!!.contains("2"), "the message must name the unsupported version")
    }

    @Test
    fun invalidManifestJsonIsFatal(@TempDir dir: Path) {
        val file = dir.resolve("bad.ijw")
        TestProjects.writeZip(file, validEntries().plus("manifest.json" to "not json"))

        val result = ProjectIo.read(file)
        assertNull(result.project)
        assertNotNull(result.report.fatalMessage)
    }

    @Test
    fun garbageBytesAreFatal(@TempDir dir: Path) {
        val file = dir.resolve("garbage.ijw")
        Files.write(file, "this is definitely not a zip archive".toByteArray())

        val result = ProjectIo.read(file)
        assertNull(result.project)
        assertNotNull(result.report.fatalMessage)
    }

    @Test
    fun oversizeNovelProducesIssue(@TempDir dir: Path) {
        val file = dir.resolve("bignovel.ijw")
        val bigPlot = "x".repeat(3000)
        val bigNovel = """{"title":"","author":"","logline":"","theme":"","plot":"$bigPlot"}"""
        TestProjects.writeZip(file, validEntries().plus("novel.json" to bigNovel))

        val result = ProjectIo.read(file)
        assertNotNull(result.project)
        assertTrue(result.report.issues.any { it.file == "novel.json" })
    }

    @Test
    fun unknownPovReferenceProducesIssue(@TempDir dir: Path) {
        val file = dir.resolve("pov.ijw")
        val badCard = sceneCardTemplate(pov = "char-999")
        TestProjects.writeZip(file, validEntries().plus("scenes/scene-0001.json" to badCard))

        val result = ProjectIo.read(file)
        assertNotNull(result.project)
        assertTrue(result.report.issues.any { it.problem.contains("pov") })
    }

    @Test
    fun unknownRelationshipReferenceProducesIssue(@TempDir dir: Path) {
        val file = dir.resolve("rel.ijw")
        val characters = """
            [
              {"id":"char-001","name":"\u0410","aliases":[],"role":"","ageByYear":{},"arcs":[],"relationships":[{"withId":"char-999","type":"\u041d\u0438\u043a\u0442\u043e","notes":""}],"voice":"","knows":[],"doesNotKnow":[],"traumas":[],"goals":[],"weakness":"","notes":""}
            ]
        """.trimIndent()
        TestProjects.writeZip(file, validEntries().plus("bible/characters.json" to characters))

        val result = ProjectIo.read(file)
        assertNotNull(result.project)
        assertTrue(result.report.issues.any { it.problem.contains("relationships") })
    }

    @Test
    fun duplicateCharacterIdsProduceIssue(@TempDir dir: Path) {
        val file = dir.resolve("dupchars.ijw")
        val characters = """
            [
              {"id":"char-001","name":"\u0410","aliases":[],"role":"","ageByYear":{},"arcs":[],"relationships":[],"voice":"","knows":[],"doesNotKnow":[],"traumas":[],"goals":[],"weakness":"","notes":""},
              {"id":"char-001","name":"\u0411","aliases":[],"role":"","ageByYear":{},"arcs":[],"relationships":[],"voice":"","knows":[],"doesNotKnow":[],"traumas":[],"goals":[],"weakness":"","notes":""}
            ]
        """.trimIndent()
        TestProjects.writeZip(file, validEntries().plus("bible/characters.json" to characters))

        val result = ProjectIo.read(file)
        assertTrue(result.report.issues.any { it.problem.contains("duplicate character id") })
    }

    @Test
    fun duplicateSceneIdsProduceIssue(@TempDir dir: Path) {
        val file = dir.resolve("dupscenes.ijw")
        val secondCard = sceneCardTemplate(id = "scene-0001", number = 2)
        TestProjects.writeZip(
            file,
            validEntries().plus(
                mapOf(
                    "scenes/scene-0002.json" to secondCard,
                    "scenes/scene-0002.md" to "text 2",
                ),
            ),
        )

        val result = ProjectIo.read(file)
        assertTrue(result.report.issues.any { it.problem.contains("duplicate scene id") })
    }

    @Test
    fun cardWithoutTextProducesIssue(@TempDir dir: Path) {
        val file = dir.resolve("notext.ijw")
        val entries = validEntries().minus("scenes/scene-0001.md")
        TestProjects.writeZip(file, entries)

        val result = ProjectIo.read(file)
        assertNotNull(result.project)
        assertTrue(result.report.issues.any { it.problem.contains("text entry") })
    }

    @Test
    fun textWithoutCardProducesIssue(@TempDir dir: Path) {
        val file = dir.resolve("nocard.ijw")
        val entries = validEntries().minus("scenes/scene-0001.json")
        TestProjects.writeZip(file, entries)

        val result = ProjectIo.read(file)
        assertNotNull(result.project)
        assertTrue(result.report.issues.any { it.problem.contains("card entry") })
    }

    @Test
    fun sceneNumberInEntryNameMismatchProducesIssue(@TempDir dir: Path) {
        val file = dir.resolve("mismatch.ijw")
        // Entry says scene-0001 but the card claims number 5 / id scene-0005.
        val mismatched = sceneCardTemplate(id = "scene-0005", number = 5)
        TestProjects.writeZip(file, validEntries().plus("scenes/scene-0001.json" to mismatched))

        val result = ProjectIo.read(file)
        assertNotNull(result.project)
        assertTrue(result.report.issues.any { it.problem.contains("does not match") })
    }
}
