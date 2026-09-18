package icejawriter.project

import icejawriter.model.Character
import icejawriter.model.ChronologyEvent
import icejawriter.model.GlossaryTerm
import icejawriter.model.Manifest
import icejawriter.model.NovelDescription
import icejawriter.model.RelationshipRef
import icejawriter.model.Rules
import icejawriter.model.SceneCard
import icejawriter.model.SceneStatus
import icejawriter.model.Storyline
import icejawriter.model.Style
import icejawriter.model.World
import java.nio.charset.StandardCharsets
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Result of parsing a JSON array of objects.
 *
 * @property items the successfully parsed elements (in archive order)
 * @property skippedElementIndices indices of elements that were not JSON
 *   objects and therefore had to be skipped; reported as validation issues so
 *   that a damaged element never silently disappears (ФРМ-21)
 */
data class ObjectListResult<T>(
    val items: List<T>,
    val skippedElementIndices: List<Int>,
)

/**
 * Converts the domain model to and from org.json trees — the only third-party
 * runtime library of the project (АРХ-02). Values are read leniently: every
 * missing or wrong-typed field falls back to its default, because one broken
 * field must never cause loss of the remaining data (ФРМ-21).
 */
object ProjectJsonCodec {

    /**
     * Parses a JSON object from raw text, or null when the text is not valid
     * JSON. A null result is reported by the caller as a validation issue.
     */
    fun jsonObjectOrNull(text: String): JSONObject? =
        try {
            JSONObject(text)
        } catch (e: JSONException) {
            null
        }

    /**
     * Parses a JSON array from raw text, or null when the text is not a valid
     * JSON array.
     */
    fun jsonArrayOrNull(text: String): JSONArray? =
        try {
            JSONArray(text)
        } catch (e: JSONException) {
            null
        }

    /**
     * Reads a string field; a missing field yields the empty string and a JSON
     * null is treated as absent too (org.json reports JSON null as the literal
     * string "null" through optString, which must be normalized away).
     */
    private fun optString(o: JSONObject, key: String): String {
        val value = o.opt(key) ?: return ""
        return if (value == JSONObject.NULL) "" else value.toString()
    }

    /** Reads a string array field; a missing or non-array field yields an empty list. */
    private fun stringListFromJson(o: JSONObject, key: String): List<String> {
        val array = o.optJSONArray(key) ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val value = array.opt(i)
                if (value != null && value != JSONObject.NULL) add(value.toString())
            }
        }
    }

    /** Serializes a string list into a JSON array. */
    private fun stringListToJsonArray(values: List<String>): JSONArray = JSONArray(values)

    /** Reads a year -> age map ("ageByYear") of a character (ФРМ-07). */
    private fun yearMapFromJson(o: JSONObject, key: String): Map<String, Int> {
        val nested = o.optJSONObject(key) ?: return emptyMap()
        val result = LinkedHashMap<String, Int>()
        val keys = nested.keys()
        while (keys.hasNext()) {
            val year = keys.next()
            val value = nested.opt(year)
            if (value is Number) result[year] = value.toInt()
        }
        return result
    }

    /** Serializes a year -> age map into a nested JSON object. */
    private fun yearMapToJson(map: Map<String, Int>): JSONObject {
        val result = JSONObject()
        for ((year, age) in map) result.put(year, age)
        return result
    }

    // ------------------------------------------------------------------
    // manifest.json (ФРМ-02, §4.3)
    // ------------------------------------------------------------------

    fun manifestToJson(manifest: Manifest): JSONObject = JSONObject()
        .put("formatVersion", manifest.formatVersion)
        .put("appVersion", manifest.appVersion)
        .put("title", manifest.title)
        .put("createdAt", manifest.createdAt)
        .put("updatedAt", manifest.updatedAt)

    /** Parses a manifest, or null when the text is not valid JSON. */
    fun manifestFromJson(text: String): Manifest? {
        val o = jsonObjectOrNull(text) ?: return null
        return Manifest(
            formatVersion = o.optInt("formatVersion", -1),
            appVersion = optString(o, "appVersion"),
            title = optString(o, "title"),
            createdAt = optString(o, "createdAt"),
            updatedAt = optString(o, "updatedAt"),
        )
    }

    // ------------------------------------------------------------------
    // novel.json (ФРМ-05, §4.4)
    // ------------------------------------------------------------------

    fun novelToJson(novel: NovelDescription): JSONObject = JSONObject()
        .put("title", novel.title)
        .put("author", novel.author)
        .put("logline", novel.logline)
        .put("theme", novel.theme)
        .put("plot", novel.plot)

    /** Parses the novel description, or null when the text is not valid JSON. */
    fun novelFromJson(text: String): NovelDescription? {
        val o = jsonObjectOrNull(text) ?: return null
        return NovelDescription(
            title = optString(o, "title"),
            author = optString(o, "author"),
            logline = optString(o, "logline"),
            theme = optString(o, "theme"),
            plot = optString(o, "plot"),
        )
    }

    /** Serialized novel.json exactly as the writer stores it (indented 2). */
    fun novelJsonString(novel: NovelDescription): String = novelToJson(novel).toString(2)

    /**
     * UTF-8 byte size of the serialized novel.json — the exact value the
     * editor shows as the "N / 2048 bytes" counter (ФРМ-05).
     */
    fun novelByteCount(novel: NovelDescription): Int =
        novelJsonString(novel).toByteArray(StandardCharsets.UTF_8).size

    // ------------------------------------------------------------------
    // bible/world.json (ФРМ-06)
    // ------------------------------------------------------------------

    fun worldToJson(world: World): JSONObject = JSONObject()
        .put("geography", world.geography)
        .put("politics", world.politics)
        .put("economy", world.economy)
        .put("techMagic", world.techMagic)
        .put("religion", world.religion)
        .put("languages", world.languages)
        .put("notes", world.notes)

    fun worldFromJson(text: String): World? {
        val o = jsonObjectOrNull(text) ?: return null
        return World(
            geography = optString(o, "geography"),
            politics = optString(o, "politics"),
            economy = optString(o, "economy"),
            techMagic = optString(o, "techMagic"),
            religion = optString(o, "religion"),
            languages = optString(o, "languages"),
            notes = optString(o, "notes"),
        )
    }

    // ------------------------------------------------------------------
    // bible/characters.json (ФРМ-07)
    // ------------------------------------------------------------------

    fun relationshipToJson(ref: RelationshipRef): JSONObject = JSONObject()
        .put("withId", ref.withId)
        .put("type", ref.type)
        .put("notes", ref.notes)

    fun relationshipFromJson(o: JSONObject): RelationshipRef = RelationshipRef(
        withId = optString(o, "withId"),
        type = optString(o, "type"),
        notes = optString(o, "notes"),
    )

    fun characterToJson(character: Character): JSONObject {
        val o = JSONObject()
            .put("id", character.id)
            .put("name", character.name)
            .put("role", character.role)
            .put("voice", character.voice)
            .put("weakness", character.weakness)
            .put("notes", character.notes)
        o.put("aliases", stringListToJsonArray(character.aliases))
        o.put("ageByYear", yearMapToJson(character.ageByYear))
        o.put("arcs", stringListToJsonArray(character.arcs))
        o.put("relationships", JSONArray(character.relationships.map { relationshipToJson(it) }))
        o.put("knows", stringListToJsonArray(character.knows))
        o.put("doesNotKnow", stringListToJsonArray(character.doesNotKnow))
        o.put("traumas", stringListToJsonArray(character.traumas))
        o.put("goals", stringListToJsonArray(character.goals))
        return o
    }

    fun characterFromJsonObject(o: JSONObject): Character = Character(
        id = optString(o, "id"),
        name = optString(o, "name"),
        aliases = stringListFromJson(o, "aliases"),
        role = optString(o, "role"),
        ageByYear = yearMapFromJson(o, "ageByYear"),
        arcs = stringListFromJson(o, "arcs"),
        relationships = (o.optJSONArray("relationships") ?: JSONArray()).let { array ->
            buildList {
                for (i in 0 until array.length()) {
                    val element = array.optJSONObject(i)
                    if (element != null) add(relationshipFromJson(element))
                }
            }
        },
        voice = optString(o, "voice"),
        knows = stringListFromJson(o, "knows"),
        doesNotKnow = stringListFromJson(o, "doesNotKnow"),
        traumas = stringListFromJson(o, "traumas"),
        goals = stringListFromJson(o, "goals"),
        weakness = optString(o, "weakness"),
        notes = optString(o, "notes"),
    )

    fun charactersToJsonArray(characters: List<Character>): JSONArray =
        JSONArray(characters.map { characterToJson(it) })

    /** Parses the characters array, or null when the text is not a JSON array. */
    fun charactersFromJson(text: String): ObjectListResult<Character>? =
        objectListOrNull(text) { characterFromJsonObject(it) }

    // ------------------------------------------------------------------
    // bible/chronology.json (ФРМ-08)
    // ------------------------------------------------------------------

    fun chronologyEventToJson(event: ChronologyEvent): JSONObject = JSONObject()
        .put("date", event.date)
        .put("event", event.event)
        .put("notes", event.notes)

    fun chronologyEventFromJsonObject(o: JSONObject): ChronologyEvent = ChronologyEvent(
        date = optString(o, "date"),
        event = optString(o, "event"),
        notes = optString(o, "notes"),
    )

    fun chronologyToJsonArray(events: List<ChronologyEvent>): JSONArray =
        JSONArray(events.map { chronologyEventToJson(it) })

    fun chronologyFromJson(text: String): ObjectListResult<ChronologyEvent>? =
        objectListOrNull(text) { chronologyEventFromJsonObject(it) }

    // ------------------------------------------------------------------
    // bible/rules.json (ФРМ-09)
    // ------------------------------------------------------------------

    fun rulesToJson(rules: Rules): JSONObject = JSONObject()
        .put("possible", rules.possible)
        .put("impossible", rules.impossible)
        .put("cost", rules.cost)
        .put("limits", rules.limits)
        .put("notes", rules.notes)

    fun rulesFromJson(text: String): Rules? {
        val o = jsonObjectOrNull(text) ?: return null
        return Rules(
            possible = optString(o, "possible"),
            impossible = optString(o, "impossible"),
            cost = optString(o, "cost"),
            limits = optString(o, "limits"),
            notes = optString(o, "notes"),
        )
    }

    // ------------------------------------------------------------------
    // bible/storylines.json (ФРМ-10)
    // ------------------------------------------------------------------

    fun storylineToJson(storyline: Storyline): JSONObject = JSONObject()
        .put("id", storyline.id)
        .put("name", storyline.name)
        .put("setup", storyline.setup)
        .put("resolution", storyline.resolution)
        .put("notes", storyline.notes)
        .put("secrets", stringListToJsonArray(storyline.secrets))
        .put("foreshadowing", stringListToJsonArray(storyline.foreshadowing))

    fun storylineFromJsonObject(o: JSONObject): Storyline = Storyline(
        id = optString(o, "id"),
        name = optString(o, "name"),
        setup = optString(o, "setup"),
        secrets = stringListFromJson(o, "secrets"),
        foreshadowing = stringListFromJson(o, "foreshadowing"),
        resolution = optString(o, "resolution"),
        notes = optString(o, "notes"),
    )

    fun storylinesToJsonArray(storylines: List<Storyline>): JSONArray =
        JSONArray(storylines.map { storylineToJson(it) })

    fun storylinesFromJson(text: String): ObjectListResult<Storyline>? =
        objectListOrNull(text) { storylineFromJsonObject(it) }

    // ------------------------------------------------------------------
    // bible/glossary.json (ФРМ-11)
    // ------------------------------------------------------------------

    fun glossaryTermToJson(term: GlossaryTerm): JSONObject = JSONObject()
        .put("term", term.term)
        .put("definition", term.definition)

    fun glossaryTermFromJsonObject(o: JSONObject): GlossaryTerm = GlossaryTerm(
        term = optString(o, "term"),
        definition = optString(o, "definition"),
    )

    fun glossaryToJsonArray(terms: List<GlossaryTerm>): JSONArray =
        JSONArray(terms.map { glossaryTermToJson(it) })

    fun glossaryFromJson(text: String): ObjectListResult<GlossaryTerm>? =
        objectListOrNull(text) { glossaryTermFromJsonObject(it) }

    // ------------------------------------------------------------------
    // bible/style.json (ФРМ-12)
    // ------------------------------------------------------------------

    fun styleToJson(style: Style): JSONObject = JSONObject()
        .put("pov", style.pov)
        .put("tense", style.tense)
        .put("tone", style.tone)
        .put("notes", style.notes)
        .put("forbiddenWords", stringListToJsonArray(style.forbiddenWords))
        .put("typicalPhrases", stringListToJsonArray(style.typicalPhrases))

    fun styleFromJson(text: String): Style? {
        val o = jsonObjectOrNull(text) ?: return null
        return Style(
            pov = optString(o, "pov"),
            tense = optString(o, "tense"),
            tone = optString(o, "tone"),
            forbiddenWords = stringListFromJson(o, "forbiddenWords"),
            typicalPhrases = stringListFromJson(o, "typicalPhrases"),
            notes = optString(o, "notes"),
        )
    }

    // ------------------------------------------------------------------
    // scenes/scene-NNNN.json (ФРМ-13)
    // ------------------------------------------------------------------

    fun sceneCardToJson(card: SceneCard): JSONObject = JSONObject()
        .put("id", card.id)
        .put("number", card.number)
        .put("act", card.act)
        .put("chapter", card.chapter)
        .put("title", card.title)
        .put("status", card.status.wireName)
        .put("pov", card.pov)
        .put("location", card.location)
        .put("time", card.time)
        .put("goal", card.goal)
        .put("conflict", card.conflict)
        .put("twist", card.twist)
        .put("readerLearns", card.readerLearns)
        .put("charactersLearn", card.charactersLearn)
        .put("entry", card.entry)
        .put("exit", card.exit)
        .put("summary", card.summary)
        .put("wordTargetMin", card.wordTargetMin)
        .put("wordTargetMax", card.wordTargetMax)
        .put("wordCount", card.wordCount)
        .put("updatedAt", card.updatedAt)
        .put("continuityNotes", stringListToJsonArray(card.continuityNotes))

    fun sceneCardFromJson(text: String): SceneCard? {
        val o = jsonObjectOrNull(text) ?: return null
        return SceneCard(
            id = optString(o, "id"),
            number = o.optInt("number", 0),
            act = o.optInt("act", 1),
            chapter = o.optInt("chapter", 1),
            title = optString(o, "title"),
            status = SceneStatus.parse(optString(o, "status")),
            pov = optString(o, "pov"),
            location = optString(o, "location"),
            time = optString(o, "time"),
            goal = optString(o, "goal"),
            conflict = optString(o, "conflict"),
            twist = optString(o, "twist"),
            readerLearns = optString(o, "readerLearns"),
            charactersLearn = optString(o, "charactersLearn"),
            entry = optString(o, "entry"),
            exit = optString(o, "exit"),
            continuityNotes = stringListFromJson(o, "continuityNotes"),
            summary = optString(o, "summary"),
            wordTargetMin = o.optInt("wordTargetMin", ProjectFormat.DEFAULT_WORD_TARGET_MIN),
            wordTargetMax = o.optInt("wordTargetMax", ProjectFormat.DEFAULT_WORD_TARGET_MAX),
            wordCount = o.optInt("wordCount", 0),
            updatedAt = optString(o, "updatedAt"),
        )
    }

    // ------------------------------------------------------------------
    // shared helper: array of objects
    // ------------------------------------------------------------------

    /**
     * Parses a JSON array whose elements should be objects. Elements that are
     * not objects are skipped and their indices recorded in
     * [ObjectListResult.skippedElementIndices] so the caller can surface them
     * as validation issues (ФРМ-21).
     */
    private fun <T> objectListOrNull(text: String, elementParser: (JSONObject) -> T): ObjectListResult<T>? {
        val array = jsonArrayOrNull(text) ?: return null
        val items = mutableListOf<T>()
        val skipped = mutableListOf<Int>()
        for (i in 0 until array.length()) {
            val element = array.optJSONObject(i)
            if (element == null) {
                skipped += i
            } else {
                items += elementParser(element)
            }
        }
        return ObjectListResult(items = items, skippedElementIndices = skipped)
    }
}
