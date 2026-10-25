package com.saulnunez.compass.notation

import alphaTab.Settings
import alphaTab.model.Score
import com.saulnunez.compass.domain.EditSession
import com.saulnunez.compass.domain.KeySignature
import com.saulnunez.compass.domain.Measure
import com.saulnunez.compass.domain.PracticeSettings
import com.saulnunez.compass.domain.Sound
import com.saulnunez.compass.domain.Tempo
import com.saulnunez.compass.domain.TimeSignature
import com.saulnunez.compass.domain.TuningPreset
import com.saulnunez.compass.domain.SongId
import com.saulnunez.compass.domain.newSong
import com.saulnunez.compass.domain.AddTrack
import org.junit.Assert.assertEquals
import org.junit.Test

/** Which of alphaTab's MIDI channels are muted so its Playback leaves out the Tracks the Practice settings silence. */
class ChannelMutesTest {

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

    private fun Score.channelsOf(track: Int): Set<Int> =
        tracks[track].playbackInfo.let { setOf(it.primaryChannel.toInt(), it.secondaryChannel.toInt()) }

    private fun Map<Int, Boolean>.muted() = filterValues { it }.keys

    @Test
    fun `a muted Track's channels are muted and no other`() {
        val score = song.toAlphaTabScore(Settings())

        val mutes = score.channelMutes(PracticeSettings(mutedTracks = setOf(1))::isHeard)

        assertEquals(score.channelsOf(1), mutes.muted())
    }

    @Test
    fun `with a solo, every channel of a Track not soloed is muted`() {
        val score = song.toAlphaTabScore(Settings())

        val mutes = score.channelMutes(PracticeSettings(soloedTracks = setOf(2))::isHeard)

        assertEquals(score.channelsOf(0) + score.channelsOf(1), mutes.muted())
    }

    @Test
    fun `after a Track is removed, mute and solo reach the channels each remaining Track now plays on`() {
        // Muting the bass and soloing the lead and the bass, then removing the rhythm guitar.
        val settings = PracticeSettings(mutedTracks = setOf(2), soloedTracks = setOf(1, 2))
        val session = EditSession.start(song).removeTrack()
        val score = session.song.toAlphaTabScore(Settings())

        val mutes = score.channelMutes(session.reconcile(settings)::isHeard)

        // The bass, now Track 1, is muted; the lead, now Track 0, plays.
        assertEquals(score.channelsOf(1), mutes.muted())
        // The channels the removed Track's neighbours used to play on are unmuted, so no old state lingers.
        val before = song.toAlphaTabScore(Settings())
        val previouslyBass = before.channelsOf(2)
        assertEquals(false, previouslyBass.any { mutes[it] == true })
        assertEquals(true, previouslyBass.all { it in mutes })
    }
}
