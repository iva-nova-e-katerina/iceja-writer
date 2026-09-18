package icejawriter.llm

import org.json.JSONArray
import org.json.JSONObject

/**
 * Normalized chat response of a provider: the answer text plus the metadata
 * needed for diagnostics (finish reason, lengths, token usage). [text] is null
 * when the provider answered successfully but produced no text at all — for
 * example a reasoning model that exhausted its token budget before writing the
 * answer.
 */
data class ChatResponse(
    val text: String?,
    val finishReason: String,
    val contentLength: Int,
    val reasoningLength: Int,
    val promptTokens: Int,
    val completionTokens: Int,
) {
    /** Compact, secret-free description written into the journal. */
    val diagnostics: String
        get() = "finish_reason=$finishReason, content=$contentLength chars, " +
            "reasoning=$reasoningLength chars, tokens=$promptTokens/$completionTokens"
}

/**
 * Parses an OpenAI-compatible or Anthropic chat response (LLM-01) and reports
 * everything needed to diagnose an empty answer: reasoning models return the
 * chain of thought in `reasoning_content` while `content` stays empty when the
 * token budget runs out.
 */
object ChatResponseParser {

    /** Parses the body; throws [LlmException] only when the body is not JSON. */
    fun parse(body: String): ChatResponse {
        val root = try {
            JSONObject(body)
        } catch (e: Exception) {
            throw LlmException(LlmErrorReason.PARSE, "The provider response is not valid JSON.", e)
        }
        val usage = root.optJSONObject("usage")

        // OpenAI-compatible: choices[0].message.content
        val choices = root.optJSONArray("choices")
        if (choices != null && choices.length() > 0) {
            val choice = choices.optJSONObject(0)
            val message = choice?.optJSONObject("message")
            val content = textValue(message?.opt("content"))
            val reasoning = textValue(message?.opt("reasoning_content"))
            return ChatResponse(
                text = content,
                finishReason = choice?.optString("finish_reason", "").orEmpty(),
                contentLength = content?.length ?: 0,
                reasoningLength = reasoning?.length ?: 0,
                promptTokens = usage?.optInt("prompt_tokens", 0) ?: 0,
                completionTokens = usage?.optInt("completion_tokens", 0) ?: 0,
            )
        }

        // Anthropic: content[] blocks with type=text
        val contentBlocks = root.optJSONArray("content")
        if (contentBlocks != null) {
            val builder = StringBuilder()
            for (i in 0 until contentBlocks.length()) {
                val block = contentBlocks.optJSONObject(i) ?: continue
                if (block.optString("type", "") == "text") {
                    builder.append(block.optString("text", ""))
                }
            }
            val text = builder.toString().ifBlank { null }
            return ChatResponse(
                text = text,
                finishReason = root.optString("stop_reason", ""),
                contentLength = text?.length ?: 0,
                reasoningLength = 0,
                promptTokens = usage?.optInt("input_tokens", 0) ?: 0,
                completionTokens = usage?.optInt("output_tokens", 0) ?: 0,
            )
        }

        throw LlmException(
            LlmErrorReason.PARSE,
            "The provider response contains neither choices nor content blocks.",
        )
    }

    /**
     * Reads a text value that may be a plain string or an array of text parts
     * (some OpenAI-compatible providers return content as an array).
     */
    private fun textValue(value: Any?): String? = when (value) {
        null, JSONObject.NULL -> null
        is String -> value.ifBlank { null }
        is JSONArray -> buildString {
            for (i in 0 until value.length()) {
                when (val element = value.opt(i)) {
                    is String -> append(element)
                    is JSONObject -> append(element.optString("text", ""))
                    else -> Unit
                }
            }
        }.ifBlank { null }
        else -> value.toString().ifBlank { null }
    }
}