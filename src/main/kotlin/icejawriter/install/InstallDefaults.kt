package icejawriter.install

/**
 * Constants and default values of the portable installation (УСТ-22, УСТ-40).
 * Defaults are aligned with the spec: АРХ-11 (config/app.json contents) and
 * LLM-02 (LLM settings fields) of the application specification.
 */
object InstallDefaults {

    const val APP_NAME = "IceJA Writer"

    const val JAR_FILE_NAME = "iceja-writer.jar"
    const val SHELL_SCRIPT_NAME = "iceja-writer.sh"
    const val BATCH_SCRIPT_NAME = "iceja-writer.bat"

    const val CONFIG_DIR_NAME = "config"
    const val PROJECTS_DIR_NAME = "projects"
    const val LOGS_DIR_NAME = "logs"

    const val APP_CONFIG_FILE_NAME = "app.json"
    const val INSTALL_LOG_FILE_NAME = "install.log"
    const val JAR_BACKUP_SUFFIX = ".backup"

    /**
     * Default settings file content written on a fresh install (УСТ-22.4).
     * On an update an existing config/app.json is preserved (УСТ-40).
     * Fields follow АРХ-11 and the settings dialogs defined by ФР-101 and
     * LLM-02: general settings plus a single LLM provider block.
     */
    val DEFAULT_APP_JSON: String = """
        {
          "language": "system",
          "zoomScale": 1.0,
          "autosaveIntervalMinutes": 5,
          "projectsDir": "projects",
          "backupCount": 3,
          "llm": {
            "provider": "",
            "baseUrl": "",
            "apiKey": "",
            "model": "",
            "temperature": 0.8,
            "maxTokens": 2048,
            "timeoutSeconds": 120
          }
        }
    """.trimIndent()
}