package com.saulnunez.compass.storage

import com.saulnunez.compass.domain.Duration
import com.saulnunez.compass.domain.InstrumentKind
import com.saulnunez.compass.domain.KeySignature
import com.saulnunez.compass.domain.Measure
import com.saulnunez.compass.domain.Note
import com.saulnunez.compass.domain.Rest
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
        startingMeasure = Measure(TimeSignature(4, 4), KeySignature.C_MAJOR, Tempo(120)),
        trackName = "Guitar",
        instrument = TuningPreset.GUITAR_STANDARD.instrument(),
    )

    @Test
    fun `an encoded Song decodes back unchanged`() {
        assertEquals(song, SongFormat.decode(SongFormat.encode(song)))
    }

    @Test
    fun `a version 1 document keeps loading whatever the domain types are renamed to`() {
        val versionOne = """
            {
              "formatVersion": 1,
              "id": "riff",
              "title": "Riff",
              "artist": "Band",
              "measures": [{ "beats": 4, "beatUnit": 4, "keyFifths": 1, "keyIsMinor": true, "tempo": 120 }],
              "tracks": [{
                "name": "Bass",
                "instrument": "bass",
                "openStrings": [43, 38, 33, 28],
                "measures": [[
                  { "type": "note", "string": 4, "fret": 3, "duration": "half" },
                  { "type": "rest", "duration": "quarter" },
                  { "type": "note", "string": 1, "fret": 0, "duration": "thirty-second" }
                ]]
              }]
            }
        """.trimIndent()

        val song = SongFormat.decode(versionOne)

        val track = song.tracks.single()
        assertEquals(InstrumentKind.BASS, track.instrument.kind)
        assertEquals(
            listOf(Note(4, 3, Duration.HALF), Rest(Duration.QUARTER), Note(1, 0, Duration.THIRTY_SECOND)),
            track.voice.measures.single(),
        )
        assertEquals(Measure(TimeSignature(4, 4), KeySignature(1, isMinor = true), Tempo(120)), song.measures.single())
    }

    @Test
    fun `a document with an unsafe SongId is refused`() {
        val unsafe = SongFormat.encode(song).replace("\"id\":\"s\"", "\"id\":\"../escape\"")

        assertThrows(IllegalArgumentException::class.java) { SongFormat.decode(unsafe) }
    }

    @Test
    fun `a document from a newer Compass is refused`() {
        val fromTheFuture = SongFormat.encode(song).replace("\"formatVersion\":1", "\"formatVersion\":99")

        val error = assertThrows(UnsupportedSongFormatException::class.java) { SongFormat.decode(fromTheFuture) }
        assertEquals(99, error.formatVersion)
    }
}
