package icejawriter.config

import org.json.JSONArray
import org.json.JSONObject

/**
 * LLM provider types supported by the application (LLM-01).
 */
enum class LlmProviderType(val wireName: String) {

    /** OpenAI cloud API. */
    OPENAI("openai"),

    /** Anthropic cloud API (Claude models). */
    ANTHROPIC("anthropic"),

    /** Custom OpenAI-compatible endpoint (DeepSeek, local servers, ...). */
    CUSTOM("custom");

    companion object {
        /** Parses the stored value; unknown/empty values fall back to OpenAI. */
        fun parse(value: String): LlmProviderType =
            entries.firstOrNull { it.wireName == value.lowercase() } ?: OPENAI
    }
}

/**
 * LLM connection and generation settings (LLM-02, АРХ-11). The API key is
 * stored only in `config/app.json` with POSIX 600 permissions (НФР-05).
 */
data class LlmSettings(
    val provider: LlmProviderType = LlmProviderType.OPENAI,
    val baseUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    val temperature: Double = 0.8,
    val maxTokens: Int = 2048,
    val timeoutSeconds: Int = 120,
    val contextTokenLimit: Int = 16384,
) {
    /**
     * Base URL to call; when the user leaves it empty the canonical default of
     * the selected provider is used (LLM-01, LLM-03).
     */
    fun effectiveBaseUrl(): String = when {
        baseUrl.isNotBlank() -> baseUrl.trimEnd('/')
        provider == LlmProviderType.OPENAI -> "https://api.openai.com/v1"
        provider == LlmProviderType.ANTHROPIC -> "https://api.anthropic.com"
        else -> ""
    }
}

/**
 * Application settings stored in `config/app.json` (АРХ-11, ФР-101):
 * general settings, the recent projects list (ФР-02) and the LLM block.
 */
data class AppSettings(
    val language: String = LANGUAGE_SYSTEM,
    val zoomScale: Double = 1.0,
    val autosaveIntervalMinutes: Int = 5,
    val projectsDir: String = "",
    val backupCount: Int = 3,
    val cardMode: String = CARD_MODE_HUMAN,
    val recentProjects: List<String> = emptyList(),
    val llm: LlmSettings = LlmSettings(),
) {
    /** Resolves the configured projects directory or the portable default. */
    fun effectiveProjectsDir(): String =
        projectsDir.ifBlank { AppPaths.defaultProjectsDirectory().toString() }

    /** Adds a project path to the recent list (newest first, at most 10, ФР-02). */
    fun withRecentProject(path: String): AppSettings =
        copy(recentProjects = (listOf(path) + recentProjects.filter { it != path }).take(MAX_RECENT_PROJECTS))

    companion object {
        const val LANGUAGE_SYSTEM = "system"
        const val LANGUAGE_RU = "ru"
        const val LANGUAGE_EN = "en"
        const val MAX_RECENT_PROJECTS = 10
        const val MIN_AUTOSAVE_MINUTES = 1
        const val MAX_AUTOSAVE_MINUTES = 60
        const val MIN_BACKUP_COUNT = 0
        const val MAX_BACKUP_COUNT = 9

        /**
         * Upper bound of the model answer length (max_tokens). Matches the
         * DeepSeek V4 maximum output of 384K; other providers simply reject
         * larger values with a clear HTTP error.
         */
        const val MAX_OUTPUT_TOKENS = 384_000

        /**
         * Upper bound of the curated context budget. Matches the DeepSeek V4
         * context window of 1M tokens.
         */
        const val MAX_CONTEXT_TOKENS = 1_000_000

        /** Card authoring mode: the author writes scene cards manually. */
        const val CARD_MODE_HUMAN = "human"

        /** Card authoring mode: the LLM proposes scene cards automatically. */
        const val CARD_MODE_AI = "ai"
    }
}

/**
 * org.json mapping of [AppSettings] used by [SettingsStore]. Unknown or
 * malformed fields fall back to defaults so a hand-edited app.json never
 * prevents the application from starting.
 */
object SettingsJson {

    fun toJson(settings: AppSettings): JSONObject {
        val root = JSONObject()
        root.put("language", settings.language)
        root.put("zoomScale", settings.zoomScale)
        root.put("autosaveIntervalMinutes", settings.autosaveIntervalMinutes)
        root.put("projectsDir", settings.projectsDir)
        root.put("backupCount", settings.backupCount)
        root.put("cardMode", settings.cardMode)
        root.put("recentProjects", JSONArray(settings.recentProjects))
        root.put("llm", llmToJson(settings.llm))
        return root
    }

    fun fromJson(text: String): AppSettings? {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            return null
        }
        return AppSettings(
            language = root.optString("language", AppSettings.LANGUAGE_SYSTEM)
                .ifBlank { AppSettings.LANGUAGE_SYSTEM },
            zoomScale = root.optDouble("zoomScale", 1.0).coerceIn(0.75, 3.0),
            autosaveIntervalMinutes = root.optInt("autosaveIntervalMinutes", 5)
                .coerceIn(0, AppSettings.MAX_AUTOSAVE_MINUTES),
            projectsDir = root.optString("projectsDir", ""),
            backupCount = root.optInt("backupCount", 3)
                .coerceIn(AppSettings.MIN_BACKUP_COUNT, AppSettings.MAX_BACKUP_COUNT),
            cardMode = root.optString("cardMode", AppSettings.CARD_MODE_HUMAN)
                .let { if (it == AppSettings.CARD_MODE_AI) AppSettings.CARD_MODE_AI else AppSettings.CARD_MODE_HUMAN },
            recentProjects = readRecentProjects(root),
            llm = readLlm(root.optJSONObject("llm")),
        )
    }

    private fun readRecentProjects(root: JSONObject): List<String> {
        val array = root.optJSONArray("recentProjects") ?: return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until array.length()) {
            val value = array.opt(i)
            if (value != null && value != JSONObject.NULL) result += value.toString()
        }
        return result.take(AppSettings.MAX_RECENT_PROJECTS)
    }

    private fun readLlm(llm: JSONObject?): LlmSettings {
        if (llm == null) return LlmSettings()
        return LlmSettings(
            provider = LlmProviderType.parse(llm.optString("provider", "")),
            baseUrl = llm.optString("baseUrl", ""),
            apiKey = llm.optString("apiKey", ""),
            model = llm.optString("model", ""),
            temperature = llm.optDouble("temperature", 0.8).coerceIn(0.0, 2.0),
            maxTokens = llm.optInt("maxTokens", 2048).coerceIn(1, AppSettings.MAX_OUTPUT_TOKENS),
            timeoutSeconds = llm.optInt("timeoutSeconds", 120).coerceIn(10, 600),
            contextTokenLimit = llm.optInt("contextTokenLimit", 16384)
                .coerceIn(1024, AppSettings.MAX_CONTEXT_TOKENS),
        )
    }

    private fun llmToJson(llm: LlmSettings): JSONObject = JSONObject()
        .put("provider", llm.provider.wireName)
        .put("baseUrl", llm.baseUrl)
        .put("apiKey", llm.apiKey)
        .put("model", llm.model)
        .put("temperature", llm.temperature)
        .put("maxTokens", llm.maxTokens)
        .put("timeoutSeconds", llm.timeoutSeconds)
        .put("contextTokenLimit", llm.contextTokenLimit)
}