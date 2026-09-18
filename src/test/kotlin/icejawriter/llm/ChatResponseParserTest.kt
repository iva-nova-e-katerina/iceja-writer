package icejawriter.llm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests the chat response parser (LLM-01): OpenAI-compatible and Anthropic
 * shapes, content arrays, JSON null content and reasoning models that produce
 * no final text. Regression for the "provider response contains no text"
 * failure where the real reason (token budget spent on reasoning) was hidden.
 */
class ChatResponseParserTest {

    @Test
    fun parsesOpenAiContent() {
        val body = """
            {"choices":[{"message":{"role":"assistant","content":"Готовый текст."},"finish_reason":"stop"}],
             "usage":{"prompt_tokens":100,"completion_tokens":50}}
        """.trimIndent()
        val parsed = ChatResponseParser.parse(body)
        assertEquals("Готовый текст.", parsed.text)
        assertEquals("stop", parsed.finishReason)
        assertEquals(100, parsed.promptTokens)
        assertEquals(50, parsed.completionTokens)
    }

    @Test
    fun parsesContentArray() {
        val body = """
            {"choices":[{"message":{"content":[{"type":"text","text":"Часть один. "},{"type":"text","text":"Часть два."}]},
             "finish_reason":"stop"}]}
        """.trimIndent()
        assertEquals("Часть один. Часть два.", ChatResponseParser.parse(body).text)
    }

    @Test
    fun nullContentIsNotTheStringNull() {
        val body = """{"choices":[{"message":{"content":null},"finish_reason":"stop"}]}"""
        assertNull(ChatResponseParser.parse(body).text)
    }

    @Test
    fun reasoningModelWithoutAnswerIsReported() {
        val body = """
            {"choices":[{"message":{"content":"","reasoning_content":"длинная цепочка рассуждений..."},
             "finish_reason":"length"}],
             "usage":{"completion_tokens":2048}}
        """.trimIndent()
        val parsed = ChatResponseParser.parse(body)
        assertNull(parsed.text)
        assertEquals("length", parsed.finishReason)
        assertTrue(parsed.reasoningLength > 0)
        assertTrue(parsed.diagnostics.contains("reasoning="))
    }

    @Test
    fun parsesAnthropicContentBlocks() {
        val body = """
            {"content":[{"type":"text","text":"Ответ Claude."}],"stop_reason":"end_turn",
             "usage":{"input_tokens":10,"output_tokens":20}}
        """.trimIndent()
        val parsed = ChatResponseParser.parse(body)
        assertEquals("Ответ Claude.", parsed.text)
        assertEquals("end_turn", parsed.finishReason)
        assertEquals(20, parsed.completionTokens)
    }

    @Test
    fun invalidJsonIsParseError() {
        val exception = assertThrows(LlmException::class.java) {
            ChatResponseParser.parse("not json")
        }
        assertEquals(LlmErrorReason.PARSE, exception.reason)
    }

    @Test
    fun unknownShapeIsParseError() {
        val exception = assertThrows(LlmException::class.java) {
            ChatResponseParser.parse("""{"object":"list"}""")
        }
        assertEquals(LlmErrorReason.PARSE, exception.reason)
    }
}