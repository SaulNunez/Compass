package com.saulnunez.compass.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionsTest {

    private val fourFour = Measure(TimeSignature(4, 4), KeySignature.C_MAJOR, Tempo(120))

    /** Eight Measures in two Tracks; Measure n (0-based) of every Track holds a single quarter Note on fret n. */
    private val song = newSong(
        id = SongId("sections"),
        title = "Sections",
        artist = "",
        startingMeasure = fourFour,
        trackName = "Guitar",
        instrument = TuningPreset.GUITAR_STANDARD.instrument(),
    ).let { song ->
        val eight = song.copy(
            measures = List(8) { fourFour },
            tracks = listOf(
                song.tracks[0].copy(voice = Voice(List(8) { emptyList() })),
                Track("Bass", TuningPreset.BASS_STANDARD.instrument(), Voice(List(8) { emptyList() })),
            ),
        )
        (0 until 8).fold(eight) { s, measure ->
            (0 until 2).fold(s) { t, track -> WriteElement(track, measure, 0, Note(1, measure, Duration.QUARTER)).applyTo(t) }
        }
    }

    @Test
    fun `a Section names a range of Measures`() {
        val edited = EditSession.start(song).addSection("Verse", first = 2, last = 3).song

        assertEquals(listOf(Section("Verse", first = 2, last = 3)), edited.sections)
    }

    /** Intro is Measures 1–2, Verse 4–6 (1-based); Measures 3, 7 and 8 belong to no Section. */
    private val withSections = EditSession.start(song)
        .addSection("Intro", first = 0, last = 1)
        .addSection("Verse", first = 3, last = 5)

    @Test
    fun `a Measure inserted before a Section moves the Section along`() {
        val edited = withSections.moveToMeasure(2).insertMeasureBefore().song

        assertEquals(listOf(Section("Intro", 0, 1), Section("Verse", 4, 6)), edited.sections)
    }

    @Test
    fun `a Measure inserted inside a Section makes it longer`() {
        val edited = withSections.moveToMeasure(4).insertMeasureBefore().song

        assertEquals(listOf(Section("Intro", 0, 1), Section("Verse", 3, 6)), edited.sections)
    }

    @Test
    fun `a Measure inserted at the start of a Section goes before it, and one right after its end stays out of it`() {
        val before = withSections.moveToMeasure(3).insertMeasureBefore().song
        assertEquals(listOf(Section("Intro", 0, 1), Section("Verse", 4, 6)), before.sections)

        val after = withSections.moveToMeasure(5).insertMeasureAfter().song
        assertEquals(listOf(Section("Intro", 0, 1), Section("Verse", 3, 5)), after.sections)
    }

    @Test
    fun `deleting a Measure before a Section moves the Section back`() {
        val edited = withSections.moveToMeasure(2).deleteMeasure().song

        assertEquals(listOf(Section("Intro", 0, 1), Section("Verse", 2, 4)), edited.sections)
    }

    @Test
    fun `deleting a Measure of a Section makes it shorter`() {
        val first = withSections.moveToMeasure(3).deleteMeasure().song
        assertEquals(listOf(Section("Intro", 0, 1), Section("Verse", 3, 4)), first.sections)

        val last = withSections.moveToMeasure(5).deleteMeasure().song
        assertEquals(listOf(Section("Intro", 0, 1), Section("Verse", 3, 4)), last.sections)
    }

    @Test
    fun `deleting a Section's only Measure removes the Section`() {
        val edited = withSections.moveToMeasure(0).deleteMeasure().deleteMeasure().song

        assertEquals(listOf(Section("Verse", 1, 3)), edited.sections)
    }

    @Test
    fun `undoing a Measure deletion brings the Section back as it was`() {
        val session = withSections.moveToMeasure(0).deleteMeasure().deleteMeasure().undo().undo()

        assertEquals(withSections.song.sections, session.song.sections)
    }

    @Test
    fun `the Section under the cursor is the one holding its Measure`() {
        assertEquals(Section("Verse", 3, 5), withSections.moveToMeasure(4).sectionAtCursor)
        assertNull(withSections.moveToMeasure(2).sectionAtCursor)
    }

    @Test
    fun `the Section under the cursor can be renamed, resized and removed, each undoable`() {
        val atVerse = withSections.moveToMeasure(4)

        val renamed = atVerse.renameSection("Chorus")
        assertEquals(listOf(Section("Intro", 0, 1), Section("Chorus", 3, 5)), renamed.song.sections)

        val resized = atVerse.resizeSection(first = 2, last = 6)
        assertEquals(listOf(Section("Intro", 0, 1), Section("Verse", 2, 6)), resized.song.sections)

        val removed = atVerse.removeSection()
        assertEquals(listOf(Section("Intro", 0, 1)), removed.song.sections)
        assertEquals(withSections.song.sections, removed.undo().song.sections)
    }

    @Test
    fun `renaming and resizing together is one edit`() {
        val changed = withSections.moveToMeasure(4).changeSection("Chorus", first = 4, last = 7)

        assertEquals(listOf(Section("Intro", 0, 1), Section("Chorus", 4, 7)), changed.song.sections)
        assertEquals(withSections.song.sections, changed.undo().song.sections)
    }

    @Test
    fun `a Section cannot share Measures with another`() {
        assertFalse(withSections.canAddSection(first = 1, last = 2))
        assertFalse(withSections.canAddSection(first = 2, last = 8))
        assertTrue(withSections.canAddSection(first = 2, last = 2))
        assertTrue(withSections.canAddSection(first = 6, last = 7))
        assertEquals(withSections.song, withSections.addSection("Bridge", first = 1, last = 2).song)

        // Resizing the cursor's Section may reuse its own Measures.
        assertTrue(withSections.moveToMeasure(4).canResizeSection(first = 2, last = 7))
        assertFalse(withSections.moveToMeasure(4).canResizeSection(first = 1, last = 7))
        assertEquals(withSections.song, withSections.moveToMeasure(4).resizeSection(first = 1, last = 7).song)
    }

    private fun Song.fretsOf(track: Int) = tracks[track].voice.measures.map { (it.singleOrNull() as Note?)?.fret }

    @Test
    fun `a Section's Measures can be copied and pasted before a Measure, in every Track`() {
        val threeFour = fourFour.copy(timeSignature = TimeSignature(3, 4))
        val session = withSections.moveToMeasure(0).changeTimeSignature(threeFour.timeSignature)
            .moveToMeasure(4).copySection()
            .moveToMeasure(7).pasteMeasures()

        val song = session.song
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 3, 4, 5, 7), song.fretsOf(0))
        assertEquals(listOf(0, 1, 2, 3, 4, 5, 6, 3, 4, 5, 7), song.fretsOf(1))
        // The pasted Measures keep their own settings; the Measure they were pasted before keeps its own too.
        assertEquals(List(11) { threeFour }, song.measures)
        // Pasting is not a Section of its own, and lands outside the Sections around it.
        assertEquals(listOf(Section("Intro", 0, 1), Section("Verse", 3, 5)), song.sections)
        assertEquals(Cursor(measure = 7, beat = 0, string = 1), session.cursor)
    }

    @Test
    fun `pasting into a Section makes it longer, and is undone in one step`() {
        val session = withSections.copySection().moveToMeasure(4).pasteMeasures()

        assertEquals(listOf(0, 1, 2, 3, 0, 1, 4, 5, 6, 7), session.song.fretsOf(0))
        assertEquals(listOf(Section("Intro", 0, 1), Section("Verse", 3, 7)), session.song.sections)
        assertEquals(withSections.song, session.undo().song)
    }

    @Test
    fun `there is nothing to paste until a Section is copied, and copying outside a Section copies nothing`() {
        assertFalse(withSections.canPaste)
        assertEquals(withSections.song, withSections.moveToMeasure(2).pasteMeasures().song)
        assertFalse(withSections.moveToMeasure(2).copySection().canPaste)
        assertTrue(withSections.copySection().canPaste)
    }

    @Test
    fun `what was copied stays the same when the Section changes afterwards`() {
        val session = withSections.copySection()
            .moveToMeasure(0).deleteMeasure()
            .moveToMeasure(6).pasteMeasures()

        assertEquals(listOf(1, 2, 3, 4, 5, 6, 0, 1, 7), session.song.fretsOf(0))
    }
}
