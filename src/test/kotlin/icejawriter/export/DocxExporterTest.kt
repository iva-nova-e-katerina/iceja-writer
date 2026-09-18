package icejawriter.export

import icejawriter.model.ProjectDocument
import icejawriter.model.Scene
import icejawriter.model.SceneCard
import icejawriter.model.SceneStatus
import icejawriter.project.TestProjects
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.zip.ZipFile
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Tests the DOCX generator (ФР-94, НФР-12): the package contains the OOXML
 * parts, headings and scene order are correct, [NEW] marks can be removed and
 * the approved-only filter works.
 */
class DocxExporterTest {

    private val labels = ExportLabels(act = "Акт", chapter = "Глава")

    private fun project(): ProjectDocument {
        val base = TestProjects.sampleProject("Мой роман")
        return base.copy(
            scenes = base.scenes.map { scene ->
                when (scene.card.number) {
                    1 -> scene.copy(text = "Текст первой сцены.\n[NEW] Новый факт.")
                    2 -> scene.copy(text = "Текст второй сцены.", card = scene.card.copy(status = SceneStatus.APPROVED))
                    else -> scene
                }
            },
        )
    }

    private fun readEntry(docx: Path, name: String): String =
        ZipFile(docx.toFile()).use { zip ->
            val entry = zip.getEntry(name) ?: return ""
            zip.getInputStream(entry).use { it.readBytes().toString(StandardCharsets.UTF_8) }
        }

    @Test
    fun createsValidOoxmlPackage(@TempDir dir: Path) {
        val target = dir.resolve("novel.docx")
        DocxExporter.export(project(), target, ExportOptions(), labels)

        val names = ZipFile(target.toFile()).use { zip -> zip.entries().asSequence().map { it.name }.toSet() }
        assertTrue(names.contains("[Content_Types].xml"))
        assertTrue(names.contains("_rels/.rels"))
        assertTrue(names.contains("word/document.xml"))
        assertTrue(names.contains("word/styles.xml"))
        assertTrue(names.contains("word/_rels/document.xml.rels"))
        assertTrue(names.contains("docProps/core.xml"))
    }

    @Test
    fun documentContainsHeadingsOrderAndTitlePage(@TempDir dir: Path) {
        val target = dir.resolve("novel.docx")
        DocxExporter.export(project(), target, ExportOptions(includeTitlePage = true), labels)
        val document = readEntry(target, "word/document.xml")

        assertTrue(document.contains("Мой роман"))
        assertTrue(document.contains(">Акт 1<"))
        assertTrue(document.contains(">Акт 2<"))
        assertTrue(document.contains(">Глава 1<"))
        assertTrue(document.contains("Heading1"))
        assertTrue(document.contains("Heading2"))

        val firstScene = document.indexOf("Текст первой сцены.")
        val secondScene = document.indexOf("Текст второй сцены.")
        assertTrue(firstScene in 1 until secondScene, "scenes must keep the narrative order")
    }

    @Test
    fun removesNewMarksByDefault(@TempDir dir: Path) {
        val target = dir.resolve("novel.docx")
        DocxExporter.export(project(), target, ExportOptions(stripNewMarks = true), labels)
        val document = readEntry(target, "word/document.xml")
        assertFalse(document.contains("[NEW]"))
        assertTrue(document.contains("Новый факт."))
    }

    @Test
    fun keepsNewMarksWhenRequested(@TempDir dir: Path) {
        val target = dir.resolve("novel.docx")
        DocxExporter.export(project(), target, ExportOptions(stripNewMarks = false), labels)
        assertTrue(readEntry(target, "word/document.xml").contains("[NEW]"))
    }

    @Test
    fun approvedOnlyFilterDropsDrafts(@TempDir dir: Path) {
        val target = dir.resolve("novel.docx")
        DocxExporter.export(project(), target, ExportOptions(approvedOnly = true), labels)
        val document = readEntry(target, "word/document.xml")
        assertFalse(document.contains("Текст первой сцены."))
        assertTrue(document.contains("Текст второй сцены."))
    }

    @Test
    fun separatorOptionsAreApplied(@TempDir dir: Path) {
        val asterismTarget = dir.resolve("asterism.docx")
        DocxExporter.export(project(), asterismTarget, ExportOptions(separator = SceneSeparator.ASTERISM), labels)
        assertTrue(readEntry(asterismTarget, "word/document.xml").contains("\u2042"))

        val noneTarget = dir.resolve("none.docx")
        DocxExporter.export(project(), noneTarget, ExportOptions(separator = SceneSeparator.NONE), labels)
        assertFalse(readEntry(noneTarget, "word/document.xml").contains("\u2042"))
    }

    @Test
    fun escapesXmlSpecialCharacters(@TempDir dir: Path) {
        val base = TestProjects.sampleProject("Роман")
        val document = base.copy(
            scenes = listOf(
                Scene(
                    card = SceneCard(id = "scene-0001", number = 1),
                    text = "Он сказал <да> & ушёл.",
                ),
            ),
        )
        val target = dir.resolve("escape.docx")
        DocxExporter.export(document, target, ExportOptions(), labels)
        val xml = readEntry(target, "word/document.xml")
        assertTrue(xml.contains("&lt;да&gt; &amp;"))
        assertFalse(xml.contains("Он сказал <да>"))
    }
}