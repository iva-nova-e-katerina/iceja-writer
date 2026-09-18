package icejawriter.llm

/**
 * Loads prompt templates from the application resources (LLM-12). Templates
 * are plain text files with `{placeholder}` markers; a missing resource yields
 * a built-in fallback so generation never breaks because of packaging.
 */
object PromptTemplates {

    /** Template for the scene draft generation (LLM-20). */
    fun draft(language: String): String = load("draft", language)

    /** Template for the AI-written scene card (card authoring mode). */
    fun card(language: String): String = load("card", language)

    /** Template for continuity checks (LLM-40). */
    fun checks(language: String): String = load("checks", language)

    /** Template for scene summaries (LLM-50). */
    fun summary(language: String): String = load("summary", language)

    /**
     * Fills `{placeholder}` markers. Unknown placeholders stay untouched so a
     * malformed template is visible in the prompt instead of being hidden.
     */
    fun fill(template: String, values: Map<String, String>): String {
        var result = template
        for ((key, value) in values) {
            result = result.replace("{$key}", value)
        }
        return result
    }

    private fun load(name: String, language: String): String {
        val suffix = if (language == "en") "en" else "ru"
        val path = "/prompts/${name}_$suffix.txt"
        val stream = PromptTemplates::class.java.getResourceAsStream(path)
            ?: return ""
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}