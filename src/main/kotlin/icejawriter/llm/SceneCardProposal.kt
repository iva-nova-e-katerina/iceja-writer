package icejawriter.llm

import icejawriter.project.ProjectFormat
import org.json.JSONArray
import org.json.JSONObject

/**
 * A scene card proposed by the model in the "AI writes the cards" mode. The
 * proposal is always shown to the author for confirmation before it is applied
 * (the canon rule of the methodology).
 */
data class SceneCardProposal(
    val title: String = "",
    val pov: String = "",
    val location: String = "",
    val time: String = "",
    val goal: String = "",
    val conflict: String = "",
    val twist: String = "",
    val readerLearns: String = "",
    val charactersLearn: String = "",
    val entry: String = "",
    val exit: String = "",
    val continuityNotes: List<String> = emptyList(),
    val summary: String = "",
    val wordTargetMin: Int = ProjectFormat.DEFAULT_WORD_TARGET_MIN,
    val wordTargetMax: Int = ProjectFormat.DEFAULT_WORD_TARGET_MAX,
    val act: Int? = null,
    val chapter: Int? = null,
)

/**
 * Parses a scene card proposal from free model output (JSON object with the
 * card fields). Returns null when the answer contains no recognizable card.
 *
 * The model sometimes prints the empty JSON template first and only then the
 * filled card, so ALL objects are scored and the one with the most filled
 * fields wins; a completely empty template is rejected (regression: empty
 * cards used to be applied silently).
 */
object SceneCardProposalParser {

    /** Minimum number of filled fields for a proposal to be accepted. */
    private const val MIN_SCORE = 2

    private val CARD_KEYS = listOf("goal", "conflict", "twist", "entry", "exit", "readerLearns")

    fun parse(answer: String): SceneCardProposal? {
        var best: SceneCardProposal? = null
        var bestScore = 0
        for (candidate in JsonObjectExtraction.extractAll(answer)) {
            if (CARD_KEYS.none { candidate.text.contains(it) }) continue
            val root = try {
                JSONObject(candidate.text)
            } catch (e: Exception) {
                continue
            }
            val proposal = toProposal(root)
            val score = score(proposal)
            if (score > bestScore) {
                bestScore = score
                best = proposal
            }
        }
        return if (bestScore >= MIN_SCORE) best else null
    }

    /** Number of fields that actually carry content. */
    private fun score(proposal: SceneCardProposal): Int =
        listOf(
            proposal.title,
            proposal.pov,
            proposal.location,
            proposal.time,
            proposal.goal,
            proposal.conflict,
            proposal.twist,
            proposal.readerLearns,
            proposal.charactersLearn,
            proposal.entry,
            proposal.exit,
            proposal.summary,
        ).count { it.isNotBlank() } + if (proposal.continuityNotes.isNotEmpty()) 1 else 0

    private fun toProposal(root: JSONObject): SceneCardProposal {
        val min = root.optInt("wordTargetMin", ProjectFormat.DEFAULT_WORD_TARGET_MIN)
        val max = root.optInt("wordTargetMax", ProjectFormat.DEFAULT_WORD_TARGET_MAX)
        return SceneCardProposal(
            title = JsonObjectExtraction.stringOrEmpty(root, "title"),
            pov = JsonObjectExtraction.stringOrEmpty(root, "pov"),
            location = JsonObjectExtraction.stringOrEmpty(root, "location"),
            time = JsonObjectExtraction.stringOrEmpty(root, "time"),
            goal = JsonObjectExtraction.stringOrEmpty(root, "goal"),
            conflict = JsonObjectExtraction.stringOrEmpty(root, "conflict"),
            twist = JsonObjectExtraction.stringOrEmpty(root, "twist"),
            readerLearns = JsonObjectExtraction.stringOrEmpty(root, "readerLearns"),
            charactersLearn = JsonObjectExtraction.stringOrEmpty(root, "charactersLearn"),
            entry = JsonObjectExtraction.stringOrEmpty(root, "entry"),
            exit = JsonObjectExtraction.stringOrEmpty(root, "exit"),
            continuityNotes = stringList(root, "continuityNotes"),
            summary = JsonObjectExtraction.stringOrEmpty(root, "summary"),
            wordTargetMin = minOf(min, max).coerceAtLeast(1),
            wordTargetMax = maxOf(min, max).coerceAtLeast(1),
            act = positiveOrNull(root, "act"),
            chapter = positiveOrNull(root, "chapter"),
        )
    }

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

    private fun positiveOrNull(root: JSONObject, key: String): Int? {
        val value = root.opt(key)
        val number = when (value) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        } ?: return null
        return if (number > 0) number else null
    }
}