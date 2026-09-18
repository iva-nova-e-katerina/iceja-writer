package icejawriter.llm

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests the model list parser (LLM-03, НФР-12) with mock provider answers for
 * the OpenAI and Anthropic shapes.
 */
class ModelListParserTest {

    @Test
    fun parsesOpenAiModelList() {
        val json = """
            {"object":"list","data":[
              {"id":"gpt-4o","object":"model"},
              {"id":"gpt-4o-mini","object":"model"},
              {"id":"o3","object":"model"}
            ]}
        """.trimIndent()
        assertEquals(listOf("gpt-4o", "gpt-4o-mini", "o3"), ModelListParser.parse(json))
    }

    @Test
    fun parsesAnthropicModelList() {
        val json = """
            {"data":[
              {"type":"model","id":"claude-sonnet-4-5","display_name":"Claude Sonnet 4.5"},
              {"type":"model","id":"claude-opus-4-1","display_name":"Claude Opus 4.1"}
            ],"has_more":false}
        """.trimIndent()
        assertEquals(listOf("claude-opus-4-1", "claude-sonnet-4-5"), ModelListParser.parse(json))
    }

    @Test
    fun returnsEmptyListForEmptyData() {
        assertTrue(ModelListParser.parse("""{"data":[]}""").isEmpty())
        assertTrue(ModelListParser.parse("{}").isEmpty())
    }

    @Test
    fun rejectsInvalidJsonWithLocalizableReason() {
        val exception = assertThrows(LlmException::class.java) {
            ModelListParser.parse("not json")
        }
        assertEquals(LlmErrorReason.PARSE, exception.reason)
    }
}