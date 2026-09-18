package icejawriter

import java.util.Locale
import java.util.ResourceBundle

/**
 * i18n dictionary backed by ResourceBundle properties files
 * (src/main/resources/messages_ru.properties and messages_en.properties).
 * Mirrors the pattern used by the Iceja Security dashboard.
 */
object I18n {

    private const val BASE_NAME = "messages"

    @Volatile
    var locale: Locale = Locale.getDefault()
        private set

    @Volatile
    private var bundle: ResourceBundle = ResourceBundle.getBundle(BASE_NAME, locale)

    fun setLocale(newLocale: Locale) {
        locale = newLocale
        bundle = ResourceBundle.getBundle(BASE_NAME, locale)
    }

    /**
     * Resolves a message key for the current locale. Unresolved keys are
     * returned verbatim as "[key]" so that a missing translation is visible
     * instead of silently failing.
     */
    fun t(key: String): String =
        try {
            bundle.getString(key)
        } catch (_: Exception) {
            "[$key]"
        }
}