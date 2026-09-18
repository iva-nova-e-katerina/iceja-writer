package icejawriter.llm

/**
 * Session cache of provider model lists (LLM-05): the list is downloaded at
 * most once per provider configuration and reused until the user explicitly
 * requests a refresh. The cache key is derived without storing the API key
 * itself (НФР-05).
 */
object ModelListCache {

    private var cacheKey: String? = null
    private var models: List<String> = emptyList()

    /** Returns the cached list for the configuration, or null when absent. */
    fun get(provider: String, baseUrl: String, apiKey: String, model: String): List<String>? {
        val key = key(provider, baseUrl, apiKey, model)
        return if (key == cacheKey) models else null
    }

    /** Stores the freshly downloaded list for the configuration. */
    fun put(provider: String, baseUrl: String, apiKey: String, model: String, loaded: List<String>) {
        cacheKey = key(provider, baseUrl, apiKey, model)
        models = loaded
    }

    /** Drops the cached list (used when settings change incompatibly). */
    fun clear() {
        cacheKey = null
        models = emptyList()
    }

    /**
     * Builds the cache key from the connection identity. The API key is
     * represented by its hash code so the raw secret never lives in the cache.
     */
    private fun key(provider: String, baseUrl: String, apiKey: String, model: String): String =
        provider + "|" + baseUrl + "|" + apiKey.hashCode() + "|" + model
}