package com.saulnunez.compass.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class LibraryTest {

    private val blackbird = LibraryEntry(SongId("1"), "Blackbird", "The Beatles", lastPracticed = Instant.parse("2026-10-01T10:00:00Z"))
    private val cancion = LibraryEntry(SongId("2"), "Canción del mariachi", "Los Lobos", lastPracticed = null)
    private val aerials = LibraryEntry(SongId("3"), "aerials", "System of a Down", lastPracticed = Instant.parse("2026-10-20T10:00:00Z"))
    private val library = Library(listOf(blackbird, cancion, aerials))

    private fun titles(entries: List<LibraryEntry>) = entries.map { it.title }

    @Test
    fun `searching matches the title or the artist, ignoring case`() {
        assertEquals(listOf("Blackbird"), titles(library.browse("BLACK", LibraryOrder.TITLE)))
        assertEquals(listOf("aerials"), titles(library.browse("system", LibraryOrder.TITLE)))
    }

    @Test
    fun `searching ignores accents`() {
        assertEquals(listOf("Canción del mariachi"), titles(library.browse("cancion", LibraryOrder.TITLE)))
    }

    @Test
    fun `sorting by title ignores case`() {
        assertEquals(
            listOf("aerials", "Blackbird", "Canción del mariachi"),
            titles(library.browse("", LibraryOrder.TITLE)),
        )
    }

    @Test
    fun `sorting by artist ignores case`() {
        assertEquals(
            listOf("Canción del mariachi", "aerials", "Blackbird"), // Los Lobos, System of a Down, The Beatles
            titles(library.browse("", LibraryOrder.ARTIST)),
        )
    }

    @Test
    fun `sorting by last practiced puts the most recent first and never-practiced Songs last`() {
        assertEquals(
            listOf("aerials", "Blackbird", "Canción del mariachi"),
            titles(library.browse("", LibraryOrder.LAST_PRACTICED)),
        )
    }

    @Test
    fun `an empty search lists every Song`() {
        assertEquals(3, library.browse("  ", LibraryOrder.TITLE).size)
    }
}
