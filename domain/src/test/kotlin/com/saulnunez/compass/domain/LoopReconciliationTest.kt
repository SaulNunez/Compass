package com.saulnunez.compass.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoopReconciliationTest {

    private val fourFour = Measure(TimeSignature(4, 4), KeySignature.C_MAJOR, Tempo(120))

    /** A Song of [count] Measures in one Track, with [sections]. */
    private fun song(count: Int, sections: List<Section> = emptyList()) = Song(
        id = SongId("loops"),
        title = "Loops",
        artist = "",
        measures = List(count) { fourFour },
        tracks = listOf(Track("Guitar", TuningPreset.GUITAR_STANDARD.instrument(), Voice(List(count) { emptyList() }))),
        sections = sections,
    )

    private fun looping(loop: Loop) = PracticeSettings(tempoPercentage = TempoPercentage(80), loop = loop)

    /** Measure [number] counted from 1, as people read it. */
    private fun EditSession.atMeasure(number: Int) = moveToMeasure(number - 1)

    @Test
    fun `Loop 17-24 becomes 17-23 after deleting Measure 20`() {
        val settings = looping(Loop.OfMeasures(first = 16, last = 23))

        val session = EditSession.start(song(30)).atMeasure(20).deleteMeasure()

        assertEquals(looping(Loop.OfMeasures(first = 16, last = 22)), session.reconcile(settings))
    }

    @Test
    fun `a Measure-range Loop moves along when Measures are inserted or deleted before it`() {
        val settings = looping(Loop.OfMeasures(first = 4, last = 6))

        val inserted = EditSession.start(song(10)).atMeasure(2).insertMeasureBefore().insertMeasureBefore()
        val deleted = EditSession.start(song(10)).atMeasure(1).deleteMeasure()

        assertEquals(Loop.OfMeasures(first = 6, last = 8), inserted.reconcile(settings).loop)
        assertEquals(Loop.OfMeasures(first = 3, last = 5), deleted.reconcile(settings).loop)
    }

    @Test
    fun `a Measure-range Loop takes in Measures inserted inside it, but not ones inserted after it`() {
        val settings = looping(Loop.OfMeasures(first = 2, last = 4))

        val inside = EditSession.start(song(8)).atMeasure(4).insertMeasureBefore()
        val after = EditSession.start(song(8)).atMeasure(5).insertMeasureAfter()

        assertEquals(Loop.OfMeasures(first = 2, last = 5), inside.reconcile(settings).loop)
        assertEquals(Loop.OfMeasures(first = 2, last = 4), after.reconcile(settings).loop)
    }

    @Test
    fun `a Measure-range Loop is cleared only when all its Measures are deleted`() {
        val settings = looping(Loop.OfMeasures(first = 2, last = 3))

        val oneLeft = EditSession.start(song(6)).atMeasure(3).deleteMeasure()
        val noneLeft = oneLeft.atMeasure(3).deleteMeasure()

        assertEquals(Loop.OfMeasures(first = 2, last = 2), oneLeft.reconcile(settings).loop)
        assertEquals(PracticeSettings(tempoPercentage = TempoPercentage(80)), noneLeft.reconcile(settings))
    }

    @Test
    fun `undoing an edit brings the Loop back to how it was`() {
        val settings = looping(Loop.OfMeasures(first = 2, last = 2))

        val deleted = EditSession.start(song(6)).atMeasure(3).deleteMeasure()
        val undone = deleted.undo()

        assertNull(deleted.reconcile(settings).loop)
        assertEquals(settings, undone.reconcile(settings))
        assertNull(undone.redo().reconcile(settings).loop)
    }

    @Test
    fun `a Section Loop follows its resized Section`() {
        val sections = listOf(Section("Intro", 0, 1), Section("Verse", 2, 5))
        val settings = looping(Loop.OfSection(section = 1))

        val session = EditSession.start(song(10, sections)).atMeasure(3).resizeSection(first = 3, last = 8)

        val reconciled = session.reconcile(settings)
        assertEquals(3..8, reconciled.loop!!.measuresIn(session.song))
    }

    @Test
    fun `a Section Loop follows its Section when another Section is added before it or removed`() {
        val sections = listOf(Section("Intro", 0, 1), Section("Verse", 4, 5))
        val settings = looping(Loop.OfSection(section = 1))

        val added = EditSession.start(song(8, sections)).addSection("Riff", first = 2, last = 3)
        val removed = EditSession.start(song(8, sections)).atMeasure(1).removeSection()

        assertEquals(Section("Verse", 4, 5), added.song.sections[(added.reconcile(settings).loop as Loop.OfSection).section])
        assertEquals(Section("Verse", 4, 5), removed.song.sections[(removed.reconcile(settings).loop as Loop.OfSection).section])
    }

    @Test
    fun `a Section Loop follows its Section as Measures are inserted and deleted`() {
        val sections = listOf(Section("Verse", 2, 4))
        val settings = looping(Loop.OfSection(section = 0))

        val session = EditSession.start(song(8, sections)).atMeasure(1).insertMeasureBefore().atMeasure(5).deleteMeasure()

        assertEquals(3..4, session.reconcile(settings).loop!!.measuresIn(session.song))
    }

    @Test
    fun `a Section Loop is cleared when its Section is removed`() {
        val sections = listOf(Section("Verse", 2, 4))
        val settings = looping(Loop.OfSection(section = 0))

        val removed = EditSession.start(song(8, sections)).atMeasure(3).removeSection()
        val deleted = EditSession.start(song(8, sections)).atMeasure(3).deleteMeasure().deleteMeasure().deleteMeasure()

        assertNull(removed.reconcile(settings).loop)
        assertNull(deleted.reconcile(settings).loop)
    }

    @Test
    fun `edits that leave the Measures and Sections in place leave the Loop alone`() {
        val settings = looping(Loop.OfSection(section = 0))

        val session = EditSession.start(song(4, listOf(Section("Verse", 1, 2)))).atMeasure(2)
            .typeDigit(5).renameSection("Chorus").changeTempo(Tempo(90))

        assertEquals(settings, session.reconcile(settings))
    }

    @Test
    fun `pasted Measures move a Loop after them along`() {
        val settings = looping(Loop.OfMeasures(first = 5, last = 6))

        val session = EditSession.start(song(8, listOf(Section("Verse", 0, 1)))).copySection().atMeasure(3).pasteMeasures()

        assertEquals(Loop.OfMeasures(first = 7, last = 8), session.reconcile(settings).loop)
    }
}
