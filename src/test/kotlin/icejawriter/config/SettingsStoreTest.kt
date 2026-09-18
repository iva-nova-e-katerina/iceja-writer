package icejawriter.config

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermission
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir

/**
 * Tests persistence of config/app.json (АРХ-11, ФР-02): settings and the
 * recent projects list must survive a restart, and a damaged file must never
 * prevent the application from starting.
 */
class SettingsStoreTest {

    private fun sampleSettings(): AppSettings = AppSettings(
        language = "ru",
        zoomScale = 1.25,
        autosaveIntervalMinutes = 7,
        projectsDir = "/home/writer/novels",
        backupCount = 4,
        cardMode = AppSettings.CARD_MODE_AI,
        recentProjects = listOf("/home/writer/novels/a.ijw", "/home/writer/novels/b.ijw"),
        llm = LlmSettings(
            provider = LlmProviderType.CUSTOM,
            baseUrl = "https://api.deepseek.com/v1",
            apiKey = "secret-key",
            model = "deepseek-flash",
            temperature = 0.7,
            maxTokens = 4096,
            timeoutSeconds = 90,
            contextTokenLimit = 8192,
        ),
    )

    @Test
    fun saveAndLoadRoundTrip(@TempDir dir: Path) {
        val configFile = dir.resolve("config").resolve("app.json")
        val store = SettingsStore(configFile)
        val settings = sampleSettings()

        assertTrue(store.save(settings), "save must succeed")
        assertTrue(Files.exists(configFile), "config/app.json must be created")

        // A fresh store simulates the next application start.
        assertEquals(settings, SettingsStore(configFile).load())
    }

    @Test
    fun missingFileYieldsDefaults(@TempDir dir: Path) {
        val store = SettingsStore(dir.resolve("config").resolve("app.json"))
        assertEquals(AppSettings(), store.load())
    }

    @Test
    fun corruptFileYieldsDefaults(@TempDir dir: Path) {
        val configFile = dir.resolve("config").resolve("app.json")
        Files.createDirectories(configFile.parent)
        Files.writeString(configFile, "this is not json")
        assertEquals(AppSettings(), SettingsStore(configFile).load())
    }

    @Test
    fun tokenLimitsAreClampedToModelMaximums() {
        val json = """{"llm":{"maxTokens":999999999,"contextTokenLimit":999999999}}"""
        val settings = SettingsJson.fromJson(json)
        assertEquals(AppSettings.MAX_OUTPUT_TOKENS, settings!!.llm.maxTokens)
        assertEquals(AppSettings.MAX_CONTEXT_TOKENS, settings.llm.contextTokenLimit)
    }

    @Test
    @DisabledOnOs(OS.WINDOWS)
    fun configFileIsOwnerOnly(@TempDir dir: Path) {
        val configFile = dir.resolve("config").resolve("app.json")
        SettingsStore(configFile).save(sampleSettings())
        assertEquals(
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
            Files.getPosixFilePermissions(configFile),
            "the API key must not be readable by other users (НФР-05)",
        )
    }
}