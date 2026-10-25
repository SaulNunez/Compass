package com.saulnunez.compass.notation

import alphaTab.Settings
import alphaTab.midi.AlphaSynthMidiFileHandler
import alphaTab.midi.MidiFile
import alphaTab.midi.MidiFileGenerator
import alphaTab.midi.MidiTickLookup
import com.saulnunez.compass.domain.Duration
import com.saulnunez.compass.domain.KeySignature
import com.saulnunez.compass.domain.Measure
import com.saulnunez.compass.domain.Note
import com.saulnunez.compass.domain.Song
import com.saulnunez.compass.domain.SongId
import com.saulnunez.compass.domain.Tempo
import com.saulnunez.compass.domain.TimeSignature
import com.saulnunez.compass.domain.Track
import com.saulnunez.compass.domain.TuningPreset
import com.saulnunez.compass.domain.Voice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The range of alphaTab's MIDI its player loops for a Loop's Measures. */
class LoopRangeTest {

    private val song = Song(
        id = SongId("loop"),
        title = "Loop",
        artist = "",
        measures = listOf(
            Measure(TimeSignature(4, 4), KeySignature.C_MAJOR, Tempo(120)),
            Measure(TimeSignature(3, 4), KeySignature.C_MAJOR, Tempo(90)),
            Measure(TimeSignature(4, 4), KeySignature.C_MAJOR, Tempo(90)),
        ),
        tracks = listOf(
            Track(
                name = "Guitar",
                instrument = TuningPreset.GUITAR_STANDARD.instrument(),
                // An incomplete Measure still lasts its whole time signature, so a repeat starts on time.
                voice = Voice(listOf(listOf(Note(1, 0, Duration.QUARTER)), emptyList(), listOf(Note(1, 2, Duration.HALF)))),
            ),
        ),
    )

    private val score = song.toAlphaTabScore(Settings())
    private val tickLookup: MidiTickLookup = run {
        val settings = Settings()
        val generator = MidiFileGenerator(score, settings, AlphaSynthMidiFileHandler(MidiFile()))
        generator.generate()
        generator.tickLookup
    }

    @Test
    fun `a Loop's range runs from the start of its first Measure to the end of its last`() {
        val range = tickLookup.playbackRangeOf(score, 1..2)!!

        assertEquals(4 * QUARTER, range.startTick, 0.0)
        assertEquals(11 * QUARTER, range.endTick, 0.0)
    }

    @Test
    fun `a one-Measure Loop at the start covers just that Measure`() {
        val range = tickLookup.playbackRangeOf(score, 0..0)!!

        assertEquals(0.0, range.startTick, 0.0)
        assertEquals(4 * QUARTER, range.endTick, 0.0)
    }

    @Test
    fun `Measures the Score doesn't have give no range`() {
        assertNull(tickLookup.playbackRangeOf(score, 2..3))
    }

    private companion object {
        const val QUARTER = 960.0
    }
}
