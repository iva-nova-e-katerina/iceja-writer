package icejawriter.llm

import icejawriter.config.LlmProviderType
import icejawriter.config.LlmSettings
import icejawriter.log.AppLog
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import org.json.JSONArray
import org.json.JSONObject

/**
 * Machine-readable reason of an LLM failure; the UI turns it into a localized
 * message (ИНТ-31).
 */
enum class LlmErrorReason {
    /** API key missing or rejected. */
    AUTH,

    /** Network is unreachable or the host refused the connection. */
    NETWORK,

    /** The request exceeded the configured timeout. */
    TIMEOUT,

    /** The provider returned an unexpected HTTP status. */
    HTTP,

    /** The provider answered but produced no usable text (empty/truncated). */
    EMPTY,

    /** The request was cancelled by the user. */
    CANCELLED,

    /** The provider response could not be parsed. */
    PARSE,

    /** The settings are incomplete (no key, no base URL, no model). */
    CONFIG,
}

/** Raised for every LLM operation failure with a localizable reason. */
class LlmException(
    val reason: LlmErrorReason,
    val detail: String = "",
    cause: Throwable? = null,
) : Exception(detail.ifBlank { reason.name }, cause)
/**
 * Parses a provider model list. Both OpenAI and Anthropic answer with
 * `{"data": [{"id": "..."}, ...]}` (LLM-03); the parser is deliberately
 * tolerant and returns a sorted distinct list.
 */
object ModelListParser {

    fun parse(json: String): List<String> {
        val root = try {
            JSONObject(json)
        } catch (e: Exception) {
            throw LlmException(LlmErrorReason.PARSE, "The model list is not valid JSON.", e)
        }
        val data = root.optJSONArray("data") ?: return emptyList()
        val ids = mutableListOf<String>()
        for (i in 0 until data.length()) {
            val element = data.optJSONObject(i) ?: continue
            val id = element.optString("id", "")
            if (id.isNotBlank()) ids += id
        }
        return ids.distinct().sorted()
    }
}

/**
 * HTTP client of the LLM subsystem (LLM-01, LLM-03, LLM-05). Speaks the
 * OpenAI-compatible protocol and the Anthropic protocol over
 * `java.net.http.HttpClient` (АРХ-02: no extra dependencies).
 *
 * The client is stateless apart from the settings snapshot; the API key never
 * appears in logs or exception messages (НФР-04, НФР-05).
 */
class LlmClient(private val settings: LlmSettings) {

    /**
     * HTTP/1.1 is forced deliberately: many OpenAI-compatible providers and
     * corporate proxies mishandle HTTP/2 upgrade for POST requests (the model
     * list GET succeeds while chat/completions dies with an IOException).
     * HTTP/1.1 is universally supported and the performance difference is
     * irrelevant for this workload.
     */
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
        .version(HttpClient.Version.HTTP_1_1)
        .build()

    /**
     * Loads the provider model list (LLM-03):
     * OpenAI/custom `GET {base}/models`, Anthropic `GET {base}/v1/models`.
     */
    fun listModels(): List<String> {
        val base = requireBaseUrl()
        val url = if (settings.provider == LlmProviderType.ANTHROPIC) "$base/v1/models" else "$base/models"
        val request = requestBuilder(url)
            .header("Accept", "application/json")
            .GET()
            .build()
        val response = send(request)
        return ModelListParser.parse(response).also { models ->
            AppLog.info("LLM model list loaded: provider=${settings.provider.wireName}, count=${models.size}")
        }
    }

    /**
     * Sends one chat completion and returns the assistant text. OpenAI/custom
     * use `/chat/completions`, Anthropic uses `/v1/messages` (LLM-01).
     */
    fun chat(systemPrompt: String, userPrompt: String): String {
        val base = requireBaseUrl()
        val model = requireModel()
        val body = if (settings.provider == LlmProviderType.ANTHROPIC) {
            anthropicBody(model, systemPrompt, userPrompt)
        } else {
            openAiBody(model, systemPrompt, userPrompt)
        }
        val url = if (settings.provider == LlmProviderType.ANTHROPIC) "$base/v1/messages" else "$base/chat/completions"
        val request = requestBuilder(url)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        // Journal request metadata only: never the prompt text or the API key.
        AppLog.info(
            "LLM chat request: provider=${settings.provider.wireName}, model=$model, " +
                "promptChars=${userPrompt.length}, maxTokens=${settings.maxTokens}, temperature=${settings.temperature}",
        )
        val response = send(request)
        val parsed = try {
            ChatResponseParser.parse(response)
        } catch (e: LlmException) {
            throw logged(e)
        }
        AppLog.info("LLM chat response: ${parsed.diagnostics}")
        val text = parsed.text
        if (text == null) {
            // A reasoning model can exhaust the token budget before writing the
            // answer: the chain of thought is present, content is empty.
            if (parsed.reasoningLength > 0 && parsed.finishReason == "length") {
                throw logged(
                    LlmException(
                        LlmErrorReason.EMPTY,
                        "The model spent the whole token budget on reasoning and produced no answer " +
                            "(${parsed.diagnostics}). Increase the max tokens in the settings.",
                    ),
                )
            }
            AppLog.warn("LLM response without text: " + bodySnippet(response, 1000))
            throw logged(LlmException(LlmErrorReason.EMPTY, "The provider returned no text (${parsed.diagnostics})."))
        }
        return text
    }

    /** True when the settings contain the minimum required fields. */
    fun isConfigured(): Boolean =
        settings.apiKey.isNotBlank() && settings.effectiveBaseUrl().isNotBlank() && settings.model.isNotBlank()

    private fun requestBuilder(url: String): HttpRequest.Builder {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(settings.timeoutSeconds.toLong()))
        return when (settings.provider) {
            LlmProviderType.ANTHROPIC -> builder
                .header("x-api-key", settings.apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
            else -> builder.header("Authorization", "Bearer " + settings.apiKey)
        }
    }

    private fun requireBaseUrl(): String {
        val base = settings.effectiveBaseUrl()
        if (base.isBlank()) throw LlmException(LlmErrorReason.CONFIG, "The base URL is empty.")
        return base
    }

    private fun requireModel(): String {
        if (settings.model.isBlank()) throw LlmException(LlmErrorReason.CONFIG, "No model is selected.")
        return settings.model
    }

    private fun openAiBody(model: String, systemPrompt: String, userPrompt: String): String {
        val messages = JSONArray()
        if (systemPrompt.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", systemPrompt))
        }
        messages.put(JSONObject().put("role", "user").put("content", userPrompt))
        return JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("temperature", settings.temperature)
            .put("max_tokens", settings.maxTokens)
            .put("stream", false)
            .toString()
    }

    private fun anthropicBody(model: String, systemPrompt: String, userPrompt: String): String {
        val messages = JSONArray().put(JSONObject().put("role", "user").put("content", userPrompt))
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", settings.maxTokens)
            .put("temperature", settings.temperature)
            .put("messages", messages)
        if (systemPrompt.isNotBlank()) body.put("system", systemPrompt)
        return body.toString()
    }

    /** Performs the HTTP call and maps failures to [LlmException]. */
    private fun send(request: HttpRequest): String {
        if (settings.apiKey.isBlank()) throw LlmException(LlmErrorReason.CONFIG, "The API key is empty.")
        val response = try {
            http.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: HttpTimeoutException) {
            throw logged(LlmException(LlmErrorReason.TIMEOUT, describe(e), e))
        } catch (e: IOException) {
            throw logged(LlmException(LlmErrorReason.NETWORK, describe(e), e))
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw logged(LlmException(LlmErrorReason.CANCELLED, "The request was cancelled.", e))
        }
        if (response.statusCode() == 401 || response.statusCode() == 403) {
            throw logged(LlmException(LlmErrorReason.AUTH, "HTTP " + response.statusCode()))
        }
        if (response.statusCode() !in 200..299) {
            throw logged(
                LlmException(
                    LlmErrorReason.HTTP,
                    "HTTP " + response.statusCode() + ": " + bodySnippet(response.body()),
                ),
            )
        }
        return response.body()
    }

    /**
     * Writes the failure into the application journal (reason and technical
     * detail only — never the API key or the prompt) and returns the exception
     * so it can be thrown in one line.
     */
    private fun logged(exception: LlmException): LlmException {
        AppLog.warn("LLM request failed: " + exception.reason + " (" + exception.detail + ")")
        return exception
    }

    /**
     * Human-readable description of a transport failure. HttpClient often
     * throws an IOException with a null message, so the class name and the
     * cause chain are included to make the problem diagnosable.
     */
    private fun describe(e: Throwable): String {
        val message = e.message
        val own = e.javaClass.simpleName + if (!message.isNullOrBlank()) ": $message" else ""
        val cause = e.cause ?: return own
        val causeMessage = cause.message
        return own + " <- " + cause.javaClass.simpleName + if (!causeMessage.isNullOrBlank()) ": $causeMessage" else ""
    }

    /**
     * Shortened body for diagnostics. Only provider responses are shortened
     * here — never the prompt text (НФР-04).
     */
    private fun bodySnippet(body: String, limit: Int = 200): String =
        body.replace('\n', ' ').take(limit)

    companion object {
        private const val CONNECT_TIMEOUT_SECONDS = 20L
        private const val ANTHROPIC_VERSION = "2023-06-01"
    }
}