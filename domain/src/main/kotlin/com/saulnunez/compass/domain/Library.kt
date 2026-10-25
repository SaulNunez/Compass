package com.saulnunez.compass.domain

import java.text.Normalizer
import java.time.Instant

/** What the Library lists about one Song, without loading the Song itself. */
data class LibraryEntry(
    val id: SongId,
    val title: String,
    val artist: String,
    val lastPracticed: Instant?,
)

enum class LibraryOrder(internal val comparator: Comparator<LibraryEntry>) {
    TITLE(compareBy { it.title.forMatching() }),
    ARTIST(compareBy<LibraryEntry> { it.artist.forMatching() }.thenBy { it.title.forMatching() }),

    /** Most recently practiced first; Songs never practiced go last. */
    LAST_PRACTICED(
        compareBy<LibraryEntry, Instant?>(nullsLast(reverseOrder())) { it.lastPracticed }.thenBy { it.title.forMatching() },
    ),
}

/** The list of all Songs on the device. */
data class Library(val entries: List<LibraryEntry>) {

    /** The entries whose title or artist contains [search], in [order]. */
    fun browse(search: String, order: LibraryOrder): List<LibraryEntry> {
        val needle = search.trim().forMatching()
        return entries
            .filter { needle.isEmpty() || needle in it.title.forMatching() || needle in it.artist.forMatching() }
            .sortedWith(order.comparator)
    }
}

/** Lowercase without accents, so "Canción" matches "cancion". */
private fun String.forMatching(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(COMBINING_MARKS, "").lowercase()

private val COMBINING_MARKS = Regex("\\p{Mn}+")
