package com.saulnunez.compass.storage

import com.saulnunez.compass.domain.Chord
import com.saulnunez.compass.domain.Duration
import com.saulnunez.compass.domain.InstrumentKind
import com.saulnunez.compass.domain.KeySignature
import com.saulnunez.compass.domain.Measure
import com.saulnunez.compass.domain.Note
import com.saulnunez.compass.domain.NoteValue
import com.saulnunez.compass.domain.Rest
import com.saulnunez.compass.domain.Section
import com.saulnunez.compass.domain.SongId
import com.saulnunez.compass.domain.Sound
import com.saulnunez.compass.domain.Track
import com.saulnunez.compass.domain.Tempo
import com.saulnunez.compass.domain.Technique
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
    fun `each Track's Sound survives a save and load`() {
        val band = song.copy(
            tracks = song.tracks.map { it.copy(sound = Sound.NYLON_ACOUSTIC_GUITAR) } +
                Track("Bass", TuningPreset.BASS_FIVE_STRING_STANDARD.instrument(), Voice(List(song.measures.size) { emptyList() }), Sound.FRETLESS_BASS),
        )

        assertEquals(band, SongFormat.decode(SongFormat.encode(band)))
    }

    @Test
    fun `a version 4 document is upgraded on load, its Tracks getting the default Sounds`() {
        val versionFour = """
            {
              "formatVersion": 4,
              "id": "duo",
              "title": "Duo",
              "artist": "",
              "measures": [{ "beats": 4, "beatUnit": 4, "keyFifths": 0, "keyIsMinor": false, "tempo": 100 }],
              "tracks": [
                {
                  "name": "Guitar",
                  "instrument": "guitar",
                  "openStrings": [64, 59, 55, 50, 45, 40],
                  "measures": [[{ "type": "note", "string": 1, "fret": 5, "duration": { "value": "quarter", "dotted": true, "triplet": false }, "tied": false, "techniques": [] }]]
                },
                { "name": "Bass", "instrument": "bass", "openStrings": [43, 38, 33, 28], "measures": [[]] }
              ],
              "sections": [{ "name": "Intro", "firstMeasure": 0, "lastMeasure": 0 }]
            }
        """.trimIndent()

        val song = SongFormat.decode(versionFour)

        assertEquals(listOf(Sound.CLEAN_ELECTRIC_GUITAR, Sound.FINGERED_BASS), song.tracks.map { it.sound })
        assertEquals(listOf(Note(1, 5, Duration(NoteValue.QUARTER, isDotted = true))), song.tracks[0].voice.measures.single())
        assertEquals(listOf(Section("Intro", 0, 0)), song.sections)
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
    fun `Chords, ties and every Technique survive a save and load`() {
        val quarter = Duration.QUARTER
        val written = song.copy(
            tracks = song.tracks.map { track ->
                track.copy(
                    voice = Voice(
                        track.voice.measures.mapIndexed { index, elements ->
                            when (index) {
                                0 -> listOf(
                                    Chord(listOf(Note(1, 0, quarter), Note(2, 1, quarter, techniques = setOf(Technique.VIBRATO)))),
                                    Chord(listOf(Note(1, 0, quarter, isTied = true), Note(3, 2, quarter))),
                                    Note(3, 2, Duration.HALF, isTied = true, techniques = setOf(Technique.HAMMER_ON_PULL_OFF, Technique.PALM_MUTE)),
                                )
                                1 -> Technique.entries.map { Note(4, 5, Duration.SIXTEENTH, techniques = setOf(it)) }
                                else -> elements
                            }
                        },
                    ),
                )
            },
        )

        assertEquals(written, SongFormat.decode(SongFormat.encode(written)))
    }

    @Test
    fun `a version 3 document is upgraded on load, keeping its Sections`() {
        val versionThree = """
            {
              "formatVersion": 3,
              "id": "riff",
              "title": "Riff",
              "artist": "Band",
              "measures": [
                { "beats": 4, "beatUnit": 4, "keyFifths": 0, "keyIsMinor": false, "tempo": 100 },
                { "beats": 4, "beatUnit": 4, "keyFifths": 0, "keyIsMinor": false, "tempo": 100 }
              ],
              "tracks": [{
                "name": "Guitar",
                "instrument": "guitar",
                "openStrings": [64, 59, 55, 50, 45, 40],
                "measures": [
                  [{ "type": "note", "string": 2, "fret": 7, "duration": { "value": "half", "dotted": false, "triplet": false } }],
                  [{ "type": "rest", "duration": { "value": "whole", "dotted": false, "triplet": false } }]
                ]
              }],
              "sections": [{ "name": "Intro", "firstMeasure": 0, "lastMeasure": 0 }, { "name": "Verse", "firstMeasure": 1, "lastMeasure": 1 }]
            }
        """.trimIndent()

        val song = SongFormat.decode(versionThree)

        assertEquals(listOf(Section("Intro", 0, 0), Section("Verse", 1, 1)), song.sections)
        assertEquals(
            listOf(listOf(Note(2, 7, Duration.HALF)), listOf(Rest(Duration.WHOLE))),
            song.tracks.single().voice.measures,
        )
    }

    @Test
    fun `a version 2 document with Notes and Rests is upgraded on load`() {
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
                  { "type": "note", "string": 6, "fret": 3, "duration": { "value": "quarter", "dotted": true, "triplet": false } },
                  { "type": "rest", "duration": { "value": "eighth", "dotted": false, "triplet": false } }
                ]]
              }]
            }
        """.trimIndent()

        val song = SongFormat.decode(versionTwo)

        assertEquals(
            listOf(Note(6, 3, Duration(NoteValue.QUARTER, isDotted = true)), Rest(Duration.EIGHTH)),
            song.tracks.single().voice.measures.single(),
        )
    }

    @Test
    fun `a Chord saved with slap or pop on only some Notes opens with it on every Note, as it was shown and played`() {
        val document = """
            {
              "formatVersion": 5,
              "id": "slap",
              "title": "Slap",
              "artist": "",
              "measures": [{ "beats": 4, "beatUnit": 4, "keyFifths": 0, "keyIsMinor": false, "tempo": 100 }],
              "tracks": [
                {
                  "name": "Bass",
                  "instrument": "bass",
                  "openStrings": [43, 38, 33, 28],
                  "sound": "slap-bass",
                  "measures": [[{
                    "type": "chord",
                    "duration": { "value": "quarter", "dotted": false, "triplet": false },
                    "notes": [
                      { "string": 3, "fret": 2, "tied": false, "techniques": ["vibrato"] },
                      { "string": 4, "fret": 0, "tied": false, "techniques": ["pop"] }
                    ]
                  }]]
                }
              ],
              "sections": []
            }
        """.trimIndent()

        val song = SongFormat.decode(document)

        assertEquals(
            listOf(
                Chord(
                    listOf(
                        Note(3, 2, Duration.QUARTER, techniques = setOf(Technique.VIBRATO, Technique.POP)),
                        Note(4, 0, Duration.QUARTER, techniques = setOf(Technique.POP)),
                    ),
                ),
            ),
            song.tracks[0].voice.measures.single(),
        )
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
