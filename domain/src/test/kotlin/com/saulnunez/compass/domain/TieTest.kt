package com.saulnunez.compass.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TieTest {

    private val song = newSong(
        id = SongId("ties"),
        title = "Ties",
        artist = "",
        startingMeasure = Measure(TimeSignature(4, 4), KeySignature.C_MAJOR, Tempo(120)),
        trackName = "Guitar",
        instrument = TuningPreset.GUITAR_STANDARD.instrument(),
    )

    private fun EditSession.measure(index: Int) = song.tracks[0].voice.measures[index]

    private val quarter = Duration.QUARTER

    /** Fret 5 on string 1, then fret 0 on string 1 at the next beat. */
    private val twoNotes = EditSession.start(song).typeDigit(5).moveToNextBeat().typeDigit(0)

    @Test
    fun `a tied Note takes the fret of the Note before it on its string`() {
        val session = twoNotes.toggleTie()

        assertEquals(listOf(Note(1, 5, quarter), Note(1, 5, quarter, isTied = true)), session.measure(0))
    }

    @Test
    fun `a tie can be taken off again`() {
        val session = twoNotes.toggleTie().toggleTie()

        assertEquals(listOf(Note(1, 5, quarter), Note(1, 5, quarter)), session.measure(0))
    }

    @Test
    fun `a tie reaches across the Measure line`() {
        val session = EditSession.start(song).chooseNoteValue(NoteValue.WHOLE).typeDigit(7)
            .moveToNextBeat().typeDigit(0).toggleTie()

        assertEquals(listOf(Note(1, 7, Duration.WHOLE, isTied = true)), session.measure(1))
    }

    @Test
    fun `a Note with no Note before it on its string cannot be tied`() {
        val first = EditSession.start(song).typeDigit(5)
        assertFalse(first.canTie)
        assertEquals(first.song, first.toggleTie().song)

        val otherString = first.moveToNextBeat().moveToStringBelow().typeDigit(3)
        assertFalse(otherString.canTie)
        assertTrue(twoNotes.canTie)
    }

    @Test
    fun `one Note of a Chord can be tied to the Chord before it`() {
        val session = EditSession.start(song)
            .typeDigit(2).moveToStringBelow().typeDigit(3)
            .moveToNextBeat().typeDigit(0).toggleTie()

        assertEquals(listOf(Note(2, 3, quarter, isTied = true)), session.measure(0).drop(1))
    }

    @Test
    fun `changing the fret a Note is tied to changes the tied Note too`() {
        val session = twoNotes.toggleTie().moveToPreviousBeat().typeDigit(9)

        assertEquals(listOf(Note(1, 9, quarter), Note(1, 9, quarter, isTied = true)), session.measure(0))
    }

    @Test
    fun `a Note whose tied-from Note is deleted is no longer tied`() {
        val session = twoNotes.toggleTie().moveToPreviousBeat().delete()

        assertEquals(listOf(Note(1, 5, quarter)), session.measure(0))
    }

    @Test
    fun `a fret typed over a tied Note writes a new, untied Note`() {
        val session = twoNotes.toggleTie().typeDigit(3)

        assertEquals(Note(1, 3, quarter), session.measure(0)[1])
    }
}
