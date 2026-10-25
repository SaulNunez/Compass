package com.saulnunez.compass.notation

import alphaTab.Settings
import alphaTab.midi.AlphaSynthMidiFileHandler
import alphaTab.midi.MidiFile
import alphaTab.midi.MidiFileGenerator
import alphaTab.midi.MidiTickLookup
import com.saulnunez.compass.domain.Cursor
import com.saulnunez.compass.domain.Duration
import com.saulnunez.compass.domain.KeySignature
import com.saulnunez.compass.domain.Measure
import com.saulnunez.compass.domain.Note
import com.saulnunez.compass.domain.Rest
import com.saulnunez.compass.domain.Song
import com.saulnunez.compass.domain.SongId
import com.saulnunez.compass.domain.Tempo
import com.saulnunez.compass.domain.TimeSignature
import com.saulnunez.compass.domain.Track
import com.saulnunez.compass.domain.TuningPreset
import com.saulnunez.compass.domain.Voice
import org.junit.Assert.assertEquals
import org.junit.Test

/** Where Playback from the Edit mode cursor starts in alphaTab's MIDI. */
class CursorTickTest {

    private val song = Song(
        id = SongId("cursor"),
        title = "Cursor",
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
                voice = Voice(
                    listOf(
                        listOf(Note(1, 0, Duration.QUARTER), Rest(Duration.QUARTER), Note(2, 1, Duration.HALF)),
                        listOf(Note(3, 2, Duration.EIGHTH), Note(3, 4, Duration.EIGHTH)),
                        emptyList(),
                    ),
                ),
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

    private fun tickAt(measure: Int, beat: Int) = tickLookup.startTickOf(score, Cursor(measure, beat, string = 1))

    @Test
    fun `Playback from the cursor starts at the element under it`() {
        assertEquals(0.0, tickAt(0, 0), 0.0)
        assertEquals(2.0 * QUARTER, tickAt(0, 2), 0.0)
        assertEquals(4.0 * QUARTER + QUARTER / 2, tickAt(1, 1), 0.0)
    }

    @Test
    fun `on the free beat after the last element it starts where that element ends`() {
        assertEquals(4.0 * QUARTER + QUARTER, tickAt(1, 2), 0.0)
    }

    @Test
    fun `in an unwritten Measure it starts at the Measure`() {
        assertEquals(7.0 * QUARTER, tickAt(2, 0), 0.0)
    }

    private companion object {
        const val QUARTER = 960.0
    }
}
