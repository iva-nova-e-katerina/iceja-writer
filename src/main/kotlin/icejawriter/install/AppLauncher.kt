package icejawriter.install

import java.nio.file.Path

/**
 * Launches the freshly installed copy of IceJA Writer from the install
 * directory (УСТ-23 "Запустить IceJA Writer"). The java command is resolved
 * from JAVA_HOME/PATH so the launched copy behaves exactly like the launcher
 * scripts. The child process is detached from the wizard and keeps running
 * after the installer exits.
 */
object AppLauncher {

    /** Starts "java -jar <installDir>/iceja-writer.jar" as a separate process. */
    fun launchInstalledCopy(installDir: Path) {
        val javaCommand = JavaVersionChecker.findJavaExecutable()?.toString() ?: "java"
        val processBuilder = ProcessBuilder(
            javaCommand,
            "-jar",
            installDir.resolve(InstallDefaults.JAR_FILE_NAME).toString(),
        )
        processBuilder.inheritIO()
        processBuilder.start()
    }
}