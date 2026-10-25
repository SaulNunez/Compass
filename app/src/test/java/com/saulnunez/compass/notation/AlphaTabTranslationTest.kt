package com.saulnunez.compass.notation

import alphaTab.Settings
import com.saulnunez.compass.domain.Duration
import com.saulnunez.compass.domain.Instrument
import com.saulnunez.compass.domain.InstrumentKind
import com.saulnunez.compass.domain.KeySignature
import com.saulnunez.compass.domain.Measure
import com.saulnunez.compass.domain.Note
import com.saulnunez.compass.domain.NoteValue
import com.saulnunez.compass.domain.Pitch
import com.saulnunez.compass.domain.Rest
import com.saulnunez.compass.domain.Song
import com.saulnunez.compass.domain.SongId
import com.saulnunez.compass.domain.Tempo
import com.saulnunez.compass.domain.TimeSignature
import com.saulnunez.compass.domain.Track
import com.saulnunez.compass.domain.Tuning
import com.saulnunez.compass.domain.Voice
import alphaTab.model.Clef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import alphaTab.model.Duration as AlphaTabDuration
import alphaTab.model.KeySignature as AlphaTabKeySignature

class AlphaTabTranslationTest {

    private val song = Song(
        id = SongId("translation-check"),
        title = "Translation check",
        artist = "Compass",
        measures = listOf(
            Measure(TimeSignature(4, 4), KeySignature.C_MAJOR, Tempo(120)),
            Measure(TimeSignature(3, 4), KeySignature(fifths = 1), Tempo(90)),
        ),
        tracks = listOf(
            Track(
                name = "Lead guitar",
                instrument = Instrument(
                    kind = InstrumentKind.GUITAR,
                    tuning = Tuning(listOf(64, 59, 55, 50, 45, 40).map(::Pitch)),
                ),
                voice = Voice(
                    listOf(
                        listOf(
                            Note(string = 1, fret = 0, duration = Duration.QUARTER),
                            Note(string = 6, fret = 3, duration = Duration.QUARTER),
                            Rest(Duration.HALF),
                        ),
                        listOf(
                            Note(string = 3, fret = 12, duration = Duration(NoteValue.QUARTER, isDotted = true)),
                            Note(string = 3, fret = 10, duration = Duration(NoteValue.EIGHTH, isTriplet = true)),
                            Note(string = 3, fret = 9, duration = Duration(NoteValue.EIGHTH, isTriplet = true)),
                            Rest(Duration(NoteValue.EIGHTH, isTriplet = true)),
                            Rest(Duration.EIGHTH),
                        ),
                    ),
                ),
            ),
        ),
    )

    private val score = song.toAlphaTabScore(Settings())

    @Test
    fun `title, artist and Track name carry over`() {
        assertEquals("Translation check", score.title)
        assertEquals("Compass", score.artist)
        assertEquals(1, score.tracks.length.toInt())
        assertEquals("Lead guitar", score.tracks[0].name)
    }

    @Test
    fun `Tuning is listed from the top tablature line down`() {
        val tuning = score.tracks[0].staves[0].tuning
        assertEquals(listOf(64, 59, 55, 50, 45, 40), (0 until tuning.length.toInt()).map { tuning[it].toInt() })
    }

    @Test
    fun `Notes keep their fret and land on the matching tablature line`() {
        val beats = score.tracks[0].staves[0].bars[0].voices[0].beats

        // alphaTab counts strings from the lowest, so Compass string 1 (high E) is alphaTab string 6.
        val highE = beats[0].notes[0]
        assertEquals(6, highE.string.toInt())
        assertEquals(0, highE.fret.toInt())

        val lowE = beats[1].notes[0]
        assertEquals(1, lowE.string.toInt())
        assertEquals(3, lowE.fret.toInt())

        val g = score.tracks[0].staves[0].bars[1].voices[0].beats[0].notes[0]
        assertEquals(4, g.string.toInt())
        assertEquals(12, g.fret.toInt())
    }

    @Test
    fun `durations and Rests carry over`() {
        val beats = score.tracks[0].staves[0].bars[0].voices[0].beats
        assertEquals(AlphaTabDuration.Quarter, beats[0].duration)
        assertEquals(AlphaTabDuration.Quarter, beats[1].duration)
        assertTrue(beats[2].isRest)
        assertEquals(AlphaTabDuration.Half, beats[2].duration)
    }

    @Test
    fun `each Measure keeps its time signature, key signature and Tempo`() {
        assertEquals(2, score.masterBars.length.toInt())

        val second = score.masterBars[1]
        assertEquals(3, second.timeSignatureNumerator.toInt())
        assertEquals(4, second.timeSignatureDenominator.toInt())
        assertEquals(AlphaTabKeySignature.G, score.tracks[0].staves[0].bars[1].keySignature)

        assertEquals(120, score.masterBars[0].tempoAutomation!!.value.toInt())
        assertEquals(90, second.tempoAutomation!!.value.toInt())
    }

    @Test
    fun `Standard notation writes guitar an octave above its sounding pitch on the treble clef`() {
        // Guitar music is written an octave higher than it sounds, so the open high E (E4) is written as E5.
        val highE = score.tracks[0].staves[0].bars[0].voices[0].beats[0].notes[0]
        assertEquals(64, highE.realValue.toInt())
        assertEquals(76, highE.displayValue.toInt())
        assertEquals(Clef.G2, score.tracks[0].staves[0].bars[0].clef)
    }

    private val bassSong = song.copy(
        tracks = listOf(
            Track(
                name = "Bass",
                instrument = Instrument(
                    kind = InstrumentKind.BASS,
                    tuning = Tuning(listOf(43, 38, 33, 28).map(::Pitch)),
                ),
                voice = Voice(
                    listOf(
                        listOf(Note(string = 4, fret = 0, duration = Duration.WHOLE)),
                        emptyList(),
                    ),
                ),
            ),
        ),
    )

    @Test
    fun `Standard notation writes bass an octave above its sounding pitch on the bass clef`() {
        val bassScore = bassSong.toAlphaTabScore(Settings())
        // The open low E (E1) is written as E2.
        val lowE = bassScore.tracks[0].staves[0].bars[0].voices[0].beats[0].notes[0]
        assertEquals(28, lowE.realValue.toInt())
        assertEquals(40, lowE.displayValue.toInt())
        assertEquals(Clef.F4, bassScore.tracks[0].staves[0].bars[0].clef)
        assertEquals(Clef.F4, bassScore.tracks[0].staves[0].bars[1].clef)
    }

    @Test
    fun `every Track carries each Measure's key signature`() {
        val twoTracks = song.copy(tracks = song.tracks + bassSong.tracks).toAlphaTabScore(Settings())
        (0 until 2).forEach { trackIndex ->
            val bars = twoTracks.tracks[trackIndex].staves[0].bars
            assertEquals(AlphaTabKeySignature.C, bars[0].keySignature)
            assertEquals(AlphaTabKeySignature.G, bars[1].keySignature)
        }
    }

    @Test
    fun `dotted Durations and triplets carry over`() {
        val beats = score.tracks[0].staves[0].bars[1].voices[0].beats

        assertEquals(AlphaTabDuration.Quarter, beats[0].duration)
        assertEquals(1, beats[0].dots.toInt())
        assertFalse(beats[0].hasTuplet)

        (1..3).forEach { index ->
            assertEquals(AlphaTabDuration.Eighth, beats[index].duration)
            assertEquals(0, beats[index].dots.toInt())
            assertEquals(3, beats[index].tupletNumerator.toInt())
            assertEquals(2, beats[index].tupletDenominator.toInt())
        }
        assertTrue(beats[3].isRest)
        assertFalse(beats[4].hasTuplet)
    }
}
