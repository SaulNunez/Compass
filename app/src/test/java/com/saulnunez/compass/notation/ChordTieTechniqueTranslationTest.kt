package com.saulnunez.compass.notation

import alphaTab.Settings
import alphaTab.model.BendType
import alphaTab.model.SlideOutType
import alphaTab.model.VibratoType
import com.saulnunez.compass.domain.Chord
import com.saulnunez.compass.domain.Duration
import com.saulnunez.compass.domain.KeySignature
import com.saulnunez.compass.domain.Measure
import com.saulnunez.compass.domain.Note
import com.saulnunez.compass.domain.Song
import com.saulnunez.compass.domain.SongId
import com.saulnunez.compass.domain.Technique
import com.saulnunez.compass.domain.Tempo
import com.saulnunez.compass.domain.TimeSignature
import com.saulnunez.compass.domain.TuningPreset
import com.saulnunez.compass.domain.VoiceElement
import com.saulnunez.compass.domain.Voice
import com.saulnunez.compass.domain.newSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import alphaTab.model.Beat as AlphaTabBeat
import alphaTab.model.Note as AlphaTabNote

class ChordTieTechniqueTranslationTest {

    private val quarter = Duration.QUARTER

    private fun songOf(preset: TuningPreset, vararg measures: List<VoiceElement>): Song {
        val song = newSong(
            id = SongId("techniques"),
            title = "Techniques",
            artist = "",
            startingMeasure = Measure(TimeSignature(4, 4), KeySignature.C_MAJOR, Tempo(120)),
            trackName = "Track",
            instrument = preset.instrument(),
        )
        return song.copy(
            measures = List(measures.size) { song.measures[0] },
            tracks = song.tracks.map { it.copy(voice = Voice(measures.toList())) },
        )
    }

    private fun Song.beats(measure: Int = 0) =
        toAlphaTabScore(Settings()).tracks[0].staves[0].bars[measure].voices[0].beats

    private fun AlphaTabBeat.noteOnString(alphaTabString: Int): AlphaTabNote =
        (0 until notes.length.toInt()).map { notes[it] }.single { it.string.toInt() == alphaTabString }

    /** The first Note of a guitar Song where [note] (on string 1) is followed by fret 7 on string 1. */
    private fun guitarNoteWith(technique: Technique): AlphaTabNote =
        songOf(TuningPreset.GUITAR_STANDARD, listOf(Note(1, 5, quarter, techniques = setOf(technique)), Note(1, 7, quarter)))
            .beats()[0].notes[0]

    @Test
    fun `a Chord is one beat with a Note on each of its strings`() {
        val beats = songOf(
            TuningPreset.GUITAR_STANDARD,
            listOf(Chord(listOf(Note(1, 0, quarter), Note(2, 1, quarter), Note(3, 0, quarter)))),
        ).beats()

        assertEquals(1, beats.length.toInt())
        val chord = beats[0]
        assertEquals(3, chord.notes.length.toInt())
        // Compass strings 1, 2, 3 of six are alphaTab strings 6, 5, 4.
        assertEquals(0, chord.noteOnString(6).fret.toInt())
        assertEquals(1, chord.noteOnString(5).fret.toInt())
        assertEquals(0, chord.noteOnString(4).fret.toInt())
    }

    @Test
    fun `a tied Note is the tie destination of the Note before it on its string`() {
        val beats = songOf(
            TuningPreset.GUITAR_STANDARD,
            listOf(
                Chord(listOf(Note(1, 3, quarter), Note(2, 5, quarter))),
                Chord(listOf(Note(1, 0, quarter), Note(2, 5, quarter, isTied = true))),
            ),
        ).beats()

        val tied = beats[1].noteOnString(5)
        assertTrue(tied.isTieDestination)
        assertSame(beats[0].noteOnString(5), tied.tieOrigin)
        assertFalse(beats[1].noteOnString(6).isTieDestination)
    }

    @Test
    fun `a tie reaches across the Measure line`() {
        val score = songOf(
            TuningPreset.GUITAR_STANDARD,
            listOf(Note(1, 7, Duration.WHOLE)),
            listOf(Note(1, 7, Duration.WHOLE, isTied = true)),
        ).toAlphaTabScore(Settings())

        val tied = score.tracks[0].staves[0].bars[1].voices[0].beats[0].notes[0]
        assertTrue(tied.isTieDestination)
        assertSame(score.tracks[0].staves[0].bars[0].voices[0].beats[0].notes[0], tied.tieOrigin)
    }

    @Test
    fun `hammer-on and pull-off leads into the next Note on the string`() {
        val origin = guitarNoteWith(Technique.HAMMER_ON_PULL_OFF)

        assertTrue(origin.isHammerPullOrigin)
        assertNotNull(origin.hammerPullDestination)
    }

    @Test
    fun `a slide is a legato slide into the next Note on the string`() {
        val origin = guitarNoteWith(Technique.SLIDE)

        assertEquals(SlideOutType.Legato, origin.slideOutType)
        assertNotNull(origin.slideTarget)
    }

    @Test
    fun `a bend goes up a whole step and holds`() {
        val bent = guitarNoteWith(Technique.BEND)

        assertTrue(bent.hasBend)
        assertEquals(BendType.Bend, bent.bendType)
        assertEquals(WHOLE_STEP_IN_QUARTER_TONES, bent.maxBendPoint!!.value.toInt())
    }

    @Test
    fun `a bend and release goes up a whole step and back down`() {
        val bent = guitarNoteWith(Technique.BEND_AND_RELEASE)

        assertTrue(bent.hasBend)
        assertEquals(BendType.BendRelease, bent.bendType)
        assertEquals(WHOLE_STEP_IN_QUARTER_TONES, bent.maxBendPoint!!.value.toInt())
        assertEquals(0, bent.bendPoints!![bent.bendPoints!!.length.toInt() - 1].value.toInt())
    }

    @Test
    fun `vibrato, palm mute and dead notes are marked on the Note`() {
        assertEquals(VibratoType.Slight, guitarNoteWith(Technique.VIBRATO).vibrato)
        assertTrue(guitarNoteWith(Technique.PALM_MUTE).isPalmMute)
        assertTrue(guitarNoteWith(Technique.DEAD_NOTE).isDead)
    }

    @Test
    fun `a Note without Techniques has none in alphaTab`() {
        val plain = songOf(TuningPreset.GUITAR_STANDARD, listOf(Note(1, 5, quarter), Note(1, 7, quarter))).beats()[0].notes[0]

        assertFalse(plain.isHammerPullOrigin)
        assertEquals(SlideOutType.None, plain.slideOutType)
        assertFalse(plain.hasBend)
        assertEquals(VibratoType.None, plain.vibrato)
        assertFalse(plain.isPalmMute)
        assertFalse(plain.isDead)
        assertFalse(plain.isTieDestination)
    }

    @Test
    fun `slap and pop mark the bass beat`() {
        val beats = songOf(
            TuningPreset.BASS_STANDARD,
            listOf(
                Note(4, 0, quarter, techniques = setOf(Technique.SLAP)),
                Note(2, 2, quarter, techniques = setOf(Technique.POP)),
                Note(4, 0, quarter),
            ),
        ).beats()

        assertTrue(beats[0].slap)
        assertFalse(beats[0].pop)
        assertTrue(beats[1].pop)
        assertFalse(beats[1].slap)
        assertFalse(beats[2].slap || beats[2].pop)
    }

    @Test
    fun `no dynamics are drawn, since Compass has none`() {
        val score = songOf(TuningPreset.GUITAR_STANDARD, listOf(Note(1, 5, quarter))).toAlphaTabScore(Settings())

        assertTrue(score.stylesheet.hideDynamics)
    }

    private companion object {
        /** alphaTab measures bends in quarter tones. */
        const val WHOLE_STEP_IN_QUARTER_TONES = 4
    }
}
