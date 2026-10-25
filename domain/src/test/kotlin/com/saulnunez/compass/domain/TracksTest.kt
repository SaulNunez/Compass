package com.saulnunez.compass.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class TracksTest {

    private val song = newSong(
        id = SongId("tracks"),
        title = "Tracks",
        artist = "",
        startingMeasure = Measure(TimeSignature(4, 4), KeySignature.C_MAJOR, Tempo(120)),
        trackName = "Guitar",
        instrument = TuningPreset.GUITAR_STANDARD.instrument(),
    ).let { WriteElement(0, 0, 0, Note(1, 5, Duration.QUARTER)).applyTo(it) }

    @Test
    fun `an added Track gets every Measure of the Song, empty, and the chosen Sound`() {
        val edited = EditSession.start(song)
            .addTrack("Bass", TuningPreset.BASS_FIVE_STRING_STANDARD.instrument(), Sound.SLAP_BASS)
            .song

        val bass = edited.tracks[1]
        assertEquals("Bass", bass.name)
        assertEquals(5, bass.instrument.stringCount)
        assertEquals(Sound.SLAP_BASS, bass.sound)
        assertEquals(List(NEW_SONG_MEASURE_COUNT) { emptyList<VoiceElement>() }, bass.voice.measures)
        assertEquals(song.tracks[0], edited.tracks[0])
    }

    @Test
    fun `the added Track is the one edited next, and another Track can be chosen to edit`() {
        val withBass = EditSession.start(song)
            .addTrack("Bass", TuningPreset.BASS_STANDARD.instrument(), Sound.FINGERED_BASS)
            .typeDigit(3)

        assertEquals(1, withBass.track)
        assertEquals(listOf(Note(1, 3, Duration.QUARTER)), withBass.song.tracks[1].voice.measures[0])

        val backToGuitar = withBass.editTrack(0).moveToNextBeat().typeDigit(7)
        assertEquals(listOf(Note(1, 5, Duration.QUARTER), Note(1, 7, Duration.QUARTER)), backToGuitar.song.tracks[0].voice.measures[0])
    }

    @Test
    fun `choosing a Track with fewer strings keeps the cursor on one of its strings`() {
        val session = EditSession.start(song)
            .addTrack("Bass", TuningPreset.BASS_STANDARD.instrument(), Sound.FINGERED_BASS)
            .editTrack(0)
            .moveToStringBelow().moveToStringBelow().moveToStringBelow().moveToStringBelow().moveToStringBelow()

        assertEquals(4, session.editTrack(1).cursor.string)
    }

    private val withBass = EditSession.start(song)
        .addTrack("Bass", TuningPreset.BASS_STANDARD.instrument(), Sound.FINGERED_BASS)

    @Test
    fun `removing a Track leaves the others and edits a remaining one, and undo brings it back`() {
        val removed = withBass.editTrack(0).removeTrack()

        assertEquals(listOf("Bass"), removed.song.tracks.map { it.name })
        assertEquals(0, removed.track)

        val undone = removed.undo()
        assertEquals(withBass.song, undone.song)
        assertEquals(0, undone.track)
    }

    @Test
    fun `a Song keeps at least one Track`() {
        val session = EditSession.start(song)

        assertEquals(false, session.canRemoveTrack)
        assertEquals(song, session.removeTrack().song)
        assertEquals(true, withBass.canRemoveTrack)
    }

    @Test
    fun `a Track can be renamed`() {
        assertEquals(listOf("Rhythm", "Bass"), withBass.editTrack(0).renameTrack("Rhythm").song.tracks.map { it.name })
    }

    @Test
    fun `changing a Track's Tuning keeps every fret, so its Notes sound at new pitches`() {
        val retuned = EditSession.start(song).changeTuning(TuningPreset.GUITAR_D_STANDARD.tuning).song

        val track = retuned.tracks[0]
        assertEquals(song.tracks[0].voice, track.voice)
        val note = track.voice.measures[0][0] as Note
        assertEquals(Pitch(64 + 5), song.tracks[0].instrument.pitchOf(note))
        assertEquals(Pitch(62 + 5), track.instrument.pitchOf(note))
    }

    @Test
    fun `a custom Tuning can be set string by string`() {
        val custom = TuningPreset.GUITAR_STANDARD.tuning.withString(6, Pitch(35)).withString(1, Pitch(65))

        val retuned = EditSession.start(song).changeTuning(custom).song

        assertEquals(listOf(65, 59, 55, 50, 45, 35), retuned.tracks[0].instrument.tuning.openStrings.map { it.midi })
    }

    @Test
    fun `a Tuning with a different number of strings is refused`() {
        assertThrows(IllegalArgumentException::class.java) {
            EditSession.start(song).changeTuning(TuningPreset.BASS_STANDARD.tuning)
        }
    }

    @Test
    fun `a Track's Sound can be changed among those of its Instrument`() {
        val distorted = EditSession.start(song).changeSound(Sound.DISTORTED_ELECTRIC_GUITAR).song

        assertEquals(Sound.DISTORTED_ELECTRIC_GUITAR, distorted.tracks[0].sound)
        assertThrows(IllegalArgumentException::class.java) { EditSession.start(song).changeSound(Sound.SLAP_BASS) }
    }

    @Test
    fun `guitars and basses each offer their own Sounds, a clean guitar and a fingered bass by default`() {
        assertEquals(
            listOf(
                Sound.CLEAN_ELECTRIC_GUITAR,
                Sound.OVERDRIVEN_GUITAR,
                Sound.DISTORTED_ELECTRIC_GUITAR,
                Sound.STEEL_ACOUSTIC_GUITAR,
                Sound.NYLON_ACOUSTIC_GUITAR,
            ),
            Sound.choicesFor(InstrumentKind.GUITAR),
        )
        assertEquals(
            listOf(Sound.FINGERED_BASS, Sound.PICKED_BASS, Sound.SLAP_BASS, Sound.FRETLESS_BASS),
            Sound.choicesFor(InstrumentKind.BASS),
        )
        assertEquals(Sound.CLEAN_ELECTRIC_GUITAR, song.tracks[0].sound)
    }
}
