package icejawriter.model

import icejawriter.project.ProjectFormat
import java.time.Instant

/**
 * The whole project kept in memory — the .ijw archive unzipped into a plain
 * object graph (АРХ-10): manifest + novel description + the bible + all
 * scenes.
 */
data class ProjectDocument(
    val manifest: Manifest,
    val novel: NovelDescription,
    val bible: Bible,
    val scenes: List<Scene>,
) {
    /** Convenience alias for the project title stored in the manifest. */
    val title: String get() = manifest.title

    companion object {

        /**
         * Creates a brand new empty project for the given novel title.
         *
         * @param title title of the novel, written both into the manifest and
         *   into novel.json
         * @param appVersion version of the application that created the file
         * @param createdAtUtc creation timestamp in ISO-8601 UTC; defaults to
         *   the current moment
         */
        fun newProject(
            title: String,
            appVersion: String,
            createdAtUtc: String = Instant.now().toString(),
        ): ProjectDocument {
            val now = createdAtUtc
            return ProjectDocument(
                manifest = Manifest(
                    formatVersion = ProjectFormat.FORMAT_VERSION,
                    appVersion = appVersion,
                    title = title,
                    createdAt = now,
                    updatedAt = now,
                ),
                novel = NovelDescription(title = title),
                bible = Bible(),
                scenes = emptyList(),
            )
        }
    }
}
