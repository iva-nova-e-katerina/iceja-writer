package icejawriter.install

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class JavaVersionCheckerTest {

    @Test
    fun parsesPlainMajorVersions() {
        assertEquals(25, JavaVersionChecker.parseMajorVersion("25"))
        assertEquals(26, JavaVersionChecker.parseMajorVersion("26.0.1"))
        assertEquals(25, JavaVersionChecker.parseMajorVersion("25.0.2"))
    }

    @Test
    fun parsesVariantEncodings() {
        assertEquals(1, JavaVersionChecker.parseMajorVersion("1.8.0_292"))
        assertEquals(25, JavaVersionChecker.parseMajorVersion("25-ea"))
        assertEquals(25, JavaVersionChecker.parseMajorVersion("\"25.0.2\""))
        assertEquals(25, JavaVersionChecker.parseMajorVersion("version \"25\""))
    }

    @Test
    fun unknownVersionsParseToZero() {
        assertEquals(0, JavaVersionChecker.parseMajorVersion(""))
        assertEquals(0, JavaVersionChecker.parseMajorVersion("not-a-version"))
        assertEquals(0, JavaVersionChecker.parseMajorVersion("  "))
    }
}

class InstallPathsTest {

    @Test
    fun posixSystemPathsAreBlocked() {
        assertTrue(InstallPaths.isBlockedPosixPath("/"))
        assertTrue(InstallPaths.isBlockedPosixPath("/usr"))
        assertTrue(InstallPaths.isBlockedPosixPath("/usr/local/lib"))
        assertTrue(InstallPaths.isBlockedPosixPath("/opt"))
        assertTrue(InstallPaths.isBlockedPosixPath("/bin/tools"))
    }

    @Test
    fun posixUserPathsAreAllowed() {
        assertFalse(InstallPaths.isBlockedPosixPath("/home/katya/IceJAWriter"))
        assertFalse(InstallPaths.isBlockedPosixPath("/tmp/IceJAWriter"))
        assertFalse(InstallPaths.isBlockedPosixPath("/home/katya/Documents/IceJAWriter"))
    }

    @Test
    fun windowsSystemPathsAreBlocked() {
        assertTrue(InstallPaths.isBlockedWindowsPath("C:\\"))
        assertTrue(InstallPaths.isBlockedWindowsPath("C:\\Windows"))
        assertTrue(InstallPaths.isBlockedWindowsPath("c:\\WINDOWS\\System32"))
        assertTrue(InstallPaths.isBlockedWindowsPath("C:\\Program Files"))
        assertTrue(InstallPaths.isBlockedWindowsPath("C:\\Program Files (x86)"))
    }

    @Test
    fun windowsUserPathsAreAllowed() {
        assertFalse(InstallPaths.isBlockedWindowsPath("C:\\Users\\katya\\IceJAWriter"))
        assertFalse(InstallPaths.isBlockedWindowsPath("D:\\IceJAWriter"))
    }
}

class LauncherScriptsTest {

    @Test
    fun shellScriptReferencesTheJar() {
        val script = LauncherScripts.shellScript()
        assertTrue(script.contains(InstallDefaults.JAR_FILE_NAME))
        assertTrue(script.startsWith("#!/bin/sh"))
    }

    @Test
    fun batchScriptReferencesTheJar() {
        val script = LauncherScripts.batchScript()
        assertTrue(script.contains(InstallDefaults.JAR_FILE_NAME))
        assertTrue(script.contains("JAVA_HOME"))
    }

    @Test
    fun scriptForMapsOsFamilies() {
        assertEquals(LauncherScripts.shellScript(), LauncherScripts.scriptFor(OsKind.LINUX))
        assertEquals(LauncherScripts.shellScript(), LauncherScripts.scriptFor(OsKind.MACOS))
        assertEquals(LauncherScripts.batchScript(), LauncherScripts.scriptFor(OsKind.WINDOWS))
    }
}

class InstallerTest {

    @org.junit.jupiter.api.io.TempDir
    lateinit var tempDirectory: java.nio.file.Path

    @Test
    fun freshLinuxInstallCreatesThePortableLayout() {
        val sourceJar = writeSourceJar("source-one.jar", "jar-content-one")
        val installDirectory = tempDirectory.resolve("install-linux")

        val result = Installer(sourceJar, installDirectory, OsKind.LINUX).install()

        assertTrue(result.success, "install failed: ${result.errorMessage}")
        assertTrue(result.logLines.isNotEmpty())
        assertTrue(java.nio.file.Files.isDirectory(installDirectory.resolve("config")))
        assertTrue(java.nio.file.Files.isDirectory(installDirectory.resolve("projects")))
        assertTrue(java.nio.file.Files.isDirectory(installDirectory.resolve("logs")))
        assertEquals("jar-content-one", java.nio.file.Files.readString(installDirectory.resolve("iceja-writer.jar")))
        assertTrue(java.nio.file.Files.isRegularFile(installDirectory.resolve("iceja-writer.sh")))
        assertTrue(java.nio.file.Files.isExecutable(installDirectory.resolve("iceja-writer.sh")))

        val config = installDirectory.resolve("config").resolve("app.json")
        assertTrue(java.nio.file.Files.exists(config))
        val configText = java.nio.file.Files.readString(config)
        assertTrue(configText.contains("\"language\""))
        assertTrue(configText.contains("\"zoomScale\""))
        assertTrue(configText.contains("\"llm\""))

        val installLog = installDirectory.resolve("logs").resolve("install.log")
        assertTrue(java.nio.file.Files.exists(installLog))
        assertTrue(java.nio.file.Files.size(installLog) > 0L)
    }

    @Test
    fun updatePreservesConfigAndProjectsAndBacksUpThePreviousJar() {
        val installDirectory = tempDirectory.resolve("install-update")
        Installer(writeSourceJar("source-first.jar", "first"), installDirectory, OsKind.LINUX).install()

        val projectFile = installDirectory.resolve("projects").resolve("my-novel.ijw")
        java.nio.file.Files.writeString(projectFile, "project-data")
        val configFile = installDirectory.resolve("config").resolve("app.json")
        java.nio.file.Files.writeString(configFile, "custom-config")

        val result = Installer(writeSourceJar("source-second.jar", "second"), installDirectory, OsKind.LINUX).install()

        assertTrue(result.success, "update failed: ${result.errorMessage}")
        assertEquals("project-data", java.nio.file.Files.readString(projectFile))
        assertEquals("custom-config", java.nio.file.Files.readString(configFile))
        assertEquals("second", java.nio.file.Files.readString(installDirectory.resolve("iceja-writer.jar")))
        assertEquals("first", java.nio.file.Files.readString(installDirectory.resolve("iceja-writer.jar.backup")))
    }

    @Test
    fun windowsInstallWritesTheBatchLauncherOnly() {
        val sourceJar = writeSourceJar("source-win.jar", "win-content")
        val installDirectory = tempDirectory.resolve("install-win")

        val result = Installer(sourceJar, installDirectory, OsKind.WINDOWS).install()

        assertTrue(result.success, "install failed: ${result.errorMessage}")
        assertTrue(java.nio.file.Files.isRegularFile(installDirectory.resolve("iceja-writer.bat")))
        assertFalse(java.nio.file.Files.exists(installDirectory.resolve("iceja-writer.sh")))
        assertEquals("win-content", java.nio.file.Files.readString(installDirectory.resolve("iceja-writer.jar")))
    }

    @Test
    fun systemDirectoriesAreRejectedWithoutTouchingThem() {
        val sourceJar = writeSourceJar("source-blocked.jar", "x")

        val result = Installer(sourceJar, java.nio.file.Paths.get("/usr"), OsKind.LINUX).install()

        assertFalse(result.success)
    }

    @Test
    fun missingSourceJarIsRejected() {
        val result = Installer(
            tempDirectory.resolve("does-not-exist.jar"),
            tempDirectory.resolve("install-missing-source"),
            OsKind.LINUX,
        ).install()

        assertFalse(result.success)
    }

    private fun writeSourceJar(fileName: String, content: String): java.nio.file.Path {
        val source = tempDirectory.resolve("sources").resolve(fileName)
        java.nio.file.Files.createDirectories(source.parent)
        java.nio.file.Files.writeString(source, content)
        return source
    }
}

class UninstallerTest {

    @org.junit.jupiter.api.io.TempDir
    lateinit var tempDirectory: java.nio.file.Path

    @Test
    fun findsIjwProjectsRecursivelySorted() {
        val root = tempDirectory.resolve("install")
        java.nio.file.Files.createDirectories(root.resolve("projects"))
        java.nio.file.Files.writeString(root.resolve("projects").resolve("a.ijw"), "a")
        java.nio.file.Files.writeString(root.resolve("standalone.ijw"), "s")
        java.nio.file.Files.writeString(root.resolve("readme.txt"), "r")

        val found = Uninstaller.findProjectFiles(root)

        assertEquals(
            listOf(
                root.resolve("projects").resolve("a.ijw"),
                root.resolve("standalone.ijw"),
            ),
            found,
        )
    }

    @Test
    fun deletesTheWholeTree() {
        val root = tempDirectory.resolve("install")
        java.nio.file.Files.createDirectories(root.resolve("config"))
        java.nio.file.Files.createDirectories(root.resolve("projects"))
        java.nio.file.Files.writeString(root.resolve("projects").resolve("a.ijw"), "a")

        val result = Uninstaller.deleteRecursively(root)

        assertTrue(result.success)
        assertTrue(result.remaining.isEmpty())
        assertFalse(java.nio.file.Files.exists(root))
    }

    @Test
    fun missingDirectoryIsASuccessfulNoop() {
        val result = Uninstaller.deleteRecursively(tempDirectory.resolve("absent"))

        assertTrue(result.success)
        assertTrue(result.remaining.isEmpty())
    }

    @Test
    fun installedDirectoryDetectionRequiresConfigSubdirectory() {
        val plain = tempDirectory.resolve("plain")
        java.nio.file.Files.createDirectories(plain)
        assertFalse(Uninstaller.isInstalledDirectory(plain))

        val installed = tempDirectory.resolve("installed")
        java.nio.file.Files.createDirectories(installed.resolve("config"))
        assertTrue(Uninstaller.isInstalledDirectory(installed))
    }
}