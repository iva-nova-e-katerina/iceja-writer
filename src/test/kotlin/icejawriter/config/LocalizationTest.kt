package icejawriter.config

import java.util.Locale
import java.util.ResourceBundle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Localization completeness (ИНТ-10, НФР-12): the Russian and English bundles
 * must contain exactly the same keys, and every key used by the UI must be
 * resolvable (a missing translation renders as "[key]").
 */
class LocalizationTest {

    private fun bundle(language: String): ResourceBundle =
        ResourceBundle.getBundle("messages", Locale(language))

    @Test
    fun russianAndEnglishHaveIdenticalKeys() {
        val ru = bundle("ru").keySet()
        val en = bundle("en").keySet()
        assertEquals(ru, en, "messages_ru.properties and messages_en.properties must have the same keys")
    }

    @Test
    fun allKeysResolveInBothLanguages() {
        val keys = bundle("ru").keySet()
        for (language in listOf("ru", "en")) {
            val bundle = bundle(language)
            for (key in keys) {
                val value = bundle.getString(key)
                assertTrue(value.isNotBlank(), "empty translation for $key in $language")
                assertFalse(value.startsWith("["), "unresolved translation for $key in $language")
            }
        }
    }

    @Test
    fun dynamicKeysExist() {
        val ru = bundle("ru")
        val dynamic = buildList {
            add("lang.system")
            for (status in listOf("draft", "revised", "approved")) add("scene.status.$status")
            for (provider in listOf("openai", "anthropic", "custom")) add("settings.llm.provider.$provider")
            for (separator in listOf("none", "blank", "asterism")) add("export.separator.$separator")
            for (reason in listOf("auth", "network", "timeout", "http", "empty", "cancelled", "parse", "config", "unexpected")) add("llm.error.$reason")
            for (type in listOf("continuity", "logic", "chronology", "knowledge", "style", "plotholes")) {
                add("checks.type.$type")
            }
            for (scope in listOf("novel", "act", "chapter")) add("checks.scope.$scope")
            for (mode in listOf("human", "ai")) add("settings.cardMode.$mode")
            for (section in listOf("characters", "chronology", "rules", "glossary", "world", "style", "storylines")) {
                add("bible.section.$section")
            }
            for (field in listOf("geography", "politics", "economy", "techMagic", "religion", "languages", "notes")) {
                add("bible.world.$field")
                add("bible.world.$field.tooltip")
            }
            for (field in listOf("possible", "impossible", "cost", "limits", "notes")) {
                add("bible.rules.$field")
                add("bible.rules.$field.tooltip")
            }
            for (field in listOf("pov", "tense", "tone", "forbiddenWords", "typicalPhrases", "notes")) {
                add("bible.style.$field")
                add("bible.style.$field.tooltip")
            }
            for (state in listOf("idle", "working", "done", "cancelled", "error")) add("status.llm.$state")
        }
        for (key in dynamic) {
            assertNotNull(ru.getString(key), "missing dynamic key $key")
        }
    }

    @Test
    fun settingsRoundTripAndCorruptFallback() {
        val settings = AppSettings(
            language = "en",
            zoomScale = 1.5,
            autosaveIntervalMinutes = 10,
            projectsDir = "/tmp/projects",
            backupCount = 5,
            cardMode = AppSettings.CARD_MODE_AI,
            recentProjects = listOf("/a.ijw", "/b.ijw"),
            llm = LlmSettings(
                provider = LlmProviderType.ANTHROPIC,
                baseUrl = "https://api.anthropic.com",
                apiKey = "secret",
                model = "claude-sonnet-4-5",
                temperature = 0.5,
                maxTokens = 4096,
                timeoutSeconds = 60,
                contextTokenLimit = 8192,
            ),
        )
        val json = SettingsJson.toJson(settings).toString()
        assertEquals(settings, SettingsJson.fromJson(json))
        assertNull(SettingsJson.fromJson("not json"))
    }

    @Test
    fun settingsDefaultsForEmptyJson() {
        val settings = SettingsJson.fromJson("{}")
        assertNotNull(settings)
        assertEquals(AppSettings.LANGUAGE_SYSTEM, settings!!.language)
        assertEquals(3, settings.backupCount)
        assertEquals(LlmProviderType.OPENAI, settings.llm.provider)
    }
}