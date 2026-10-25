package com.saulnunez.compass.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewSongTest {

    private val song = newSong(
        id = SongId("song-1"),
        title = "Waltz",
        artist = "Me",
        startingMeasure = Measure(TimeSignature(3, 4), KeySignature(fifths = -1, isMinor = true), Tempo(90)),
        trackName = "Bass",
        instrument = TuningPreset.BASS_STANDARD.instrument(),
    )

    @Test
    fun `a new Song starts with four empty Measures in the chosen Tempo, time signature and key signature`() {
        assertEquals(4, song.measures.size)
        song.measures.forEach { measure ->
            assertEquals(Tempo(90), measure.tempo)
            assertEquals(TimeSignature(3, 4), measure.timeSignature)
            assertEquals(KeySignature(fifths = -1, isMinor = true), measure.keySignature)
        }
    }

    @Test
    fun `a new Song has one empty Track on the chosen Instrument`() {
        val track = song.tracks.single()
        assertEquals("Bass", track.name)
        assertEquals(InstrumentKind.BASS, track.instrument.kind)
        assertTrue(track.voice.measures.all { it.isEmpty() })
    }

    @Test
    fun `a new Song keeps its identity, title and artist`() {
        assertEquals(SongId("song-1"), song.id)
        assertEquals("Waltz", song.title)
        assertEquals("Me", song.artist)
    }
}
