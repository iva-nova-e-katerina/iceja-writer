package icejawriter.llm

import icejawriter.config.LlmSettings
import icejawriter.log.AppLog
import icejawriter.model.ProjectDocument
import icejawriter.model.SceneStructure
import org.json.JSONArray
import org.json.JSONObject

/**
 * One problem found by the continuity check (LLM-41): an optional scene number
 * and the description of the problem. The text is never rewritten.
 */
data class CheckIssue(
    val sceneNumber: Int?,
    val problem: String,
)

/**
 * High-level LLM operations of the generation pipeline (LLM-20..LLM-23,
 * LLM-40..LLM-42, LLM-50..LLM-51). Blocking; the UI runs these methods on a
 * SwingWorker so the interface stays responsive (НФР-02, ИНТ-40).
 */
class LlmService(
    private val settings: LlmSettings,
    private val language: String,
) {

    private val client = LlmClient(settings)

    /** True when the LLM settings are complete enough to send requests. */
    fun isConfigured(): Boolean = client.isConfigured()

    /** Loads the provider model list (LLM-03). */
    fun listModels(): List<String> = client.listModels()

    /** Control request for the "Check connection" button (LLM-05). */
    fun checkConnection(): Int = listModels().size

    /**
     * Generates a scene draft with the change protocol (LLM-20, LLM-30). Up to
     * two automatic retries are performed for network/timeout errors (LLM-21).
     */
    fun generateDraft(document: ProjectDocument, sceneId: String): DraftResult {
        val scene = document.scenes.firstOrNull { it.card.id == sceneId }
            ?: throw LlmException(LlmErrorReason.CONFIG, "The scene does not exist.")
        val context = ContextBuilder.build(document, sceneId, settings.contextTokenLimit, language)
        val prompt = PromptTemplates.fill(
            PromptTemplates.draft(language),
            mapOf(
                "context" to context,
                "minWords" to scene.card.wordTargetMin.toString(),
                "maxWords" to scene.card.wordTargetMax.toString(),
            ),
        )
        val answer = chatWithRetries(SYSTEM_PROMPT, prompt)
        return ChangeProtocolParser.parse(answer)
    }

    /**
     * Generates a 2–5 sentence summary of the scene (LLM-50). The caller stores
     * it into the card after the scene is approved.
     */
    fun summarizeScene(document: ProjectDocument, sceneId: String): String {
        val context = ContextBuilder.build(document, sceneId, settings.contextTokenLimit, language)
        val prompt = PromptTemplates.fill(PromptTemplates.summary(language), mapOf("context" to context))
        return chatWithRetries(SYSTEM_PROMPT, prompt).trim()
    }

    /**
     * Generates a scene card proposal in the "AI writes the cards" mode. The
     * model sees everything written before (bible + summaries + last scene
     * text) and proposes the next scene; the author confirms the proposal
     * before it is applied.
     */
    fun generateCard(document: ProjectDocument, sceneId: String): SceneCardProposal {
        val scene = document.scenes.firstOrNull { it.card.id == sceneId }
            ?: throw LlmException(LlmErrorReason.CONFIG, "The scene does not exist.")
        val context = ContextBuilder.build(document, sceneId, settings.contextTokenLimit, language)
        val prompt = PromptTemplates.fill(
            PromptTemplates.card(language),
            mapOf(
                "context" to context,
                "number" to scene.card.number.toString(),
                "act" to scene.card.act.toString(),
                "chapter" to scene.card.chapter.toString(),
            ),
        )
        val answer = chatWithRetries(SYSTEM_PROMPT, prompt)
        val proposal = SceneCardProposalParser.parse(answer)
        if (proposal == null) {
            // Log a preview of the model answer (model output, never the prompt)
            // so an unexpected format can be diagnosed from the journal.
            AppLog.warn("Scene card answer contained no usable card: " + preview(answer))
            throw LlmException(
                LlmErrorReason.PARSE,
                "The model returned no scene card; an answer preview is in logs/app.log.",
            )
        }
        AppLog.info("Scene card proposal: title=\"${proposal.title}\", goal=\"${proposal.goal}\"")
        return proposal
    }

    /** Short single-line preview of model output for the journal. */
    private fun preview(text: String): String =
        text.replace('\n', ' ').replace('\r', ' ').take(1200)

    /**
     * Runs a continuity / logic / chronology / knowledge / style / plot-holes
     * check over the given scenes (LLM-40, LLM-41) and returns only the list of
     * problems with scene references.
     */
    fun runChecks(
        document: ProjectDocument,
        sceneIds: Set<String>,
        checkTypeKey: String,
        scopeKey: String,
    ): List<CheckIssue> {
        if (sceneIds.isEmpty()) return emptyList()
        val context = ContextBuilder.checksContext(document, sceneIds, settings.contextTokenLimit, language)
        val prompt = PromptTemplates.fill(
            PromptTemplates.checks(language),
            mapOf(
                "context" to context,
                "checkType" to checkTypeKey,
                "scope" to scopeKey,
            ),
        )
        val answer = chatWithRetries(SYSTEM_PROMPT, prompt)
        return parseCheckIssues(answer)
    }

    /** Scene ids of a scope: "novel", "act:<n>" or "chapter:<act>:<chapter>". */
    fun scenesInScope(document: ProjectDocument, scope: String): Set<String> {
        val ordered = SceneStructure.ordered(document.scenes)
        val selected = when {
            scope == SCOPE_NOVEL -> ordered
            scope.startsWith("act:") -> {
                val act = scope.removePrefix("act:").toIntOrNull()
                ordered.filter { it.card.act == act }
            }
            scope.startsWith("chapter:") -> {
                val parts = scope.removePrefix("chapter:").split(':')
                val act = parts.getOrNull(0)?.toIntOrNull()
                val chapter = parts.getOrNull(1)?.toIntOrNull()
                ordered.filter { it.card.act == act && it.card.chapter == chapter }
            }
            else -> ordered
        }
        return selected.map { it.card.id }.toSet()
    }

    /**
     * Parses the checks answer: a JSON array of `{"scene": n, "problem": "..."}`.
     * A model that returns plain lines still yields issues (one per line).
     */
    fun parseCheckIssues(answer: String): List<CheckIssue> {
        val arrayStart = answer.indexOf('[')
        val arrayEnd = answer.lastIndexOf(']')
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            val candidate = answer.substring(arrayStart, arrayEnd + 1)
            try {
                val array = JSONArray(candidate)
                val issues = mutableListOf<CheckIssue>()
                for (i in 0 until array.length()) {
                    val element = array.optJSONObject(i) ?: continue
                    val problem = element.optString("problem", "").trim()
                    if (problem.isEmpty()) continue
                    val scene = element.opt("scene")
                    val sceneNumber = when (scene) {
                        is Number -> scene.toInt()
                        is String -> scene.toIntOrNull()
                        else -> null
                    }
                    issues += CheckIssue(sceneNumber = sceneNumber, problem = problem)
                }
                return issues
            } catch (_: Exception) {
                // Fall through to the plain-text parser.
            }
        }
        return answer.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it != "[]" }
            .map { CheckIssue(sceneNumber = null, problem = it.removePrefix("- ").trim()) }
    }

    /**
     * Sends the request with up to [MAX_RETRIES] automatic retries on network
     * and timeout failures (LLM-21).
     */
    private fun chatWithRetries(systemPrompt: String, userPrompt: String): String {
        var lastError: LlmException? = null
        repeat(MAX_RETRIES + 1) {
            try {
                return client.chat(systemPrompt, userPrompt)
            } catch (e: LlmException) {
                lastError = e
                // A cancelled request must not be retried: the user asked to stop.
                if (e.reason == LlmErrorReason.CANCELLED) throw e
                if (e.reason != LlmErrorReason.NETWORK && e.reason != LlmErrorReason.TIMEOUT) throw e
            }
        }
        val failure = lastError ?: LlmException(LlmErrorReason.NETWORK, "The request failed.")
        AppLog.error("LLM chat failed after ${MAX_RETRIES + 1} attempts: ${failure.reason} (${failure.detail})")
        throw failure
    }

    companion object {
        /** Automatic retries for network errors (LLM-21). */
        const val MAX_RETRIES = 2

        const val SCOPE_NOVEL = "novel"

        private const val SYSTEM_PROMPT = "You are a professional fiction-writing assistant. Follow the user's format instructions exactly."
    }
}