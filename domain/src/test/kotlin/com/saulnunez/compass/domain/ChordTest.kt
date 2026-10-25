package com.saulnunez.compass.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ChordTest {

    private val song = newSong(
        id = SongId("chords"),
        title = "Chords",
        artist = "",
        startingMeasure = Measure(TimeSignature(4, 4), KeySignature.C_MAJOR, Tempo(120)),
        trackName = "Guitar",
        instrument = TuningPreset.GUITAR_STANDARD.instrument(),
    )

    private fun EditSession.measure(index: Int) = song.tracks[0].voice.measures[index]

    private val quarter = Duration.QUARTER

    @Test
    fun `frets typed on several strings at one beat make a Chord`() {
        val session = EditSession.start(song)
            .typeDigit(0).moveToStringBelow().typeDigit(1).moveToStringBelow().typeDigit(0)

        assertEquals(
            listOf(Chord(listOf(Note(1, 0, quarter), Note(2, 1, quarter), Note(3, 0, quarter)))),
            session.measure(0),
        )
    }

    private val twoStrings = EditSession.start(song).typeDigit(3).moveToStringBelow().typeDigit(5)

    @Test
    fun `a fret typed on a string the Chord already has replaces that Note`() {
        val session = twoStrings.moveToStringAbove().typeDigit(7)

        assertEquals(listOf(Chord(listOf(Note(1, 7, quarter), Note(2, 5, quarter)))), session.measure(0))
    }

    @Test
    fun `a Note added to a Chord takes the Chord's Duration`() {
        val session = EditSession.start(song).chooseNoteValue(NoteValue.HALF).typeDigit(3)
            .moveToNextBeat().chooseNoteValue(NoteValue.EIGHTH).moveToPreviousBeat()
            .moveToStringBelow().typeDigit(5)

        assertEquals(listOf(Chord(listOf(Note(1, 3, Duration.HALF), Note(2, 5, Duration.HALF)))), session.measure(0))
    }

    @Test
    fun `choosing a note value on a Chord changes every Note's Duration`() {
        val session = twoStrings.chooseNoteValue(NoteValue.EIGHTH)

        assertEquals(listOf(Chord(listOf(Note(1, 3, Duration.EIGHTH), Note(2, 5, Duration.EIGHTH)))), session.measure(0))
        assertEquals(Duration.EIGHTH, session.duration)
    }

    @Test
    fun `deleting in a Chord removes only the Note on the cursor's string`() {
        val session = twoStrings.delete()

        assertEquals(listOf(Note(1, 3, quarter)), session.measure(0))
        assertEquals(Cursor(0, 0, 2), session.cursor)
    }

    @Test
    fun `deleting in a Chord on a string without a Note removes the whole Chord`() {
        val session = twoStrings.moveToStringBelow().delete()

        assertEquals(emptyList<VoiceElement>(), session.measure(0))
    }

    @Test
    fun `a Rest written over a Chord replaces all its Notes`() {
        assertEquals(listOf(Rest(quarter)), twoStrings.writeRest().measure(0))
    }

    @Test
    fun `a Chord is undone one Note at a time`() {
        val undone = twoStrings.undo()

        assertEquals(listOf(Note(1, 3, quarter)), undone.measure(0))
    }
}
