package com.saulnunez.compass.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MeasureFillTest {

    private val guitar = TuningPreset.GUITAR_STANDARD.instrument()

    private fun fillOf(timeSignature: TimeSignature, vararg elements: VoiceElement): MeasureFill {
        val song = Song(
            id = SongId("fill"),
            title = "Fill",
            artist = "",
            measures = listOf(Measure(timeSignature, KeySignature.C_MAJOR, Tempo(120))),
            tracks = listOf(Track("Guitar", guitar, Voice(listOf(elements.toList())))),
        )
        return song.measureFill(track = 0, measure = 0)
    }

    private fun note(duration: Duration) = Note(string = 1, fret = 0, duration = duration)

    @Test
    fun `a Measure with nothing written is unwritten`() {
        assertEquals(MeasureFill.UNWRITTEN, fillOf(TimeSignature(4, 4)))
    }

    @Test
    fun `a Measure shorter than its time signature is incomplete`() {
        assertEquals(
            MeasureFill.INCOMPLETE,
            fillOf(TimeSignature(4, 4), note(Duration.QUARTER), note(Duration.QUARTER), Rest(Duration.QUARTER)),
        )
    }

    @Test
    fun `a dotted half and a quarter fill 4-4`() {
        assertEquals(
            MeasureFill.FULL,
            fillOf(TimeSignature(4, 4), note(Duration(NoteValue.HALF, isDotted = true)), Rest(Duration.QUARTER)),
        )
    }

    @Test
    fun `three triplet eighths take the time of one quarter`() {
        val tripletEighth = Duration(NoteValue.EIGHTH, isTriplet = true)
        assertEquals(
            MeasureFill.FULL,
            fillOf(TimeSignature(2, 4), note(tripletEighth), note(tripletEighth), note(tripletEighth), note(Duration.QUARTER)),
        )
    }

    @Test
    fun `two dotted quarters fill 6-8`() {
        val dottedQuarter = Duration(NoteValue.QUARTER, isDotted = true)
        assertEquals(MeasureFill.FULL, fillOf(TimeSignature(6, 8), note(dottedQuarter), note(dottedQuarter)))
    }

    @Test
    fun `thirty-second Notes, dotted and in triplets, add up exactly`() {
        val elements = List(3) { note(Duration(NoteValue.THIRTY_SECOND, isTriplet = true)) } +
            List(2) { note(Duration(NoteValue.THIRTY_SECOND, isDotted = true)) } +
            note(Duration(NoteValue.SIXTEENTH))
        // 1/16 + 3/32 + 1/16 = 7/32, short of a quarter by 1/32.
        assertEquals(MeasureFill.INCOMPLETE, fillOf(TimeSignature(1, 4), *elements.toTypedArray()))
        assertEquals(
            MeasureFill.FULL,
            fillOf(TimeSignature(1, 4), *(elements + note(Duration(NoteValue.THIRTY_SECOND))).toTypedArray()),
        )
    }

    @Test
    fun `a Measure longer than its time signature is overfull`() {
        assertEquals(MeasureFill.OVERFULL, fillOf(TimeSignature(3, 4), note(Duration.WHOLE)))
    }
}
