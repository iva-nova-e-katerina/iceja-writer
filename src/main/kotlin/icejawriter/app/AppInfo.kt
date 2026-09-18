package icejawriter.app

/**
 * Static application information: the version is read from the Maven metadata
 * embedded into the JAR, so there is no hard-coded version string.
 */
object AppInfo {

    /** Version of the running build, e.g. "0.1.0". */
    val VERSION: String = readVersion()

    private fun readVersion(): String {
        val properties = java.util.Properties()
        val resource = AppInfo::class.java
            .getResourceAsStream("/META-INF/maven/iceja/iceja-writer/pom.properties")
        if (resource != null) {
            resource.use { properties.load(it) }
        }
        val version = properties.getProperty("version")
        if (!version.isNullOrBlank()) return version
        return AppInfo::class.java.`package`?.implementationVersion ?: "dev"
    }
}