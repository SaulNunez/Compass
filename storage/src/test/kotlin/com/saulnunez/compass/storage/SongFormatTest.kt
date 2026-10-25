package com.saulnunez.compass.storage

import com.saulnunez.compass.domain.Duration
import com.saulnunez.compass.domain.InstrumentKind
import com.saulnunez.compass.domain.KeySignature
import com.saulnunez.compass.domain.Measure
import com.saulnunez.compass.domain.Note
import com.saulnunez.compass.domain.NoteValue
import com.saulnunez.compass.domain.Rest
import com.saulnunez.compass.domain.Section
import com.saulnunez.compass.domain.SongId
import com.saulnunez.compass.domain.Tempo
import com.saulnunez.compass.domain.TimeSignature
import com.saulnunez.compass.domain.TuningPreset
import com.saulnunez.compass.domain.Voice
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
    fun `dotted and triplet Durations survive a save and load`() {
        val dotted = Duration(NoteValue.QUARTER, isDotted = true)
        val triplet = Duration(NoteValue.EIGHTH, isTriplet = true)
        val written = song.copy(
            tracks = song.tracks.map { track ->
                track.copy(
                    voice = Voice(
                        track.voice.measures.mapIndexed { index, elements ->
                            if (index == 0) listOf(Note(2, 12, dotted), Rest(triplet), Note(6, 0, Duration(NoteValue.HALF, true, true))) else elements
                        },
                    ),
                )
            },
        )

        assertEquals(written, SongFormat.decode(SongFormat.encode(written)))
    }

    @Test
    fun `Sections survive a save and load`() {
        val withSections = song.copy(sections = listOf(Section("Intro", 0, 0), Section("Verse", 1, 3)))

        assertEquals(withSections, SongFormat.decode(SongFormat.encode(withSections)))
    }

    @Test
    fun `a version 2 document is upgraded on load, with no Sections`() {
        val versionTwo = """
            {
              "formatVersion": 2,
              "id": "riff",
              "title": "Riff",
              "artist": "Band",
              "measures": [{ "beats": 4, "beatUnit": 4, "keyFifths": 0, "keyIsMinor": false, "tempo": 100 }],
              "tracks": [{
                "name": "Guitar",
                "instrument": "guitar",
                "openStrings": [64, 59, 55, 50, 45, 40],
                "measures": [[
                  { "type": "note", "string": 1, "fret": 5, "duration": { "value": "quarter", "dotted": true, "triplet": false } }
                ]]
              }]
            }
        """.trimIndent()

        val song = SongFormat.decode(versionTwo)

        assertEquals(emptyList<Section>(), song.sections)
        assertEquals(listOf(Note(1, 5, Duration(NoteValue.QUARTER, isDotted = true))), song.tracks.single().voice.measures.single())
    }

    @Test
    fun `a version 1 document is upgraded on load`() {
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
        val fromTheFuture = SongFormat.encode(song).replace(Regex("\"formatVersion\":\\d+"), "\"formatVersion\":99")

        val error = assertThrows(UnsupportedSongFormatException::class.java) { SongFormat.decode(fromTheFuture) }
        assertEquals(99, error.formatVersion)
    }
}
