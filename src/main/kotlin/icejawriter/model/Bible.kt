package icejawriter.model

/**
 * The world section of the novel bible, stored in `bible/world.json` (ФРМ-06).
 */
data class World(
    val geography: String = "",
    val politics: String = "",
    val economy: String = "",
    val techMagic: String = "",
    val religion: String = "",
    val languages: String = "",
    val notes: String = "",
)

/**
 * A relationship link from one character to another, stored inside a character
 * card (ФРМ-07). The referenced character must exist in the bible (ФРМ-20).
 */
data class RelationshipRef(
    val withId: String = "",
    val type: String = "",
    val notes: String = "",
)

/**
 * A character card of the bible, stored in `bible/characters.json` (ФРМ-07).
 *
 * @property ageByYear age of the character in given years (year -> age), e.g.
 *   {"1918": 25, "1945": 52}; keys are years, values are ages.
 */
data class Character(
    val id: String = "",
    val name: String = "",
    val aliases: List<String> = emptyList(),
    val role: String = "",
    val ageByYear: Map<String, Int> = emptyMap(),
    val arcs: List<String> = emptyList(),
    val relationships: List<RelationshipRef> = emptyList(),
    val voice: String = "",
    val knows: List<String> = emptyList(),
    val doesNotKnow: List<String> = emptyList(),
    val traumas: List<String> = emptyList(),
    val goals: List<String> = emptyList(),
    val weakness: String = "",
    val notes: String = "",
)

/**
 * A single event of the novel timeline, stored in `bible/chronology.json`
 * (ФРМ-08). Events are displayed sorted by [date].
 */
data class ChronologyEvent(
    val date: String = "",
    val event: String = "",
    val notes: String = "",
)

/**
 * Rules of the world, stored in `bible/rules.json` (ФРМ-09).
 */
data class Rules(
    val possible: String = "",
    val impossible: String = "",
    val cost: String = "",
    val limits: String = "",
    val notes: String = "",
)

/**
 * A plot line A/B/C of the novel, stored in `bible/storylines.json` (ФРМ-10).
 */
data class Storyline(
    val id: String = "",
    val name: String = "",
    val setup: String = "",
    val secrets: List<String> = emptyList(),
    val foreshadowing: List<String> = emptyList(),
    val resolution: String = "",
    val notes: String = "",
)

/**
 * A glossary term of the bible, stored in `bible/glossary.json` (ФРМ-11).
 */
data class GlossaryTerm(
    val term: String = "",
    val definition: String = "",
)

/**
 * The style description of the narration, stored in `bible/style.json`
 * (ФРМ-12).
 */
data class Style(
    val pov: String = "",
    val tense: String = "",
    val tone: String = "",
    val forbiddenWords: List<String> = emptyList(),
    val typicalPhrases: List<String> = emptyList(),
    val notes: String = "",
)

/**
 * The whole novel bible — seven sections, each stored in its own JSON file
 * inside the `bible/` directory of the .ijw archive (ФРМ-06..ФРМ-12).
 */
data class Bible(
    val world: World = World(),
    val characters: List<Character> = emptyList(),
    val chronology: List<ChronologyEvent> = emptyList(),
    val rules: Rules = Rules(),
    val storylines: List<Storyline> = emptyList(),
    val glossary: List<GlossaryTerm> = emptyList(),
    val style: Style = Style(),
)
