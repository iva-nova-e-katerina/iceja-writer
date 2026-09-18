package icejawriter.ui

import icejawriter.I18n
import icejawriter.llm.LlmException
import icejawriter.log.AppLog
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException

/**
 * Turns an LLM failure into a user-facing message.
 *
 * SwingWorker wraps every background exception into an ExecutionException, so
 * the cause chain is unwrapped first. Without this, every failure (auth,
 * HTTP error, parse error, configuration) was misreported as "no connection
 * to the provider" (ИНТ-31).
 */
object LlmErrors {

    /** Unwraps SwingWorker/CompletionException wrappers down to the real cause. */
    fun rootCause(throwable: Throwable): Throwable {
        var current = throwable
        while ((current is ExecutionException || current is CompletionException) && current.cause != null) {
            current = current.cause!!
        }
        return current
    }

    /** Localized message for the user, including the technical detail. */
    fun message(throwable: Throwable): String {
        val cause = rootCause(throwable)
        val llm = cause as? LlmException
        val base = if (llm != null) {
            I18n.t("llm.error." + llm.reason.name.lowercase())
        } else {
            I18n.t("llm.error.unexpected")
        }
        val detail = llm?.detail ?: describe(cause)
        return if (detail.isBlank()) base else "$base ($detail)"
    }

    /** Logs the real cause for diagnostics (never prompts or API keys). */
    fun log(operation: String, throwable: Throwable) {
        AppLog.error(operation, rootCause(throwable))
    }

    private fun describe(cause: Throwable): String {
        val message = cause.message
        return cause.javaClass.simpleName + if (!message.isNullOrBlank()) ": $message" else ""
    }
}