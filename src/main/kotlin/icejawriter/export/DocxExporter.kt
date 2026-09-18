package icejawriter.export

import icejawriter.model.ProjectDocument
import icejawriter.model.Scene
import icejawriter.model.SceneStatus
import icejawriter.model.SceneStructure
import java.io.BufferedOutputStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Separator inserted between scenes in the exported document (ФР-92). */
enum class SceneSeparator {
    /** No separator. */
    NONE,

    /** An empty paragraph between scenes. */
    BLANK,

    /** A centered asterism "⁂" between scenes. */
    ASTERISM,
}

/**
 * Export options of the DOCX dialog (ФР-92).
 */
data class ExportOptions(
    val includeTitlePage: Boolean = true,
    val approvedOnly: Boolean = false,
    val separator: SceneSeparator = SceneSeparator.BLANK,
    val stripNewMarks: Boolean = true,
)

/**
 * Localized heading labels used by the generator; the UI builds them from the
 * ResourceBundle so that the export language matches the interface language.
 */
data class ExportLabels(
    val act: String,
    val chapter: String,
)

/**
 * Self-contained OOXML (DOCX) generator (ФР-91, ФР-94). Produces a minimal but
 * valid WordprocessingML package — no external libraries, no installed
 * Microsoft Word. The structure (headings, order, encoding) is covered by
 * unit tests (НФР-12).
 */
object DocxExporter {

    /**
     * Exports the project to [target] following [options].
     *
     * @param project the in-memory project
     * @param target the .docx file to create (overwritten when present)
     * @param options dialog options (title page, status filter, separator)
     * @param labels localized act/chapter heading words
     */
    fun export(
        project: ProjectDocument,
        target: Path,
        options: ExportOptions,
        labels: ExportLabels,
    ) {
        val scenes = SceneStructure.ordered(project.scenes)
            .filter { !options.approvedOnly || it.card.status == SceneStatus.APPROVED }

        val documentXml = buildDocumentXml(project, scenes, options, labels)
        Files.newOutputStream(target).use { raw ->
            ZipOutputStream(BufferedOutputStream(raw)).use { zip ->
                put(zip, "[Content_Types].xml", CONTENT_TYPES)
                put(zip, "_rels/.rels", ROOT_RELS)
                put(zip, "word/document.xml", documentXml)
                put(zip, "word/styles.xml", STYLES)
                put(zip, "word/_rels/document.xml.rels", DOCUMENT_RELS)
                put(zip, "docProps/core.xml", coreProperties(project))
            }
        }
    }

    /** Builds `word/document.xml` with title page, headings and scene texts. */
    private fun buildDocumentXml(
        project: ProjectDocument,
        scenes: List<Scene>,
        options: ExportOptions,
        labels: ExportLabels,
    ): String {
        val body = StringBuilder()

        if (options.includeTitlePage) {
            body.append(centeredRun(project.novel.title.ifBlank { project.manifest.title }, size = 56, bold = true))
            if (project.novel.author.isNotBlank()) {
                body.append(centeredRun(project.novel.author, size = 32, bold = false))
            }
            body.append(pageBreak())
        }

        var currentAct: Int? = null
        var currentChapter: Int? = null
        scenes.forEachIndexed { index, scene ->
            val card = scene.card
            if (card.act != currentAct) {
                currentAct = card.act
                currentChapter = null
                body.append(paragraph("${labels.act} ${card.act}", style = "Heading1"))
            }
            if (card.chapter != currentChapter) {
                currentChapter = card.chapter
                body.append(paragraph("${labels.chapter} ${card.chapter}", style = "Heading2"))
            }

            val text = if (options.stripNewMarks) scene.text.replace("[NEW]", "") else scene.text
            text.split('\n').forEach { line ->
                body.append(if (line.isBlank()) "<w:p/>" else paragraph(line))
            }

            if (index < scenes.lastIndex) {
                when (options.separator) {
                    SceneSeparator.NONE -> Unit
                    SceneSeparator.BLANK -> body.append("<w:p/>")
                    SceneSeparator.ASTERISM -> body.append(centeredRun(ASTERISM, size = 28, bold = false))
                }
            }
        }

        return XML_HEADER +
            "<w:document xmlns:w=\"$WORD_NS\"><w:body>$body" +
            "<w:sectPr><w:pgSz w:w=\"11906\" w:h=\"16838\"/>" +
            "<w:pgMar w:top=\"1134\" w:right=\"850\" w:bottom=\"1134\" w:left=\"1701\"/></w:sectPr>" +
            "</w:body></w:document>"
    }

    /** A regular paragraph, optionally with a style id. */
    private fun paragraph(text: String, style: String? = null): String {
        val properties = if (style == null) "" else "<w:pPr><w:pStyle w:val=\"$style\"/></w:pPr>"
        return "<w:p>$properties<w:r><w:t xml:space=\"preserve\">${escape(text)}</w:t></w:r></w:p>"
    }

    /** A centered run used for the title page and the asterism separator. */
    private fun centeredRun(text: String, size: Int, bold: Boolean): String {
        val boldTag = if (bold) "<w:b/>" else ""
        return "<w:p><w:pPr><w:jc w:val=\"center\"/></w:pPr>" +
            "<w:r><w:rPr>$boldTag<w:sz w:val=\"$size\"/></w:rPr>" +
            "<w:t xml:space=\"preserve\">${escape(text)}</w:t></w:r></w:p>"
    }

    private fun pageBreak(): String =
        "<w:p><w:r><w:br w:type=\"page\"/></w:r></w:p>"

    private fun coreProperties(project: ProjectDocument): String {
        val title = escape(project.novel.title.ifBlank { project.manifest.title })
        val author = escape(project.novel.author)
        return XML_HEADER +
            "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" " +
            "xmlns:dc=\"http://purl.org/dc/elements/1.1/\">" +
            "<dc:title>$title</dc:title><dc:creator>$author</dc:creator>" +
            "</cp:coreProperties>"
    }

    /** Escapes the five XML predefined entities (UTF-8 output stays intact). */
    fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    private fun put(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(StandardCharsets.UTF_8))
        zip.closeEntry()
    }

    private const val ASTERISM = "\u2042"
    private const val WORD_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main"
    private const val XML_HEADER = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"

    private val CONTENT_TYPES = XML_HEADER +
        "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
        "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
        "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
        "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>" +
        "<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>" +
        "<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>" +
        "</Types>"

    private val ROOT_RELS = XML_HEADER +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
        "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>" +
        "</Relationships>"

    private val DOCUMENT_RELS = XML_HEADER +
        "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
        "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>" +
        "</Relationships>"

    private val STYLES = XML_HEADER +
        "<w:styles xmlns:w=\"$WORD_NS\">" +
        "<w:style w:type=\"paragraph\" w:default=\"1\" w:styleId=\"Normal\"><w:name w:val=\"Normal\"/></w:style>" +
        "<w:style w:type=\"paragraph\" w:styleId=\"Heading1\"><w:name w:val=\"heading 1\"/>" +
        "<w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/><w:pPr><w:outlineLvl w:val=\"0\"/></w:pPr>" +
        "<w:rPr><w:b/><w:sz w:val=\"36\"/></w:rPr></w:style>" +
        "<w:style w:type=\"paragraph\" w:styleId=\"Heading2\"><w:name w:val=\"heading 2\"/>" +
        "<w:basedOn w:val=\"Normal\"/><w:next w:val=\"Normal\"/><w:pPr><w:outlineLvl w:val=\"1\"/></w:pPr>" +
        "<w:rPr><w:b/><w:sz w:val=\"30\"/></w:rPr></w:style>" +
        "</w:styles>"
}