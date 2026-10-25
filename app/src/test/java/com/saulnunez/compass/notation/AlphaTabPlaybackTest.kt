package com.saulnunez.compass.notation

import alphaTab.Settings
import alphaTab.midi.AlphaSynthMidiFileHandler
import alphaTab.midi.MidiFile
import alphaTab.midi.MidiFileGenerator
import alphaTab.midi.NoteOnEvent
import alphaTab.midi.ProgramChangeEvent
import alphaTab.midi.TempoChangeEvent
import com.saulnunez.compass.domain.Duration
import com.saulnunez.compass.domain.InstrumentKind
import com.saulnunez.compass.domain.Sound
import com.saulnunez.compass.domain.KeySignature
import com.saulnunez.compass.domain.Measure
import com.saulnunez.compass.domain.Note
import com.saulnunez.compass.domain.PracticeSettings
import com.saulnunez.compass.domain.Rest
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

/** What alphaTab's player is given to play for a Compass Song, checked against the playback timeline. */
class AlphaTabPlaybackTest {

    private val guitar = Track(
        name = "Guitar",
        instrument = TuningPreset.GUITAR_STANDARD.instrument(),
        voice = Voice(
            listOf(
                listOf(Note(1, 0, Duration.QUARTER), Rest(Duration.QUARTER), Note(2, 1, Duration.HALF)),
                listOf(Note(3, 2, Duration.EIGHTH), Note(3, 4, Duration.EIGHTH)),
            ),
        ),
    )
    private val bass = Track(
        name = "Bass",
        instrument = TuningPreset.BASS_STANDARD.instrument(),
        voice = Voice(listOf(listOf(Note(4, 0, Duration.WHOLE)), listOf(Note(4, 3, Duration.HALF)))),
    )
    private val song = Song(
        id = SongId("playback"),
        title = "Playback",
        artist = "",
        measures = listOf(
            Measure(TimeSignature(4, 4), KeySignature.C_MAJOR, Tempo(120)),
            Measure(TimeSignature(3, 4), KeySignature.C_MAJOR, Tempo(90)),
        ),
        tracks = listOf(guitar, bass),
    )

    private fun midiOf(song: Song): MidiFile {
        val settings = Settings()
        val midiFile = MidiFile()
        MidiFileGenerator(song.toAlphaTabScore(settings), settings, AlphaSynthMidiFileHandler(midiFile)).generate()
        return midiFile
    }

    private val midi: MidiFile = midiOf(song)

    private val noteOns get() = midi.events.toList().filterIsInstance<NoteOnEvent>()

    /** The General MIDI program each Track of [song] plays with, by Track index. */
    private fun programsOf(song: Song): List<Int> {
        val score = song.toAlphaTabScore(Settings())
        val events = midiOf(song).events.toList().filterIsInstance<ProgramChangeEvent>()
        return (0 until score.tracks.length.toInt()).map { index ->
            val channel = score.tracks[index].playbackInfo.primaryChannel.toInt()
            events.last { it.channel.toInt() == channel }.program.toInt()
        }
    }

    @Test
    fun `alphaTab plays every Note when the playback timeline says, Tempo changes included`() {
        val timeline = song.playbackTimeline(PracticeSettings(TempoPercentage.FULL))

        val played = noteOns.map { millisAt(it.tick) }.sorted()
        val expected = timeline.notes.map { it.startMillis }.sorted()
        assertEquals(expected.size, played.size)
        expected.zip(played).forEach { (want, got) -> assertEquals(want, got, 0.5) }
    }

    @Test
    fun `each Track plays on channels of its own, skipping the percussion channel`() {
        val score = song.copy(tracks = List(5) { guitar }).toAlphaTabScore(Settings())
        val channels = (0 until score.tracks.length.toInt()).flatMap { index ->
            val info = score.tracks[index].playbackInfo
            listOf(info.primaryChannel.toInt(), info.secondaryChannel.toInt())
        }

        assertEquals(channels.distinct(), channels)
        assertEquals(false, PERCUSSION_CHANNEL in channels)
    }

    @Test
    fun `a guitar Track sounds as a clean electric guitar and a bass Track as a fingered bass`() {
        val guitarChannel = noteOns.first { it.noteKey.toInt() == 64 }.channel
        val bassChannel = noteOns.first { it.noteKey.toInt() == 28 }.channel
        val programs = midi.events.toList().filterIsInstance<ProgramChangeEvent>().associate { it.channel to it.program.toInt() }

        assertEquals(CLEAN_ELECTRIC_GUITAR, programs[guitarChannel])
        assertEquals(FINGERED_BASS, programs[bassChannel])
    }

    @Test
    fun `each Sound plays with its General MIDI program`() {
        val expected = mapOf(
            Sound.CLEAN_ELECTRIC_GUITAR to 27,
            Sound.OVERDRIVEN_GUITAR to 29,
            Sound.DISTORTED_ELECTRIC_GUITAR to 30,
            Sound.STEEL_ACOUSTIC_GUITAR to 25,
            Sound.NYLON_ACOUSTIC_GUITAR to 24,
            Sound.FINGERED_BASS to 33,
            Sound.PICKED_BASS to 34,
            Sound.SLAP_BASS to 36,
            Sound.FRETLESS_BASS to 35,
        )
        // A Song per Instrument, since 9 Tracks would be more than the channels go round.
        for (kind in InstrumentKind.entries) {
            val sounds = Sound.choicesFor(kind)
            val tracks = sounds.map { (if (kind == InstrumentKind.GUITAR) guitar else bass).copy(sound = it) }

            assertEquals(sounds.map { expected.getValue(it) }, programsOf(song.copy(tracks = tracks)))
        }
    }

    @Test
    fun `every Track plays together, each at the pitches of its own Tuning`() {
        val retuned = song.copy(tracks = listOf(guitar.copy(instrument = TuningPreset.GUITAR_D_STANDARD.instrument()), bass))
        val starts = midiOf(retuned).events.toList().filterIsInstance<NoteOnEvent>()
            .filter { it.tick == 0.0 }.map { it.noteKey.toInt() }.sorted()

        // String 1 open in D standard is D4 (62), against E4 (64) in standard; the bass's open E1 (28) is unchanged.
        assertEquals(listOf(28, 62), starts)
    }

    /** Milliseconds from the start of the MIDI to [tick], following its tempo changes. */
    private fun millisAt(tick: Double): Double {
        val tempos = midi.events.toList().filterIsInstance<TempoChangeEvent>().sortedBy { it.tick }
        var millis = 0.0
        var lastTick = 0.0
        var microsPerQuarter = 500_000.0
        for (tempo in tempos) {
            if (tempo.tick >= tick) break
            millis += (tempo.tick - lastTick) / midi.division * microsPerQuarter / 1000
            lastTick = tempo.tick
            microsPerQuarter = tempo.microSecondsPerQuarterNote
        }
        return millis + (tick - lastTick) / midi.division * microsPerQuarter / 1000
    }

    private fun <T> alphaTab.collections.List<T>.toList(): List<T> = (0 until length.toInt()).map { this[it] }

    private companion object {
        const val PERCUSSION_CHANNEL = 9
        const val CLEAN_ELECTRIC_GUITAR = 27
        const val FINGERED_BASS = 33
    }
}
