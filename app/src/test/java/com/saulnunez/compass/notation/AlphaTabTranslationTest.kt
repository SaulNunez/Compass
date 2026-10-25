package com.saulnunez.compass.notation

import com.saulnunez.compass.domain.Duration
import com.saulnunez.compass.domain.Instrument
import com.saulnunez.compass.domain.InstrumentKind
import com.saulnunez.compass.domain.KeySignature
import com.saulnunez.compass.domain.Measure
import com.saulnunez.compass.domain.Note
import com.saulnunez.compass.domain.Pitch
import com.saulnunez.compass.domain.Rest
import com.saulnunez.compass.domain.Song
import com.saulnunez.compass.domain.Tempo
import com.saulnunez.compass.domain.TimeSignature
import com.saulnunez.compass.domain.Track
import com.saulnunez.compass.domain.Tuning
import com.saulnunez.compass.domain.Voice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import alphaTab.model.Duration as AlphaTabDuration
import alphaTab.model.KeySignature as AlphaTabKeySignature

class AlphaTabTranslationTest {

    private val song = Song(
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
                        listOf(Note(string = 3, fret = 12, duration = Duration.HALF), Rest(Duration.QUARTER)),
                    ),
                ),
            ),
        ),
    )

    private val score = song.toAlphaTabScore()

    @Test
    fun songDetailsCarryOver() {
        assertEquals("Translation check", score.title)
        assertEquals("Compass", score.artist)
        assertEquals(1, score.tracks.length.toInt())
        assertEquals("Lead guitar", score.tracks[0].name)
    }

    @Test
    fun tuningIsListedFromTheTopTablatureLine() {
        val tuning = score.tracks[0].staves[0].tuning
        assertEquals(listOf(64, 59, 55, 50, 45, 40), (0 until tuning.length.toInt()).map { tuning[it].toInt() })
    }

    @Test
    fun notesKeepTheirFretAndLandOnTheMatchingTablatureLine() {
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
    fun durationsAndRestsCarryOver() {
        val beats = score.tracks[0].staves[0].bars[0].voices[0].beats
        assertEquals(AlphaTabDuration.Quarter, beats[0].duration)
        assertEquals(AlphaTabDuration.Quarter, beats[1].duration)
        assertTrue(beats[2].isRest)
        assertEquals(AlphaTabDuration.Half, beats[2].duration)
    }

    @Test
    fun eachMeasureKeepsItsTimeSignatureKeySignatureAndTempo() {
        assertEquals(2, score.masterBars.length.toInt())

        val second = score.masterBars[1]
        assertEquals(3, second.timeSignatureNumerator.toInt())
        assertEquals(4, second.timeSignatureDenominator.toInt())
        assertEquals(AlphaTabKeySignature.G, score.tracks[0].staves[0].bars[1].keySignature)

        assertEquals(120, score.masterBars[0].tempoAutomation!!.value.toInt())
        assertEquals(90, second.tempoAutomation!!.value.toInt())
    }
}
