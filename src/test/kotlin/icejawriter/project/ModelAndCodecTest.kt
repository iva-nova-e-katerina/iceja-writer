package icejawriter.project

import icejawriter.model.Character
import icejawriter.model.ChronologyEvent
import icejawriter.model.NovelDescription
import icejawriter.model.RelationshipRef
import icejawriter.model.SceneCard
import icejawriter.model.SceneStatus
import icejawriter.model.Storyline
import icejawriter.model.World
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests the org.json mapping of the domain model (ProjectJsonCodec): field
 * round-trips, lenient parsing and the novel.json byte counter (ФРМ-05).
 */
class ModelAndCodecTest {

    @Test
    fun manifestRoundTrip() {
        val manifest = icejawriter.model.Manifest(
            formatVersion = 1,
            appVersion = "0.1.0",
            title = "\u0420\u043e\u043c\u0430\u043d",
            createdAt = "2026-09-18T10:00:00Z",
            updatedAt = "2026-09-18T10:00:00Z",
        )
        val json = ProjectJsonCodec.manifestToJson(manifest).toString()
        val parsed = ProjectJsonCodec.manifestFromJson(json)
        assertEquals(manifest, parsed)
    }

    @Test
    fun novelRoundTrip() {
        val novel = NovelDescription(
            title = "\u0420\u043e\u043c\u0430\u043d",
            author = "\u0418\u0432\u0430\u043d\u043e\u0432",
            logline = "\u041e\u0431 \u043b\u044e\u0431\u0432\u0438 \u0438 \u0432\u043e\u0439\u043d\u0435",
            theme = "\u043f\u0440\u043e\u0449\u0435\u043d\u0438\u0435",
            plot = "\u0421\u044e\u0436\u0435\u0442 \u0432 \u043f\u043e\u0434\u0440\u043e\u0431\u043d\u043e\u0441\u0442\u044f\u0445",
        )
        val parsed = ProjectJsonCodec.novelFromJson(ProjectJsonCodec.novelToJson(novel).toString())
        assertEquals(novel, parsed)
    }

    @Test
    fun novelByteCountEqualsSerializedFileSize() {
        val novel = NovelDescription(plot = "x".repeat(1500))
        val expectedBytes = ProjectJsonCodec.novelJsonString(novel).toByteArray(Charsets.UTF_8).size
        assertEquals(expectedBytes, ProjectJsonCodec.novelByteCount(novel))
        // The counter must stay under the hard limit for a reasonable novel description.
        assertTrue(ProjectJsonCodec.novelByteCount(NovelDescription()) <= ProjectFormat.NOVEL_DESCRIPTION_BYTES_LIMIT)
        // And it must exceed the limit for an oversized plot (ФРМ-05).
        assertTrue(ProjectJsonCodec.novelByteCount(NovelDescription(plot = "x".repeat(3000))) > ProjectFormat.NOVEL_DESCRIPTION_BYTES_LIMIT)
    }

    @Test
    fun characterRoundTrip() {
        val character = Character(
            id = "char-001",
            name = "\u041c\u0430\u0440\u0438\u044f",
            aliases = listOf("\u041c\u0430\u0440\u0430", "\u041c"),
            role = "\u0433\u0435\u0440\u043e\u0438\u043d\u044f",
            ageByYear = mapOf("1918" to 25, "1945" to 52),
            arcs = listOf("\u0430\u0440\u043a\u0430 \u043e\u0434\u043d\u0430"),
            relationships = listOf(
                RelationshipRef(withId = "char-002", type = "\u0421\u043e\u043f\u0435\u0440\u043d\u0438\u043a", notes = "\u043f\u043e\u0434\u0440\u043e\u0431\u043d\u043e"),
            ),
            voice = "\u0442\u0438\u0445\u0438\u0439",
            knows = listOf("\u0442\u0430\u0439\u043d\u0443"),
            doesNotKnow = listOf("\u043f\u0440\u0430\u0432\u0434\u0443"),
            traumas = listOf("\u0432\u043e\u0439\u043d\u0430"),
            goals = listOf("\u0432\u044b\u0436\u0438\u0442\u044c"),
            weakness = "\u0441\u0442\u0440\u0430\u0445",
            notes = "\u043f\u0440\u0438\u043c\u0435\u0447\u0430\u043d\u0438\u0435",
        )
        val jsonObject = ProjectJsonCodec.characterToJson(character)
        val text = jsonObject.toString()
        val parsed = ProjectJsonCodec.charactersFromJson("[$text]")!!.items.single()
        assertEquals(character, parsed)
        assertTrue(text.contains("\"ageByYear\""))
        assertTrue(text.contains("\"1918\":25"))
    }

    @Test
    fun charactersArrayReportsSkippedNonObjectElements() {
        val text = """[{"id":"char-001","name":"\u0410"}, "не объект", 42, {"id":"char-002","name":"\u0411"}]"""
        val parsed = ProjectJsonCodec.charactersFromJson(text)
        assertEquals(listOf(1, 2), parsed!!.skippedElementIndices)
        assertEquals(listOf("char-001", "char-002"), parsed.items.map { it.id })
    }

    @Test
    fun sceneCardRoundTripAllStatuses() {
        val card = SceneCard(
            id = "scene-0001",
            number = 1,
            act = 3,
            chapter = 5,
            title = "\u0420\u0430\u0437\u0432\u044f\u0437\u043a\u0430",
            status = SceneStatus.REVISED,
            pov = "char-001",
            location = "\u0434\u043e\u043c",
            time = "\u0432\u0435\u0447\u0435\u0440",
            goal = "\u0434\u043e\u0433\u043e\u0432\u043e\u0440\u0438\u0442\u044c\u0441\u044f",
            conflict = "\u0441\u0442\u0430\u0440\u0430\u044f \u0432\u0440\u0430\u0436\u0434\u0430",
            twist = "\u043e\u043d\u0430 \u043f\u043e\u043c\u043d\u0438\u0442",
            readerLearns = "X",
            charactersLearn = "Y",
            entry = "state A",
            exit = "state B",
            continuityNotes = listOf("\u043f\u0443\u0431\u043a\u0430"),
            summary = "\u0440\u0435\u0437\u044e\u043c\u0435",
            wordCount = 1400,
            updatedAt = "2026-09-18T10:00:00Z",
        )
        val text = ProjectJsonCodec.sceneCardToJson(card).toString()
        assertEquals(card, ProjectJsonCodec.sceneCardFromJson(text))
        // Unknown status values fall back to DRAFT (ФРМ-21), never to a crash.
        assertEquals(SceneStatus.DRAFT, ProjectJsonCodec.sceneCardFromJson("""{"id":"scene-1","number":1,"status":"final"}""")!!.status)
    }

    @Test
    fun bibleSectionsRoundTrip() {
        val translated = ProjectJsonCodec
        val world = World(geography = "g", politics = "p", notes = "n")
        assertEquals(world, translated.worldFromJson(translated.worldToJson(world).toString()))

        val event = ChronologyEvent(date = "1905-01", event = "e", notes = "n")
        val parsedEvents = translated.chronologyFromJson(translated.chronologyToJsonArray(listOf(event)).toString())
        assertEquals(listOf(event), parsedEvents!!.items)

        val storyline = Storyline(id = "B", name = "line", setup = "s", resolution = "r", secrets = listOf("z"))
        val parsedLines = translated.storylinesFromJson(translated.storylinesToJsonArray(listOf(storyline)).toString())
        assertEquals(listOf(storyline), parsedLines!!.items)
    }

    @Test
    fun sceneEntryNumberParsing() {
        assertEquals(1, ProjectFormat.parseSceneNumber("scene-0001.json"))
        assertEquals(9999, ProjectFormat.parseSceneNumber("scene-9999.md"))
        assertNull(ProjectFormat.parseSceneNumber("scene-123.json"))
        assertNull(ProjectFormat.parseSceneNumber("chapter-0001.json"))
        assertNull(ProjectFormat.parseSceneNumber("scene-00001.json"))
    }
}
