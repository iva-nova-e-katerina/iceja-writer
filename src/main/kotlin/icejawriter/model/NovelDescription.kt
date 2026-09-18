package icejawriter.model

/**
 * Short story description ("logline + plot" of the whole novel) stored in
 * `novel.json`. Its serialized byte size must never exceed 2048 UTF-8 bytes
 * (ФРМ-05). Every field is an empty string by default so that a newly created
 * project stays valid.
 */
data class NovelDescription(
    val title: String = "",
    val author: String = "",
    val logline: String = "",
    val theme: String = "",
    val plot: String = "",
)
