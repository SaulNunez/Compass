package com.saulnunez.compass.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PlaybackTimelineTest {

    private val guitar = TuningPreset.GUITAR_STANDARD.instrument()

    private fun song(vararg measures: Pair<Measure, List<VoiceElement>>) = Song(
        id = SongId("timeline"),
        title = "Timeline",
        artist = "",
        measures = measures.map { it.first },
        tracks = listOf(Track("Guitar", guitar, Voice(measures.map { it.second }))),
    )

    private fun fourFour(bpm: Int) = Measure(TimeSignature(4, 4), KeySignature.C_MAJOR, Tempo(bpm))

    private fun quarter(fret: Int) = Note(string = 1, fret = fret, duration = Duration.QUARTER)

    private fun settings(percent: Int = 100, click: Boolean = false, countIn: Boolean = false) =
        PracticeSettings(tempoPercentage = TempoPercentage(percent), click = click, countIn = countIn)

    @Test
    fun `the tempo percentage scales every written Tempo`() {
        val song = song(fourFour(120) to listOf(quarter(0)), fourFour(140) to listOf(quarter(2)))

        val timeline = song.playbackTimeline(settings(percent = 50))

        assertEquals(listOf(60.0, 70.0), timeline.measures.map { it.beatsPerMinute })
        // A quarter note lasts one beat: 1 s at 60 BPM, 6/7 s at 70 BPM.
        assertEquals(1000.0, timeline.notes[0].lengthMillis, DELTA)
        assertEquals(857.142, timeline.notes[1].lengthMillis, DELTA)
        // The second Measure starts after four beats at 60 BPM.
        assertEquals(4000.0, timeline.notes[1].startMillis, DELTA)
    }

    @Test
    fun `a count-in plays one Measure of clicks once, before the first Measure`() {
        val song = song(fourFour(120) to listOf(quarter(0)), fourFour(120) to listOf(quarter(2)))

        val timeline = song.playbackTimeline(settings(countIn = true))

        // Four beats at 120 BPM, half a second apart, then the Song.
        assertEquals(listOf(0.0, 500.0, 1000.0, 1500.0), timeline.clicks.map { it.startMillis })
        assertEquals(listOf(true, true, true, true), timeline.clicks.map { it.isCountIn })
        assertEquals(listOf(2000.0, 4000.0), timeline.measures.map { it.startMillis })
        assertEquals(2000.0, timeline.notes[0].startMillis, DELTA)
    }

    @Test
    fun `without the click or a count-in nothing but the Notes sounds`() {
        val song = song(fourFour(120) to listOf(quarter(0)))

        val timeline = song.playbackTimeline(settings())

        assertEquals(emptyList<Click>(), timeline.clicks)
        assertEquals(0.0, timeline.notes[0].startMillis, DELTA)
    }

    @Test
    fun `the click sounds on every beat of every Measure, counting the time signature's beat unit`() {
        val sixEight = Measure(TimeSignature(6, 8), KeySignature.C_MAJOR, Tempo(120))
        val song = song(fourFour(120) to emptyList(), sixEight to emptyList())

        val timeline = song.playbackTimeline(settings(click = true))

        // 4/4: quarter-note clicks every 500 ms; 6/8: eighth-note clicks every 250 ms.
        assertEquals(
            listOf(0.0, 500.0, 1000.0, 1500.0, 2000.0, 2250.0, 2500.0, 2750.0, 3000.0, 3250.0),
            timeline.clicks.map { it.startMillis },
        )
        assertEquals(false, timeline.clicks.any { it.isCountIn })
    }

    @Test
    fun `Playback started at a Measure skips the Measures before it and counts in at that Measure's Tempo`() {
        val song = song(fourFour(60) to listOf(quarter(0)), fourFour(120) to listOf(quarter(2)))

        val timeline = song.playbackTimeline(settings(countIn = true), fromMeasure = 1)

        assertEquals(listOf(1), timeline.measures.map { it.measure })
        assertEquals(listOf(2), timeline.notes.map { it.note.fret })
        assertEquals(listOf(0.0, 500.0, 1000.0, 1500.0), timeline.clicks.map { it.startMillis })
        assertEquals(2000.0, timeline.notes[0].startMillis, DELTA)
    }

    @Test
    fun `rests and unwritten time keep their place, so the next Measure starts on time`() {
        val song = song(
            fourFour(120) to listOf(Rest(Duration.QUARTER), quarter(3)),
            fourFour(120) to listOf(quarter(5)),
        )

        val timeline = song.playbackTimeline(settings())

        assertEquals(listOf(500.0, 2000.0), timeline.notes.map { it.startMillis })
    }

    @Test
    fun `every Track plays together`() {
        val bass = Track(
            "Bass",
            TuningPreset.BASS_STANDARD.instrument(),
            Voice(listOf(listOf(Note(string = 4, fret = 0, duration = Duration.HALF)))),
        )
        val song = song(fourFour(120) to listOf(quarter(0))).let { it.copy(tracks = it.tracks + bass) }

        val timeline = song.playbackTimeline(settings())

        assertEquals(listOf(0 to 0.0, 1 to 0.0), timeline.notes.map { it.track to it.startMillis })
        assertEquals(1000.0, timeline.notes[1].lengthMillis, DELTA)
    }

    @Test
    fun `the tempo percentage goes from 25 to 150`() {
        assertEquals(25, TempoPercentage(25).percent)
        assertEquals(150, TempoPercentage(150).percent)
        assertThrows(IllegalArgumentException::class.java) { TempoPercentage(24) }
        assertThrows(IllegalArgumentException::class.java) { TempoPercentage(151) }
    }

    private companion object {
        const val DELTA = 0.001
    }
}
