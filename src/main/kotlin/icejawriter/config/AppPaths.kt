package icejawriter.config

import icejawriter.install.InstallPaths
import java.nio.file.Path

/**
 * Resolves the portable application directories (АРХ-04, АРХ-11): everything
 * lives next to the running `iceja-writer.jar` — `config/`, `logs/`,
 * `projects/`. When the JAR location cannot be determined (for example during
 * development from compiled classes) the current working directory is used so
 * that no files are ever written outside the portable tree.
 */
object AppPaths {

    /** Base directory of the portable installation (the JAR's parent). */
    fun baseDirectory(): Path =
        InstallPaths.runningJarPath()?.parent ?: Path.of(System.getProperty("user.dir"))

    /** `config/app.json` — application settings (АРХ-11). */
    fun configFile(): Path = baseDirectory().resolve("config").resolve("app.json")

    /** `logs/` — application journal directory (АРХ-30). */
    fun logsDirectory(): Path = baseDirectory().resolve("logs")

    /** `logs/app.log` — rotating application journal (НФР-04). */
    fun logFile(): Path = logsDirectory().resolve("app.log")

    /** `projects/` — default project directory (АРХ-11). */
    fun defaultProjectsDirectory(): Path = baseDirectory().resolve("projects")
}