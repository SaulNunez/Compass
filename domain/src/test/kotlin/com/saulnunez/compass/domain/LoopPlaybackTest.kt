package com.saulnunez.compass.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class LoopPlaybackTest {

    private val guitar = TuningPreset.GUITAR_STANDARD.instrument()
    private fun fourFour(bpm: Int) = Measure(TimeSignature(4, 4), KeySignature.C_MAJOR, Tempo(bpm))
    private fun threeFour(bpm: Int) = Measure(TimeSignature(3, 4), KeySignature.C_MAJOR, Tempo(bpm))

    /** Measure n (0-based) holds a single quarter Note on fret n. */
    private fun song(measures: List<Measure>, sections: List<Section> = emptyList()) = Song(
        id = SongId("loops"),
        title = "Loops",
        artist = "",
        measures = measures,
        tracks = listOf(Track("Guitar", guitar, Voice(measures.indices.map { listOf(Note(1, it, Duration.QUARTER)) }))),
        sections = sections,
    )

    @Test
    fun `a Measure-range Loop repeats its Measures with no gap between repeats`() {
        val song = song(listOf(fourFour(120), fourFour(120), threeFour(60), fourFour(120)))
        val settings = PracticeSettings(loop = Loop.OfMeasures(first = 1, last = 2))

        val timeline = song.playbackTimeline(settings, passes = 3)

        assertEquals(listOf(1, 2, 1, 2, 1, 2), timeline.measures.map { it.measure })
        // 4/4 at 120 BPM lasts 2 s, 3/4 at 60 BPM 3 s: each repeat starts the moment the last one ends.
        assertEquals(listOf(0.0, 2000.0, 5000.0, 7000.0, 10000.0, 12000.0), timeline.measures.map { it.startMillis })
        assertEquals(listOf(1, 2, 1, 2, 1, 2), timeline.notes.map { it.note.fret })
        assertEquals(listOf(0.0, 2000.0, 5000.0, 7000.0, 10000.0, 12000.0), timeline.notes.map { it.startMillis })
    }

    @Test
    fun `the count-in plays only before the first time through a Loop`() {
        val song = song(List(4) { fourFour(120) })
        val settings = PracticeSettings(countIn = true, loop = Loop.OfMeasures(first = 2, last = 3))

        val timeline = song.playbackTimeline(settings, passes = 3)

        assertEquals(listOf(0.0, 500.0, 1000.0, 1500.0), timeline.clicks.filter { it.isCountIn }.map { it.startMillis })
        assertEquals(listOf(2000.0, 4000.0, 6000.0, 8000.0, 10000.0, 12000.0), timeline.measures.map { it.startMillis })
    }

    @Test
    fun `the click keeps going through every repeat`() {
        val song = song(List(2) { fourFour(120) })
        val settings = PracticeSettings(click = true, loop = Loop.OfMeasures(first = 1, last = 1))

        val timeline = song.playbackTimeline(settings, passes = 2)

        assertEquals(List(8) { it * 500.0 }, timeline.clicks.map { it.startMillis })
    }

    @Test
    fun `a Section Loop plays its Section's Measures`() {
        val song = song(List(5) { fourFour(120) }, sections = listOf(Section("Intro", 0, 0), Section("Verse", 2, 3)))
        val settings = PracticeSettings(loop = Loop.OfSection(section = 1))

        val timeline = song.playbackTimeline(settings, passes = 2)

        assertEquals(listOf(2, 3, 2, 3), timeline.measures.map { it.measure })
    }

    @Test
    fun `starting inside a Loop plays to its end, then repeats it from its first Measure`() {
        val song = song(List(6) { fourFour(120) })
        val settings = PracticeSettings(loop = Loop.OfMeasures(first = 1, last = 4))

        val timeline = song.playbackTimeline(settings, fromMeasure = 3, passes = 2)

        assertEquals(listOf(3, 4, 1, 2, 3, 4), timeline.measures.map { it.measure })
    }

    @Test
    fun `starting outside a Loop starts at its first Measure`() {
        val song = song(List(6) { fourFour(120) })
        val settings = PracticeSettings(loop = Loop.OfMeasures(first = 1, last = 2))

        val timeline = song.playbackTimeline(settings, fromMeasure = 5, passes = 1)

        assertEquals(listOf(1, 2), timeline.measures.map { it.measure })
    }
}
