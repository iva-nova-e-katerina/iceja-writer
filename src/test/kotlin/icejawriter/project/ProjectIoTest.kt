package icejawriter.project

import icejawriter.model.Scene
import icejawriter.model.SceneCard
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Round-trip and write-contract tests of ProjectIo: read(project) == written
 * project, canonical ZIP structure, atomicity, LF normalization, and the
 * refusal conditions (ФРМ-05, ФРМ-30, ФРМ-31, ФРМ-32).
 */
class ProjectIoTest {

    @Test
    fun writeThenReadRoundTrip(@TempDir dir: Path) {
        val projectFile = dir.resolve("novel.ijw")
        val expected = TestProjects.sampleProject()

        ProjectIo.write(projectFile, expected)
        val result = ProjectIo.read(projectFile)

        assertEquals(expected, result.project)
        assertFalse(result.report.hasProblems, "a clean project must have an empty validation report")
    }

    @Test
    fun newProjectRoundTripIsClean(@TempDir dir: Path) {
        val projectFile = dir.resolve("novel.ijw")
        val expected = icejawriter.model.ProjectDocument.newProject("\u0420\u043e\u043c\u0430\u043d", "0.1.0")

        ProjectIo.write(projectFile, expected)
        val result = ProjectIo.read(projectFile)

        assertEquals(expected, result.project)
        assertFalse(result.report.hasProblems)
    }

    @Test
    fun archiveContainsCanonicalStructure(@TempDir dir: Path) {
        val projectFile = dir.resolve("novel.ijw")
        ProjectIo.write(projectFile, TestProjects.sampleProject())

        val entryNames = ZipFile(projectFile.toFile()).use { zip ->
            zip.entries().asSequence().map { it.name }.toSortedSet()
        }
        val expected = sortedSetOf(
            "manifest.json",
            "novel.json",
            "bible/world.json",
            "bible/characters.json",
            "bible/chronology.json",
            "bible/rules.json",
            "bible/storylines.json",
            "bible/glossary.json",
            "bible/style.json",
            "scenes/scene-0001.json",
            "scenes/scene-0001.md",
            "scenes/scene-0002.json",
            "scenes/scene-0002.md",
        )
        assertEquals(expected, entryNames)
    }

    @Test
    fun scenesAreWrittenOrderedByNumber(@TempDir dir: Path) {
        val projectFile = dir.resolve("novel.ijw")
        // Deliberately out of order in the model.
        val project = TestProjects.sampleProject().copy(
            scenes = listOf(
                Scene(SceneCard(id = "scene-0002", number = 2), "\u0441\u0446\u0435\u043d\u0430 2"),
                Scene(SceneCard(id = "scene-0001", number = 1), "\u0441\u0446\u0435\u043d\u0430 1"),
            ),
        )
        ProjectIo.write(projectFile, project)
        assertEquals(
            listOf(1, 2),
            ProjectIo.read(projectFile).project!!.scenes.map { it.card.number },
        )
    }

    @Test
    fun atomicWriteLeavesNoTempFiles(@TempDir dir: Path) {
        val projectFile = dir.resolve("novel.ijw")
        ProjectIo.write(projectFile, TestProjects.sampleProject())
        ProjectIo.write(projectFile, TestProjects.sampleProject("second"))

        val leftovers = Files.list(dir).use { stream -> stream.toList() }
            .filter { it.fileName.toString().contains(".tmp-") }
        assertEquals(emptyList<Path>(), leftovers)
    }

    @Test
    fun sceneTextIsNormalizedToLf(@TempDir dir: Path) {
        val projectFile = dir.resolve("novel.ijw")
        val project = TestProjects.sampleProject().copy(
            scenes = listOf(
                Scene(
                    card = SceneCard(id = "scene-0001", number = 1),
                    text = "\u041f\u043b\u043e\u0445\u043e\u0439 CRLF\r\n\u0438 BOM\uFEFFповерх\n\u043a\u043e\u043d\u0435\u0446",
                ),
            ),
        )
        ProjectIo.write(projectFile, project)
        val readText = ProjectIo.read(projectFile).project!!.scenes.single().text
        assertEquals("\u041f\u043b\u043e\u0445\u043e\u0439 CRLF\n\u0438 BOM\uFEFFповерх\n\u043a\u043e\u043d\u0435\u0446", readText)
        assertFalse(readText.contains("\r"))
    }

    @Test
    fun oversizeNovelIsRefusedOnWrite(@TempDir dir: Path) {
        val projectFile = dir.resolve("novel.ijw")
        val project = TestProjects.sampleProject().copy(
            novel = icejawriter.model.NovelDescription(plot = "x".repeat(3000)),
        )
        val exception = assertThrows(NovelDescriptionTooLargeException::class.java) {
            ProjectIo.write(projectFile, project)
        }
        assertTrue(exception.byteSize > ProjectFormat.NOVEL_DESCRIPTION_BYTES_LIMIT)
        assertFalse(Files.exists(projectFile), "a refused write must not create the file")
    }

    @Test
    fun futureFormatIsRefusedOnWrite(@TempDir dir: Path) {
        val projectFile = dir.resolve("novel.ijw")
        val project = TestProjects.sampleProject().copy(
            manifest = TestProjects.sampleProject().manifest.copy(formatVersion = ProjectFormat.FORMAT_VERSION + 1),
        )
        assertThrows(ProjectWriteException::class.java) {
            ProjectIo.write(projectFile, project)
        }
        assertFalse(Files.exists(projectFile))
    }

    @Test
    fun duplicateSceneNumbersAreRefusedOnWrite(@TempDir dir: Path) {
        val projectFile = dir.resolve("novel.ijw")
        val project = TestProjects.sampleProject().copy(
            scenes = listOf(
                Scene(SceneCard(id = "scene-0001", number = 1), "a"),
                Scene(SceneCard(id = "scene-0002", number = 1), "b"),
            ),
        )
        assertThrows(ProjectWriteException::class.java) {
            ProjectIo.write(projectFile, project)
        }
    }

    @Test
    fun sceneNumbersOutOfRangeAreRefusedOnWrite(@TempDir dir: Path) {
        val projectFile = dir.resolve("novel.ijw")
        val project = TestProjects.sampleProject().copy(
            scenes = listOf(Scene(SceneCard(id = "scene-wide", number = 0), "x")),
        )
        assertThrows(ProjectWriteException::class.java) {
            ProjectIo.write(projectFile, project)
        }
    }

    @Test
    fun readKeepsSurvivorDataWhenBibleSectionIsBroken(@TempDir dir: Path) {
        val projectFile = dir.resolve("novel.ijw")
        // A valid project where world.json is deliberately garbage.
        val entries = linkedMapOf(
            "manifest.json" to """{"formatVersion":1,"appVersion":"0.1.0","title":"\u0420\u043e\u043c\u0430\u043d","createdAt":"t","updatedAt":"t"}""",
            "novel.json" to """{"title":"\u0420\u043e\u043c\u0430\u043d","author":"","logline":"","theme":"","plot":""}""",
            "bible/world.json" to "this is not json",
        )
        TestProjects.writeZip(projectFile, entries)

        val result = ProjectIo.read(projectFile)
        assertNotNull(result.project)
        assertTrue(result.report.issues.any { it.file == "bible/world.json" })
    }
}
