package com.saulnunez.compass.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MeasureStructureTest {

    private val fourFour = Measure(TimeSignature(4, 4), KeySignature.C_MAJOR, Tempo(120))

    private val twoTracks = newSong(
        id = SongId("structure"),
        title = "Structure",
        artist = "",
        startingMeasure = fourFour,
        trackName = "Guitar",
        instrument = TuningPreset.GUITAR_STANDARD.instrument(),
    ).let { song ->
        song.copy(tracks = song.tracks + Track("Bass", TuningPreset.BASS_STANDARD.instrument(), Voice(List(4) { emptyList() })))
    }

    /** Measure n (0-based) of every Track holds a single quarter Note on fret n. */
    private val song = (0 until 4).fold(twoTracks) { song, measure ->
        (0 until 2).fold(song) { s, track -> WriteElement(track, measure, 0, Note(1, measure, Duration.QUARTER)).applyTo(s) }
    }

    private fun Song.fretsOf(track: Int) = tracks[track].voice.measures.map { (it.singleOrNull() as Note?)?.fret }

    @Test
    fun `inserting a Measure adds an empty Measure to every Track at that point`() {
        val edited = EditSession.start(song).moveToMeasure(2).insertMeasureBefore().song

        assertEquals(5, edited.measures.size)
        assertEquals(listOf(0, 1, null, 2, 3), edited.fretsOf(0))
        assertEquals(listOf(0, 1, null, 2, 3), edited.fretsOf(1))
    }

    @Test
    fun `a Measure can be inserted after the cursor's, even after the last, and the cursor moves into it`() {
        val session = EditSession.start(song).moveToMeasure(3).insertMeasureAfter()

        assertEquals(listOf(0, 1, 2, 3, null), session.song.fretsOf(0))
        assertEquals(listOf(0, 1, 2, 3, null), session.song.fretsOf(1))
        assertEquals(Cursor(measure = 4, beat = 0, string = 1), session.cursor)
    }

    @Test
    fun `an inserted Measure carries on the signatures and Tempo before it, so a later change stays put`() {
        val threeFour = fourFour.copy(timeSignature = TimeSignature(3, 4), tempo = Tempo(90))
        val changing = song.copy(measures = listOf(fourFour, fourFour, threeFour, threeFour))

        val atChange = EditSession.start(changing).moveToMeasure(2).insertMeasureBefore().song
        assertEquals(listOf(fourFour, fourFour, fourFour, threeFour, threeFour), atChange.measures)

        val atStart = EditSession.start(changing).insertMeasureBefore().song
        assertEquals(listOf(fourFour, fourFour, fourFour, threeFour, threeFour), atStart.measures)
    }

    @Test
    fun `deleting a Measure removes it from every Track`() {
        val session = EditSession.start(song).moveToMeasure(1).deleteMeasure()

        assertEquals(3, session.song.measures.size)
        assertEquals(listOf(0, 2, 3), session.song.fretsOf(0))
        assertEquals(listOf(0, 2, 3), session.song.fretsOf(1))
        assertEquals(Cursor(measure = 1, beat = 0, string = 1), session.cursor)
    }

    @Test
    fun `deleting the last Measure moves the cursor to the one before it`() {
        val session = EditSession.start(song).moveToMeasure(3).deleteMeasure()

        assertEquals(listOf(0, 1, 2), session.song.fretsOf(0))
        assertEquals(2, session.cursor.measure)
    }

    @Test
    fun `a Song's only Measure cannot be deleted`() {
        val single = EditSession.start(song).deleteMeasure().deleteMeasure().deleteMeasure()

        assertEquals(1, single.song.measures.size)
        assertEquals(false, single.canDeleteMeasure)
        assertEquals(single.song, single.deleteMeasure().song)
    }

    private val threeFour = TimeSignature(3, 4)

    @Test
    fun `a time signature change applies from the cursor's Measure to the end`() {
        val edited = EditSession.start(song).moveToMeasure(2).changeTimeSignature(threeFour).song

        assertEquals(listOf(TimeSignature(4, 4), TimeSignature(4, 4), threeFour, threeFour), edited.measures.map { it.timeSignature })
    }

    @Test
    fun `a time signature that fills the Measure moves the cursor off the free beat`() {
        val session = EditSession.start(twoTracks)
            .typeDigit(1).moveToNextBeat().typeDigit(2).moveToNextBeat().typeDigit(3).moveToNextBeat()
            .changeTimeSignature(threeFour)

        assertEquals(Cursor(measure = 0, beat = 2, string = 1), session.cursor)
    }

    @Test
    fun `a change lasts only until the next change already written`() {
        val sixEight = TimeSignature(6, 8)
        val edited = EditSession.start(song)
            .moveToMeasure(2).changeTimeSignature(sixEight)
            .moveToMeasure(0).changeTimeSignature(threeFour).song

        assertEquals(listOf(threeFour, threeFour, sixEight, sixEight), edited.measures.map { it.timeSignature })
    }

    @Test
    fun `key signature and Tempo change at any Measure, leaving the other settings alone`() {
        val gMajor = KeySignature(fifths = 1)
        val edited = EditSession.start(song)
            .moveToMeasure(1).changeKeySignature(gMajor)
            .moveToMeasure(3).changeTempo(Tempo(90)).song

        assertEquals(listOf(KeySignature.C_MAJOR, gMajor, gMajor, gMajor), edited.measures.map { it.keySignature })
        assertEquals(listOf(120, 120, 120, 90), edited.measures.map { it.tempo.beatsPerMinute })
        assertEquals(List(4) { TimeSignature(4, 4) }, edited.measures.map { it.timeSignature })
        assertEquals(song.tracks, edited.tracks)
    }

    @Test
    fun `Measure inserts, deletes and changes are undone and redone exactly`() {
        val inserted = EditSession.start(song).moveToMeasure(1).insertMeasureAfter()
        val changed = inserted.changeTempo(Tempo(60))
        val deleted = changed.moveToMeasure(0).deleteMeasure()

        val undone = deleted.undo()
        assertEquals(changed.song, undone.song)
        assertEquals(inserted.song, undone.undo().song)
        assertEquals(song, undone.undo().undo().song)
        assertEquals(deleted.song, undone.undo().undo().redo().redo().redo().song)
    }

    @Test
    fun `changing several settings of a Measure at once is one edit, each lasting until its own next change`() {
        val start = EditSession.start(song).moveToMeasure(3).changeTempo(Tempo(100))
        val changed = start.moveToMeasure(1)
            .changeMeasure(Measure(threeFour, KeySignature(fifths = 2), Tempo(140)))

        assertEquals(listOf(TimeSignature(4, 4), threeFour, threeFour, threeFour), changed.song.measures.map { it.timeSignature })
        assertEquals(listOf(0, 2, 2, 2), changed.song.measures.map { it.keySignature.fifths })
        assertEquals(listOf(120, 140, 140, 100), changed.song.measures.map { it.tempo.beatsPerMinute })
        assertEquals(start.song, changed.undo().song)
    }

    @Test
    fun `undoing a Measure delete puts the cursor back on that Measure`() {
        val session = EditSession.start(song).moveToMeasure(3).deleteMeasure().undo()

        assertEquals(Cursor(measure = 3, beat = 0, string = 1), session.cursor)
    }
}
