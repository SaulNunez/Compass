package com.saulnunez.compass.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PracticeSettingsReconciliationTest {

    private val song = newSong(
        id = SongId("band"),
        title = "Band",
        artist = "",
        startingMeasure = Measure(TimeSignature(4, 4), KeySignature.C_MAJOR, Tempo(120)),
        trackName = "Rhythm",
        instrument = TuningPreset.GUITAR_STANDARD.instrument(),
    ).let { song ->
        listOf(
            AddTrack("Lead", TuningPreset.GUITAR_STANDARD.instrument(), Sound.OVERDRIVEN_GUITAR),
            AddTrack("Bass", TuningPreset.BASS_STANDARD.instrument(), Sound.FINGERED_BASS),
        ).fold(song) { s, edit -> edit.applyTo(s) }
    }

    /** Reading the bass. */
    private val settings = PracticeSettings(tempoPercentage = TempoPercentage(80), readTrack = 2)

    @Test
    fun `the Track being read is followed when a Track before it is removed`() {
        val session = EditSession.start(song).removeTrack()

        assertEquals(settings.copy(readTrack = 1), session.reconcile(settings))
    }

    @Test
    fun `reading a removed Track is dropped, back to the first Track, and the other settings are kept`() {
        val session = EditSession.start(song, track = 2).removeTrack()

        assertEquals(settings.copy(readTrack = 0), session.reconcile(settings))
    }

    @Test
    fun `adding Tracks keeps the Track being read`() {
        val session = EditSession.start(song).addTrack("Acoustic", TuningPreset.GUITAR_STANDARD.instrument(), Sound.NYLON_ACOUSTIC_GUITAR)

        assertEquals(settings, session.reconcile(settings))
    }

    @Test
    fun `undoing a removal brings back the Track being read`() {
        val session = EditSession.start(song, track = 2).removeTrack().undo()

        assertEquals(settings, session.reconcile(settings))
    }

    @Test
    fun `a Track added in place of a removed one is not read in its place`() {
        val session = EditSession.start(song, track = 2).removeTrack()
            .addTrack("New bass", TuningPreset.BASS_STANDARD.instrument(), Sound.PICKED_BASS)

        assertEquals(settings.copy(readTrack = 0), session.reconcile(settings))
    }

    /** Muting the rhythm guitar and the bass, soloing the lead and the bass. */
    private val mix = PracticeSettings(mutedTracks = setOf(0, 2), soloedTracks = setOf(1, 2))

    @Test
    fun `muted and soloed Tracks are followed when a Track before them is removed`() {
        val session = EditSession.start(song).removeTrack()

        assertEquals(PracticeSettings(mutedTracks = setOf(1), soloedTracks = setOf(0, 1)), session.reconcile(mix))
    }

    @Test
    fun `a removed Track's mute and solo are dropped, not passed to the Track that moves up`() {
        val session = EditSession.start(song, track = 1).removeTrack()

        assertEquals(PracticeSettings(mutedTracks = setOf(0, 1), soloedTracks = setOf(1)), session.reconcile(mix))
    }

    @Test
    fun `a Track added in place of a removed one is neither muted nor soloed`() {
        val session = EditSession.start(song, track = 2).removeTrack()
            .addTrack("New bass", TuningPreset.BASS_STANDARD.instrument(), Sound.PICKED_BASS)

        assertEquals(PracticeSettings(mutedTracks = setOf(0), soloedTracks = setOf(1)), session.reconcile(mix))
    }

    @Test
    fun `undoing a removal brings back its mute and solo`() {
        val session = EditSession.start(song, track = 2).removeTrack().undo()

        assertEquals(mix, session.reconcile(mix))
    }
}
