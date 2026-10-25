package com.saulnunez.compass.storage

import com.saulnunez.compass.domain.KeySignature
import com.saulnunez.compass.domain.SongId
import com.saulnunez.compass.domain.Tempo
import com.saulnunez.compass.domain.TimeSignature
import com.saulnunez.compass.domain.TuningPreset
import com.saulnunez.compass.domain.newSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SongFormatTest {

    private val song = newSong(
        id = SongId("s"),
        title = "Song",
        artist = "Artist",
        tempo = Tempo(120),
        timeSignature = TimeSignature(4, 4),
        keySignature = KeySignature.C_MAJOR,
        trackName = "Guitar",
        instrument = TuningPreset.GUITAR_STANDARD.instrument(),
    )

    @Test
    fun `an encoded Song decodes back unchanged`() {
        assertEquals(song, SongFormat.decode(SongFormat.encode(song)))
    }

    @Test
    fun `a document from a newer Compass is refused`() {
        val fromTheFuture = SongFormat.encode(song).replace("\"formatVersion\":1", "\"formatVersion\":99")

        val error = assertThrows(UnsupportedSongFormatException::class.java) { SongFormat.decode(fromTheFuture) }
        assertEquals(99, error.formatVersion)
    }
}
