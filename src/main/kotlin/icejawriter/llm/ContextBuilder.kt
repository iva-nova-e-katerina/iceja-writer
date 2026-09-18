package icejawriter.llm

import icejawriter.model.Character
import icejawriter.model.ProjectDocument
import icejawriter.model.Scene
import icejawriter.model.SceneStructure

/**
 * Curated context builder of the generation pipeline (LLM-10, LLM-11).
 *
 * Instead of sending the whole novel, the builder assembles only: a bible
 * slice, the previous scene summary, the current scene card and the style —
 * and trims it to the configured token budget by priority: card → style →
 * previous summary → bible slice.
 */
object ContextBuilder {

    /** Characters of each scene text sent to the continuity check. */
    private const val SCENE_EXCERPT_LIMIT = 4000

    /** Rough token estimate: ~4 characters per token for Russian/English. */
    fun estimateTokens(text: String): Int = text.length / 4 + 1

    /**
     * Builds the context for the given scene, honoring [tokenLimit].
     *
     * @param language "ru" or "en" — selects the section labels
     */
    fun build(document: ProjectDocument, sceneId: String, tokenLimit: Int, language: String = "ru"): String {
        val scene = document.scenes.firstOrNull { it.card.id == sceneId } ?: return ""
        val ordered = SceneStructure.ordered(document.scenes)
        val index = ordered.indexOfFirst { it.card.id == sceneId }
        val previous = if (index > 0) ordered[index - 1] else null

        val labels = labels(language)
        val cardText = cardSection(scene, labels)
        val styleText = styleSection(document, labels)
        val previousText = previousSummarySection(previous, labels)
        val bibleText = bibleSection(document, scene, labels)

        // Mandatory: the card. Optional sections are added by priority while
        // the estimated size stays within the limit (LLM-11).
        val included = mutableListOf(cardText)
        var used = estimateTokens(cardText)
        for (section in listOf(styleText, previousText, bibleText)) {
            if (section.isBlank()) continue
            val cost = estimateTokens(section)
            if (used + cost > tokenLimit) continue
            included += section
            used += cost
        }

        // Prompt order: bible, previous summary, card, style.
        val orderedSections = listOf(bibleText, previousText, cardText, styleText)
        return orderedSections.filter { it.isNotBlank() && it in included }.joinToString("\n\n")
    }

    /**
     * Context for the continuity checks (LLM-40, LLM-41): the bible facts
     * followed by excerpts of the scenes in scope, trimmed to the token budget
     * from the end so that the earliest scenes always make it into the prompt.
     */
    fun checksContext(document: ProjectDocument, sceneIds: Set<String>, tokenLimit: Int, language: String = "ru"): String {
        val labels = labels(language)
        val scenes = SceneStructure.ordered(document.scenes).filter { it.card.id in sceneIds }
        val reference = scenes.firstOrNull() ?: document.scenes.firstOrNull()
        val builder = StringBuilder()
        if (reference != null) {
            builder.append(bibleSection(document, reference, labels))
        }
        var used = estimateTokens(builder.toString())
        for (scene in scenes) {
            val excerpt = buildString {
                appendLine()
                appendLine("${labels.getValue("card")} №${scene.card.number}: ${scene.card.title}")
                appendLine(scene.text.take(SCENE_EXCERPT_LIMIT))
            }
            val cost = estimateTokens(excerpt)
            if (used + cost > tokenLimit) break
            builder.append(excerpt)
            used += cost
        }
        return builder.toString().trim()
    }

    private fun cardSection(scene: Scene, labels: Map<String, String>): String {        val card = scene.card
        return buildString {
            appendLine(labels.getValue("card"))
            appendLine("${labels.getValue("number")}: ${card.number}")
            appendLine("${labels.getValue("act")}: ${card.act}")
            appendLine("${labels.getValue("chapter")}: ${card.chapter}")
            appendLine("${labels.getValue("title")}: ${card.title}")
            appendLine("${labels.getValue("pov")}: ${card.pov}")
            appendLine("${labels.getValue("location")}: ${card.location}")
            appendLine("${labels.getValue("time")}: ${card.time}")
            appendLine("${labels.getValue("goal")}: ${card.goal}")
            appendLine("${labels.getValue("conflict")}: ${card.conflict}")
            appendLine("${labels.getValue("twist")}: ${card.twist}")
            appendLine("${labels.getValue("readerLearns")}: ${card.readerLearns}")
            appendLine("${labels.getValue("charactersLearn")}: ${card.charactersLearn}")
            appendLine("${labels.getValue("entry")}: ${card.entry}")
            appendLine("${labels.getValue("exit")}: ${card.exit}")
            if (card.continuityNotes.isNotEmpty()) {
                appendLine("${labels.getValue("continuityNotes")}: " + card.continuityNotes.joinToString("; "))
            }
            appendLine("${labels.getValue("wordTarget")}: ${card.wordTargetMin}-${card.wordTargetMax}")
        }.trim()
    }

    private fun styleSection(document: ProjectDocument, labels: Map<String, String>): String {
        val style = document.bible.style
        val text = buildString {
            appendLine(labels.getValue("style"))
            if (style.pov.isNotBlank()) appendLine("POV: ${style.pov}")
            if (style.tense.isNotBlank()) appendLine("${labels.getValue("tense")}: ${style.tense}")
            if (style.tone.isNotBlank()) appendLine("${labels.getValue("tone")}: ${style.tone}")
            if (style.forbiddenWords.isNotEmpty()) appendLine("${labels.getValue("forbidden")}: " + style.forbiddenWords.joinToString(", "))
            if (style.typicalPhrases.isNotEmpty()) appendLine("${labels.getValue("phrases")}: " + style.typicalPhrases.joinToString(", "))
            if (style.notes.isNotBlank()) appendLine(style.notes)
        }.trim()
        return if (text == labels.getValue("style")) "" else text
    }

    private fun previousSummarySection(previous: Scene?, labels: Map<String, String>): String {
        if (previous == null || previous.card.summary.isBlank()) return ""
        return labels.getValue("previous") + ":\n" + previous.card.summary.trim()
    }

    private fun bibleSection(document: ProjectDocument, scene: Scene, labels: Map<String, String>): String {
        val bible = document.bible
        return buildString {
            appendLine(labels.getValue("bible"))
            if (bible.world.geography.isNotBlank() || bible.world.notes.isNotBlank()) {
                appendLine("${labels.getValue("world")}: " + listOf(bible.world.geography, bible.world.politics, bible.world.notes)
                    .filter { it.isNotBlank() }.joinToString(" "))
            }
            if (bible.rules.possible.isNotBlank() || bible.rules.impossible.isNotBlank()) {
                appendLine("${labels.getValue("rules")}: " + listOf(bible.rules.possible, bible.rules.impossible, bible.rules.cost, bible.rules.limits)
                    .filter { it.isNotBlank() }.joinToString(" "))
            }
            val relevant = relevantCharacters(bible.characters, scene)
            if (relevant.isNotEmpty()) {
                appendLine(labels.getValue("characters") + ":")
                relevant.forEach { character -> appendLine("  " + characterLine(character)) }
            }
            if (bible.chronology.isNotEmpty()) {
                appendLine(labels.getValue("chronology") + ": " + bible.chronology.joinToString("; ") { "${it.date}: ${it.event}" })
            }
        }.trim()
    }

    /**
     * Characters relevant to the scene: its POV character plus every character
     * mentioned in the card fields. When nothing is mentioned all characters
     * are included so the model still sees the cast (LLM-10).
     */
    private fun relevantCharacters(characters: List<Character>, scene: Scene): List<Character> {
        if (characters.isEmpty()) return emptyList()
        val cardText = listOf(
            scene.card.title, scene.card.goal, scene.card.conflict, scene.card.twist,
            scene.card.readerLearns, scene.card.charactersLearn, scene.card.entry, scene.card.exit,
            scene.card.summary, scene.text,
        ).joinToString(" ")
        val mentioned = characters.filter { character ->
            (character.id.isNotBlank() && cardText.contains(character.id)) ||
                (character.name.isNotBlank() && cardText.contains(character.name))
        }
        val pov = characters.filter { it.id == scene.card.pov }
        val result = (pov + mentioned).distinctBy { it.id }
        return result.ifEmpty { characters }
    }

    private fun characterLine(character: Character): String = buildString {
        append(character.id)
        if (character.name.isNotBlank()) append(" (").append(character.name).append(')')
        if (character.role.isNotBlank()) append(" — ").append(character.role)
        if (character.voice.isNotBlank()) append("; ").append(character.voice)
    }

    /** Section labels per UI language. */
    private fun labels(language: String): Map<String, String> = if (language == "en") EN else RU

    private val RU = mapOf(
        "card" to "Карточка сцены",
        "number" to "Номер",
        "act" to "Акт",
        "chapter" to "Глава",
        "title" to "Название",
        "pov" to "POV",
        "location" to "Место",
        "time" to "Время",
        "goal" to "Цель",
        "conflict" to "Конфликт",
        "twist" to "Поворот",
        "readerLearns" to "Читатель узнаёт",
        "charactersLearn" to "Персонажи узнают",
        "entry" to "Вход",
        "exit" to "Выход",
        "continuityNotes" to "Заметки непрерывности",
        "wordTarget" to "Целевой объём, слов",
        "style" to "Стиль",
        "tense" to "Время повествования",
        "tone" to "Тон",
        "forbidden" to "Запрещённые слова",
        "phrases" to "Характерные фразы",
        "previous" to "Резюме предыдущей сцены",
        "bible" to "Библия романа",
        "world" to "Мир",
        "rules" to "Правила",
        "characters" to "Персонажи",
        "chronology" to "Хронология",
    )

    private val EN = mapOf(
        "card" to "Scene card",
        "number" to "Number",
        "act" to "Act",
        "chapter" to "Chapter",
        "title" to "Title",
        "pov" to "POV",
        "location" to "Location",
        "time" to "Time",
        "goal" to "Goal",
        "conflict" to "Conflict",
        "twist" to "Twist",
        "readerLearns" to "Reader learns",
        "charactersLearn" to "Characters learn",
        "entry" to "Entry",
        "exit" to "Exit",
        "continuityNotes" to "Continuity notes",
        "wordTarget" to "Target length, words",
        "style" to "Style",
        "tense" to "Tense",
        "tone" to "Tone",
        "forbidden" to "Forbidden words",
        "phrases" to "Typical phrases",
        "previous" to "Previous scene summary",
        "bible" to "Novel bible",
        "world" to "World",
        "rules" to "Rules",
        "characters" to "Characters",
        "chronology" to "Chronology",
    )
}