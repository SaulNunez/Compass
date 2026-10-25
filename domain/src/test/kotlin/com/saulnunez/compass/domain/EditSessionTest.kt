package com.saulnunez.compass.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class EditSessionTest {

    private val song = newSong(
        id = SongId("edit"),
        title = "Edit",
        artist = "",
        startingMeasure = Measure(TimeSignature(4, 4), KeySignature.C_MAJOR, Tempo(120)),
        trackName = "Guitar",
        instrument = TuningPreset.GUITAR_STANDARD.instrument(),
    )

    private fun EditSession.measure(index: Int) = song.tracks[0].voice.measures[index]

    @Test
    fun `typing a fret writes a Note on the cursor's string`() {
        val session = EditSession.start(song).typeDigit(3)

        assertEquals(listOf(Note(string = 1, fret = 3, duration = Duration.QUARTER)), session.measure(0))
    }

    private fun EditSession.type(vararg digits: Int) = digits.fold(this) { session, digit -> session.typeDigit(digit) }

    private fun EditSession.fretAtCursor() = (measure(cursor.measure)[cursor.beat] as Note).fret

    @Test
    fun `two digits typed in a row make a two-digit fret`() {
        val session = EditSession.start(song).type(1, 2)

        assertEquals(listOf(Note(1, 12, Duration.QUARTER)), session.measure(0))
    }

    @Test
    fun `a second digit that would go past the highest fret starts a new fret`() {
        assertEquals(5, EditSession.start(song).type(2, 5).fretAtCursor())
        assertEquals(5, EditSession.start(song).type(3, 5).fretAtCursor())
        assertEquals(24, EditSession.start(song).type(2, 4).fretAtCursor())
    }

    @Test
    fun `a third digit starts a new fret`() {
        assertEquals(3, EditSession.start(song).type(1, 2, 3).fretAtCursor())
    }

    @Test
    fun `an open string followed by a digit is that digit`() {
        assertEquals(7, EditSession.start(song).type(0, 7).fretAtCursor())
    }

    @Test
    fun `moving to the next beat after a Note lets the next Note follow it`() {
        val session = EditSession.start(song).type(3).moveToNextBeat().moveToStringBelow().type(5)

        assertEquals(listOf(Note(1, 3, Duration.QUARTER), Note(2, 5, Duration.QUARTER)), session.measure(0))
        assertEquals(Cursor(measure = 0, beat = 1, string = 2), session.cursor)
    }

    @Test
    fun `past the free beat after the last Note the cursor moves to the next Measure`() {
        val once = EditSession.start(song).type(3).moveToNextBeat()
        assertEquals(Cursor(measure = 0, beat = 1, string = 1), once.cursor)

        assertEquals(Cursor(measure = 1, beat = 0, string = 1), once.moveToNextBeat().cursor)
    }

    @Test
    fun `past the last beat of a full Measure the cursor moves to the next Measure`() {
        val session = EditSession.start(song).chooseNoteValue(NoteValue.WHOLE).type(0).moveToNextBeat()

        assertEquals(Cursor(measure = 1, beat = 0, string = 1), session.cursor)
    }

    @Test
    fun `before the first beat the cursor moves to the last beat of the previous Measure`() {
        val session = EditSession.start(song)
            .type(1).moveToNextBeat().type(2).moveToNextBeat()
            .moveToNextMeasure().moveToPreviousBeat()

        // Measure 1 is incomplete, so its last cursor position is the free beat after its two Notes.
        assertEquals(Cursor(measure = 0, beat = 2, string = 1), session.cursor)
    }

    @Test
    fun `the cursor moves by Measure and stops at the first and last Measures`() {
        val start = EditSession.start(song)

        assertEquals(Cursor(3, 0, 1), start.moveToNextMeasure().moveToNextMeasure().moveToNextMeasure().moveToNextMeasure().cursor)
        assertEquals(Cursor(0, 0, 1), start.moveToPreviousMeasure().cursor)
        assertEquals(Cursor(0, 0, 1), start.moveToPreviousBeat().cursor)
        assertEquals(Cursor(0, 0, 1), start.moveToNextMeasure().moveToPreviousMeasure().cursor)
    }

    @Test
    fun `the cursor can jump straight to a Measure, keeping its string`() {
        val session = EditSession.start(song).moveToStringBelow().moveToMeasure(2)

        assertEquals(Cursor(2, 0, 2), session.cursor)
        assertEquals(Cursor(3, 0, 2), session.moveToMeasure(99).cursor)
    }

    @Test
    fun `the cursor moves between the Instrument's strings and no further`() {
        val start = EditSession.start(song)

        assertEquals(1, start.moveToStringAbove().cursor.string)
        assertEquals(6, (1..10).fold(start) { session, _ -> session.moveToStringBelow() }.cursor.string)
    }

    @Test
    fun `moving the cursor between digits ends a two-digit fret`() {
        val session = EditSession.start(song).type(1).moveToStringBelow().type(2)

        assertEquals(listOf(Chord(listOf(Note(1, 1, Duration.QUARTER), Note(2, 2, Duration.QUARTER)))), session.measure(0))
    }

    @Test
    fun `the chosen Duration follows the Note under the cursor`() {
        val session = EditSession.start(song).chooseNoteValue(NoteValue.EIGHTH).type(7).moveToNextBeat()
            .chooseNoteValue(NoteValue.HALF).moveToPreviousBeat()

        assertEquals(Duration.EIGHTH, session.duration)
    }

    @Test
    fun `choosing a note value on a Note changes its Duration`() {
        val session = EditSession.start(song).type(3).chooseNoteValue(NoteValue.SIXTEENTH)

        assertEquals(listOf(Note(1, 3, Duration.SIXTEENTH)), session.measure(0))
    }

    @Test
    fun `a dot can be added to and taken off a Note`() {
        val dotted = EditSession.start(song).type(3).toggleDot()
        assertEquals(listOf(Note(1, 3, Duration(NoteValue.QUARTER, isDotted = true))), dotted.measure(0))

        assertEquals(listOf(Note(1, 3, Duration.QUARTER)), dotted.toggleDot().measure(0))
    }

    @Test
    fun `a triplet chosen on the free beat applies to the Notes written next`() {
        val session = EditSession.start(song).chooseNoteValue(NoteValue.EIGHTH).toggleTriplet()
            .type(5).moveToNextBeat().type(7)

        val tripletEighth = Duration(NoteValue.EIGHTH, isTriplet = true)
        assertEquals(listOf(Note(1, 5, tripletEighth), Note(1, 7, tripletEighth)), session.measure(0))
    }

    @Test
    fun `a Rest is written with the chosen Duration`() {
        val session = EditSession.start(song).chooseNoteValue(NoteValue.HALF).writeRest()

        assertEquals(listOf(Rest(Duration.HALF)), session.measure(0))
    }

    @Test
    fun `a Rest written over a Note keeps its Duration, and a fret over a Rest does too`() {
        val rest = EditSession.start(song).chooseNoteValue(NoteValue.EIGHTH).type(3).writeRest()
        assertEquals(listOf(Rest(Duration.EIGHTH)), rest.measure(0))

        assertEquals(listOf(Note(1, 9, Duration.EIGHTH)), rest.type(9).measure(0))
    }

    @Test
    fun `deleting a Note moves the following ones up`() {
        val session = EditSession.start(song)
            .type(1).moveToNextBeat().type(2).moveToNextBeat().type(3)
            .moveToPreviousBeat().delete()

        assertEquals(listOf(Note(1, 1, Duration.QUARTER), Note(1, 3, Duration.QUARTER)), session.measure(0))
        assertEquals(Cursor(0, 1, 1), session.cursor)
    }

    @Test
    fun `deleting a Rest removes it, and deleting on the free beat changes nothing`() {
        val rest = EditSession.start(song).writeRest()
        assertEquals(emptyList<VoiceElement>(), rest.delete().measure(0))

        val nothing = EditSession.start(song).delete()
        assertEquals(song, nothing.song)
    }

    @Test
    fun `a new session has nothing to undo or redo`() {
        val session = EditSession.start(song)

        assertFalse(session.canUndo)
        assertFalse(session.canRedo)
    }

    @Test
    fun `undo restores the exact Song before each edit, and redo brings the edits back`() {
        val first = EditSession.start(song).type(3)
        val second = first.moveToNextBeat().writeRest()

        val undoneOnce = second.undo()
        assertEquals(first.song, undoneOnce.song)
        val undoneTwice = undoneOnce.undo()
        assertEquals(song, undoneTwice.song)
        assertFalse(undoneTwice.canUndo)

        assertEquals(second.song, undoneTwice.redo().redo().song)
        assertFalse(undoneTwice.redo().redo().canRedo)
    }

    @Test
    fun `undo puts the cursor back where the edit was made`() {
        val session = EditSession.start(song).moveToStringBelow().type(3).moveToNextMeasure().undo()

        assertEquals(Cursor(0, 0, 2), session.cursor)
    }

    @Test
    fun `an action that leaves the Song as it was adds nothing to undo`() {
        val session = EditSession.start(song).type(3).chooseNoteValue(NoteValue.QUARTER).undo()

        assertEquals(song, session.song)
    }

    @Test
    fun `a two-digit fret is undone in one step`() {
        val session = EditSession.start(song).type(1, 2).undo()

        assertEquals(song, session.song)
    }

    @Test
    fun `a new edit after undo drops what could have been redone`() {
        val session = EditSession.start(song).type(3).undo().type(5)

        assertFalse(session.canRedo)
        assertEquals(listOf(Note(1, 5, Duration.QUARTER)), session.measure(0))
    }

    @Test
    fun `cursor moves and choosing a Duration for the next Note are not edits`() {
        val session = EditSession.start(song).moveToNextMeasure().chooseNoteValue(NoteValue.HALF).toggleDot()

        assertFalse(session.canUndo)
    }

    @Test
    fun `undo and redo with nothing to undo or redo change nothing`() {
        val session = EditSession.start(song).type(4)

        assertEquals(session.song, session.redo().song)
        assertEquals(song, session.undo().undo().song)
    }
}
