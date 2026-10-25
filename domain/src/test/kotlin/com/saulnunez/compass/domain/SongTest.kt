package com.saulnunez.compass.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SongTest {

    private val measure = Measure(
        timeSignature = TimeSignature(4, 4),
        keySignature = KeySignature.C_MAJOR,
        tempo = Tempo(120),
    )

    private val guitar = Instrument(
        kind = InstrumentKind.GUITAR,
        tuning = Tuning(listOf(64, 59, 55, 50, 45, 40).map(::Pitch)),
    )

    @Test
    fun `a Track that does not cover every Measure of the Song is rejected`() {
        val oneMeasureTrack = Track(
            name = "Guitar",
            instrument = guitar,
            voice = Voice(listOf(listOf(Rest(Duration.WHOLE)))),
        )

        assertThrows(IllegalArgumentException::class.java) {
            Song(
                id = SongId("two-measures"),
                title = "Two measures",
                artist = "Nobody",
                measures = listOf(measure, measure),
                tracks = listOf(oneMeasureTrack),
            )
        }
    }

    @Test
    fun `there are 30 key signatures, major and minor from 7 flats to 7 sharps`() {
        assertEquals(30, KeySignature.ALL.toSet().size)
        assertTrue(KeySignature(-7) in KeySignature.ALL)
        assertTrue(KeySignature(7, isMinor = true) in KeySignature.ALL)
    }

    @Test
    fun `a SongId that could reach outside the Library is rejected`() {
        listOf("../x", "a/b", "", ".", "with space").forEach { unsafe ->
            assertThrows(IllegalArgumentException::class.java) { SongId(unsafe) }
        }
    }

    @Test
    fun `generated and readable SongIds are accepted`() {
        SongId("3f2b8c1e-9a4d-4e57-b0c2-6d1f7a8e9b10")
        SongId("demo_song-1")
    }
}
