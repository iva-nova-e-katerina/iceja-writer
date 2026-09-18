package icejawriter.llm

import org.json.JSONObject

/**
 * Structured change protocol returned by the model together with a draft
 * (LLM-30): new facts, state changes, open questions and possible
 * contradictions. Nothing here is ever applied to the bible automatically —
 * the author confirms every item (LLM-32).
 */
data class ChangeProtocol(
    val newFacts: List<String> = emptyList(),
    val stateChanges: List<String> = emptyList(),
    val openQuestions: List<String> = emptyList(),
    val contradictions: List<String> = emptyList(),
) {
    /** True when the model returned no protocol items at all. */
    val isEmpty: Boolean
        get() = newFacts.isEmpty() && stateChanges.isEmpty() && openQuestions.isEmpty() && contradictions.isEmpty()
}

/**
 * A generated draft plus its change protocol (LLM-22).
 */
data class DraftResult(
    val draft: String,
    val protocol: ChangeProtocol,
)

/**
 * Parses the model answer into the draft text and the JSON change protocol
 * (LLM-30). The parser is tolerant: when no protocol JSON is found the whole
 * answer is treated as the draft and an empty protocol is returned, so a
 * model formatting slip never loses the generated text.
 */
object ChangeProtocolParser {

    fun parse(answer: String): DraftResult {
        // The model may print the empty protocol template first; pick the
        // object with the most filled items instead of the first one.
        var bestProtocol: ChangeProtocol? = null
        var bestStart = -1
        var bestScore = -1
        for (candidate in JsonObjectExtraction.extractAll(answer)) {
            if (PROTOCOL_KEYS.none { candidate.text.contains(it) }) continue
            val root = try {
                JSONObject(candidate.text)
            } catch (e: Exception) {
                continue
            }
            val protocol = parseProtocol(root)
            val score = protocol.newFacts.size + protocol.stateChanges.size +
                protocol.openQuestions.size + protocol.contradictions.size
            if (score > bestScore) {
                bestScore = score
                bestProtocol = protocol
                bestStart = candidate.startIndex
            }
        }
        if (bestProtocol == null) return DraftResult(answer.trim(), ChangeProtocol())
        var draft = answer.substring(0, bestStart)
        // Remove protocol templates the model printed before the real one, so
        // the draft never contains service JSON.
        val templates = JsonObjectExtraction.extractAll(draft)
            .filter { candidate -> PROTOCOL_KEYS.any { candidate.text.contains(it) } }
        for (template in templates.reversed()) {
            val end = template.startIndex + template.text.length
            if (end <= draft.length) draft = draft.removeRange(template.startIndex, end)
        }
        draft = draft.replace(JSON_FENCE_REGEX, "").trim()
        return DraftResult(draft, bestProtocol)
    }

    /** Parses a protocol object directly (used by tests and by checks). */
    fun parseProtocol(root: JSONObject): ChangeProtocol = ChangeProtocol(
        newFacts = stringList(root, "newFacts"),
        stateChanges = stringList(root, "stateChanges"),
        openQuestions = stringList(root, "openQuestions"),
        contradictions = stringList(root, "contradictions"),
    )

    private fun stringList(root: JSONObject, key: String): List<String> {
        val array = root.optJSONArray(key) ?: return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val value = array.opt(i)
                if (value != null && value != JSONObject.NULL) {
                    val text = value.toString().trim()
                    if (text.isNotEmpty()) add(text)
                }
            }
        }
    }

    private val PROTOCOL_KEYS = listOf("newFacts", "stateChanges", "openQuestions", "contradictions")
    private val JSON_FENCE_REGEX = Regex("```[a-zA-Z]*")
}