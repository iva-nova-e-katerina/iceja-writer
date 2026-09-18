package icejawriter.llm

import org.json.JSONObject

/**
 * An object extracted from free model output: the balanced `{...}` text and
 * its position in the original answer (needed to separate the answer text from
 * the JSON block).
 */
data class ExtractedJson(
    val startIndex: Int,
    val text: String,
)

/**
 * Extracts the first balanced JSON object that contains at least one of the
 * given keys from free-form model output. String literals and nested braces
 * are handled correctly, so a JSON block inside prose or a fenced code block
 * is found reliably.
 */
internal object JsonObjectExtraction {

    /**
     * All balanced JSON objects found in the answer (outer and nested ones).
     * Callers can score them and pick the best instead of trusting the first.
     */
    fun extractAll(answer: String): List<ExtractedJson> {
        val result = mutableListOf<ExtractedJson>()
        var start = answer.indexOf('{')
        while (start >= 0) {
            val end = findMatchingBrace(answer, start)
            if (end != null) {
                result += ExtractedJson(startIndex = start, text = answer.substring(start, end + 1))
            }
            start = answer.indexOf('{', start + 1)
        }
        return result
    }

    fun extract(answer: String, keys: List<String>): ExtractedJson? {
        var start = answer.indexOf('{')
        while (start >= 0) {
            val end = findMatchingBrace(answer, start)
            if (end != null) {
                val candidate = answer.substring(start, end + 1)
                // Prefer an object that carries the keys DIRECTLY: a model may
                // wrap the payload into another object (e.g. {"card": {...}}),
                // and the outer object would otherwise match by substring and
                // yield an empty result.
                if (keys.any { candidate.contains(it) } && hasDirectKey(candidate, keys)) {
                    return ExtractedJson(startIndex = start, text = candidate)
                }
            }
            start = answer.indexOf('{', start + 1)
        }
        return null
    }

    /** True when the candidate parses and has at least one key at its top level. */
    private fun hasDirectKey(candidate: String, keys: List<String>): Boolean {
        val parsed = try {
            JSONObject(candidate)
        } catch (e: Exception) {
            return false
        }
        return keys.any { parsed.has(it) }
    }

    /** Returns the index of the brace closing the object opened at [start]. */
    fun findMatchingBrace(text: String, start: Int): Int? {
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until text.length) {
            val char = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                continue
            }
            when (char) {
                '"' -> inString = true
                '{' -> depth += 1
                '}' -> {
                    depth -= 1
                    if (depth == 0) return index
                }
            }
        }
        return null
    }

    /** Reads a string field tolerating JSON null and missing values. */
    fun stringOrEmpty(o: JSONObject, key: String): String {
        val value = o.opt(key) ?: return ""
        return if (value == JSONObject.NULL) "" else value.toString()
    }
}