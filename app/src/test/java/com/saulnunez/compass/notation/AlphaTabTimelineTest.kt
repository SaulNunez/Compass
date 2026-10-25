package com.saulnunez.compass.notation

import com.saulnunez.compass.domain.Duration
import com.saulnunez.compass.domain.KeySignature
import com.saulnunez.compass.domain.Loop
import com.saulnunez.compass.domain.Measure
import com.saulnunez.compass.domain.Note
import com.saulnunez.compass.domain.PlaybackTimeline
import com.saulnunez.compass.domain.PracticeSettings
import com.saulnunez.compass.domain.Rest
import com.saulnunez.compass.domain.Section
import com.saulnunez.compass.domain.Song
import com.saulnunez.compass.domain.SongId
import com.saulnunez.compass.domain.Tempo
import com.saulnunez.compass.domain.TempoPercentage
import com.saulnunez.compass.domain.TimeSignature
import com.saulnunez.compass.domain.Track
import com.saulnunez.compass.domain.TuningPreset
import com.saulnunez.compass.domain.Voice
import com.saulnunez.compass.domain.playbackTimeline
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * alphaTab's player, set up by Compass for a Song and its Practice settings, plays what the
 * playback timeline says: the Loop, mute and solo, and the count-in.
 */
class AlphaTabTimelineTest {

    private val guitar = Track(
        name = "Guitar",
        instrument = TuningPreset.GUITAR_STANDARD.instrument(),
        voice = Voice(
            listOf(
                listOf(Note(1, 0, Duration.QUARTER), Rest(Duration.QUARTER), Note(2, 1, Duration.HALF)),
                listOf(Note(3, 2, Duration.EIGHTH), Note(3, 4, Duration.EIGHTH), Note(1, 5, Duration.HALF)),
                listOf(Note(1, 3, Duration.HALF), Note(1, 3, Duration.QUARTER, isTied = true)),
            ),
        ),
    )
    private val bass = Track(
        name = "Bass",
        instrument = TuningPreset.BASS_STANDARD.instrument(),
        voice = Voice(
            listOf(
                listOf(Note(4, 0, Duration.WHOLE)),
                listOf(Rest(Duration.QUARTER), Note(4, 3, Duration.HALF)),
                listOf(Note(3, 2, Duration.QUARTER), Note(3, 4, Duration.HALF)),
            ),
        ),
    )
    private val song = Song(
        id = SongId("timeline"),
        title = "Timeline",
        artist = "",
        measures = listOf(
            Measure(TimeSignature(4, 4), KeySignature.C_MAJOR, Tempo(120)),
            Measure(TimeSignature(3, 4), KeySignature.C_MAJOR, Tempo(60)),
            Measure(TimeSignature(3, 4), KeySignature.C_MAJOR, Tempo(60)),
        ),
        tracks = listOf(guitar, bass),
        sections = listOf(Section("Chorus", first = 1, last = 2)),
    )

    /** alphaTab and the timeline start the same Notes at the same moments, within an audio buffer. */
    private fun assertPlaysAsTimeline(timeline: PlaybackTimeline, player: HeadlessPlayer, untilMillis: Double) {
        val expected = timeline.notes.map { it.startMillis }.filter { it < untilMillis }.sorted()
        val heard = player.notes.filterNot(player::isMuted).map { it.atMillis }.sorted()
        assertEquals("Notes started: expected $expected, heard $heard", expected.size, heard.size)
        expected.zip(heard).forEach { (want, got) -> assertEquals(want, got, TOLERANCE_MILLIS) }
    }

    /** When the timeline's [passes]th time through its Measures ends, minus a little so no further Note sneaks in. */
    private fun PlaybackTimeline.end() = measures.last().let { it.startMillis + it.lengthMillis } - TOLERANCE_MILLIS

    @Test
    fun `a Loop of Measures, started before it, plays its Measures over and over with no gap`() {
        val settings = PracticeSettings(loop = Loop.OfMeasures(first = 1, last = 2))
        val timeline = song.playbackTimeline(settings, fromMeasure = 0, passes = 3)

        val player = HeadlessPlayer(song, settings).playFrom(measure = 0, millis = timeline.end())

        assertEquals(1, timeline.measures.first().measure)
        assertPlaysAsTimeline(timeline, player, timeline.end())
    }

    @Test
    fun `a Section Loop, started inside it, plays to its end and goes back to its first Measure`() {
        val settings = PracticeSettings(loop = Loop.OfSection(section = 0))
        val timeline = song.playbackTimeline(settings, fromMeasure = 2, passes = 2)

        val player = HeadlessPlayer(song, settings).playFrom(measure = 2, millis = timeline.end())

        assertPlaysAsTimeline(timeline, player, timeline.end())
    }

    @Test
    fun `a muted Track is left out, as the timeline says`() {
        val settings = PracticeSettings(mutedTracks = setOf(0))
        val timeline = song.playbackTimeline(settings)

        val player = HeadlessPlayer(song, settings).playFrom(measure = 0, millis = timeline.end())

        assertPlaysAsTimeline(timeline, player, timeline.end())
    }

    @Test
    fun `a solo leaves only the soloed Track, as the timeline says`() {
        val settings = PracticeSettings(soloedTracks = setOf(0), loop = Loop.OfMeasures(0, 1))
        val timeline = song.playbackTimeline(settings, passes = 2)

        val player = HeadlessPlayer(song, settings).playFrom(measure = 0, millis = timeline.end())

        assertPlaysAsTimeline(timeline, player, timeline.end())
    }

    @Test
    fun `the count-in plays once, one Measure at the tempo percentage, even with a Loop`() {
        val settings = PracticeSettings(TempoPercentage(50), countIn = true, loop = Loop.OfMeasures(0, 1))
        val timeline = song.playbackTimeline(settings, passes = 2)

        val player = HeadlessPlayer(song, settings).playFrom(measure = 0, millis = timeline.end())

        // One 4/4 Measure at 60 BPM (120 at 50%).
        assertEquals(timeline.measures.first().startMillis, player.countInMillis, TOLERANCE_MILLIS)
        assertPlaysAsTimeline(timeline, player, timeline.end())
    }

    @Test
    fun `Playback started at a later Measure counts in at its Tempo and time signature when the Measure before has the same`() {
        val settings = PracticeSettings(countIn = true)
        val timeline = song.playbackTimeline(settings, fromMeasure = 2)

        val player = HeadlessPlayer(song, settings).playFrom(measure = 2, millis = timeline.end())

        // One 3/4 Measure at 60 BPM.
        assertEquals(3000.0, timeline.measures.first().startMillis, 0.0)
        assertEquals(timeline.measures.first().startMillis, player.countInMillis, TOLERANCE_MILLIS)
        assertPlaysAsTimeline(timeline, player, timeline.end())
    }

    /**
     * A known difference, kept here so an alphaTab upgrade that fixes it is noticed: alphaTab's
     * count-in takes the Tempo and time signature its synthesizer last saw, and seeking to a Measure
     * stops just short of that Measure's own changes. So started at a Measure whose Tempo or time
     * signature differ from the Measure before, alphaTab counts in with the Measure before's
     * (here 4/4 at 120 BPM, 2 s) where the timeline counts in with the Measure's own (3/4 at 60, 3 s).
     * Compass can't reach the synthesizer to correct it; the Song itself still plays on time.
     */
    @Test
    fun `alphaTab counts in with the Measure before when Playback starts where the Tempo or time signature changes`() {
        val settings = PracticeSettings(countIn = true)
        val timeline = song.playbackTimeline(settings, fromMeasure = 1)

        val player = HeadlessPlayer(song, settings).playFrom(measure = 1, millis = 4000.0)

        assertEquals(3000.0, timeline.measures.first().startMillis, 0.0)
        assertEquals(2000.0, player.countInMillis, TOLERANCE_MILLIS)
    }

    private companion object {
        /** alphaTab hands audio over in buffers of 2048 frames, about 46 ms at 44.1 kHz. */
        const val TOLERANCE_MILLIS = 50.0
    }
}
