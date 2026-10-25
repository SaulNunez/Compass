package com.saulnunez.compass.notation

import alphaTab.Settings
import com.saulnunez.compass.domain.Duration
import com.saulnunez.compass.domain.EditSession
import com.saulnunez.compass.domain.KeySignature
import com.saulnunez.compass.domain.Measure
import com.saulnunez.compass.domain.Note
import com.saulnunez.compass.domain.SongId
import com.saulnunez.compass.domain.Tempo
import com.saulnunez.compass.domain.TimeSignature
import com.saulnunez.compass.domain.Track
import com.saulnunez.compass.domain.TuningPreset
import com.saulnunez.compass.domain.Voice
import com.saulnunez.compass.domain.WriteElement
import com.saulnunez.compass.domain.newSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import alphaTab.model.KeySignature as AlphaTabKeySignature

/** Measure structure changes made in Edit mode reach the alphaTab Score the views draw and Playback plays. */
class MeasureStructureTranslationTest {

    private val song = newSong(
        id = SongId("structure"),
        title = "Structure",
        artist = "",
        startingMeasure = Measure(TimeSignature(4, 4), KeySignature.C_MAJOR, Tempo(120)),
        trackName = "Guitar",
        instrument = TuningPreset.GUITAR_STANDARD.instrument(),
    ).let { song ->
        song.copy(tracks = song.tracks + Track("Bass", TuningPreset.BASS_STANDARD.instrument(), Voice(List(4) { emptyList() })))
    }.let { WriteElement(track = 1, measure = 1, beat = 0, element = Note(4, 5, Duration.WHOLE)).applyTo(it) }

    @Test
    fun `an inserted Measure is an empty bar in every Track`() {
        val score = EditSession.start(song).insertMeasureBefore().song.toAlphaTabScore(Settings())

        assertEquals(5, score.masterBars.length.toInt())
        (0 until 2).forEach { track ->
            val bars = score.tracks[track].staves[0].bars
            assertEquals(5, bars.length.toInt())
            assertTrue(bars[0].voices[0].beats[0].isEmpty)
        }
        // The bass Note written in Measure 2 is now in Measure 3.
        assertEquals(5, score.tracks[1].staves[0].bars[2].voices[0].beats[0].notes[0].fret.toInt())
    }

    @Test
    fun `a deleted Measure is gone from every Track`() {
        val score = EditSession.start(song).deleteMeasure().song.toAlphaTabScore(Settings())

        assertEquals(3, score.masterBars.length.toInt())
        (0 until 2).forEach { track -> assertEquals(3, score.tracks[track].staves[0].bars.length.toInt()) }
        assertEquals(5, score.tracks[1].staves[0].bars[0].voices[0].beats[0].notes[0].fret.toInt())
    }

    @Test
    fun `time signature, key signature and Tempo changes reach the bars from that Measure on`() {
        val edited = EditSession.start(song).moveToMeasure(2)
            .changeTimeSignature(TimeSignature(6, 8))
            .changeKeySignature(KeySignature(fifths = -1))
            .changeTempo(Tempo(80))
            .song
        val score = edited.toAlphaTabScore(Settings())

        assertEquals(listOf(4, 4, 6, 6), (0 until 4).map { score.masterBars[it].timeSignatureNumerator.toInt() })
        assertEquals(listOf(4, 4, 8, 8), (0 until 4).map { score.masterBars[it].timeSignatureDenominator.toInt() })
        (0 until 2).forEach { track ->
            val bars = score.tracks[track].staves[0].bars
            assertEquals(
                listOf(AlphaTabKeySignature.C, AlphaTabKeySignature.C, AlphaTabKeySignature.F, AlphaTabKeySignature.F),
                (0 until 4).map { bars[it].keySignature },
            )
        }
        // Playback follows the Tempo: it is set at the start and changes only where the Song changes it.
        assertEquals(120, score.masterBars[0].tempoAutomation!!.value.toInt())
        assertNull(score.masterBars[1].tempoAutomation)
        assertEquals(80, score.masterBars[2].tempoAutomation!!.value.toInt())
        assertNull(score.masterBars[3].tempoAutomation)
    }
}
