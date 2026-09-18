package icejawriter.model

/**
 * Project metadata stored in the ZIP entry `manifest.json` (ФРМ-02).
 *
 * @property formatVersion format version of the .ijw archive; the application
 *   reads any value not greater than the supported current format and refuses
 *   to open a newer format produced by a future IceJA Writer build.
 */
data class Manifest(
    val formatVersion: Int,
    val appVersion: String,
    val title: String,
    val createdAt: String,
    val updatedAt: String,
)
