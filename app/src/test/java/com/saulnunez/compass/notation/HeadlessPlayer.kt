package com.saulnunez.compass.notation

import alphaTab.IEventEmitter
import alphaTab.IEventEmitterOfT
import alphaTab.Settings
import alphaTab.core.ecmaScript.Float32Array
import alphaTab.midi.AlphaSynthMidiFileHandler
import alphaTab.midi.MidiEventType
import alphaTab.midi.MidiFile
import alphaTab.midi.MidiFileGenerator
import alphaTab.midi.NoteOnEvent
import alphaTab.model.Score
import alphaTab.synth.AlphaSynth
import alphaTab.synth.ISynthOutput
import alphaTab.synth.ISynthOutputDevice
import com.saulnunez.compass.domain.PracticeSettings
import com.saulnunez.compass.domain.Song
import com.saulnunez.compass.domain.measuresIn

/**
 * alphaTab's own player (the AlphaSynth the view plays with) run on the JVM without audio, set up
 * the way [NotationPlayer] and [NotationView] set it up for a Song and its Practice settings, so a
 * test can hear what it plays and when.
 */
internal class HeadlessPlayer(private val song: Song, private val settings: PracticeSettings) {

    /** A Note alphaTab started, [atMillis] after Playback started, on MIDI [channel]. */
    data class Played(val atMillis: Double, val channel: Int, val key: Int)

    val score: Score
    private val output = SilentOutput()
    private val synth: AlphaSynth
    private val tickLookup: alphaTab.midi.MidiTickLookup

    private var elapsed = 0.0
    private val played = mutableListOf<Played>()
    private var mainStartedAt: Double? = null

    init {
        val alphaTabSettings = Settings()
        score = song.toAlphaTabScore(alphaTabSettings)
        val midi = MidiFile()
        val generator = MidiFileGenerator(score, alphaTabSettings, AlphaSynthMidiFileHandler(midi))
        generator.generate()
        tickLookup = generator.tickLookup

        synth = AlphaSynth(output, BUFFER_MILLIS)
        synth.midiEventsPlayedFilter = alphaTab.collections.List(MidiEventType.NoteOn)
        synth.loadMidiFile(midi)
        settings.playerControls().let {
            synth.playbackSpeed = it.playbackSpeed
            synth.metronomeVolume = it.metronomeVolume
            synth.countInVolume = it.countInVolume
        }
        // As NotationPlayer does once the player is ready.
        val loop = settings.loop?.measuresIn(song)
        val range = loop?.let { tickLookup.playbackRangeOf(score, it) }
        synth.playbackRange = range
        synth.isLooping = range != null
        synth.silence(score, song.tracks.indices.filterNot(settings::isHeard).toSet())

        synth.midiEventsPlayed.on { args ->
            for (event in args.events) {
                if (event is NoteOnEvent) played += Played(elapsed, event.channel.toInt(), event.noteKey.toInt())
            }
        }
        // Reports only while the Song itself plays, so its first report is the end of any count-in.
        synth.positionChanged.on { if (!it.isSeek && mainStartedAt == null) mainStartedAt = elapsed }
    }

    /** Taps Measure [measure] (0-based), as Practice mode does, and listens for [millis]. */
    fun playFrom(measure: Int, millis: Double): HeadlessPlayer {
        val start = playbackStart(settings.loop?.measuresIn(song), measure)
        synth.tickPosition = tickLookup.getMasterBarStart(score.masterBars[start])
        check(synth.play()) { "alphaTab didn't start" }
        while (elapsed < millis) {
            output.requestSamples()
            // Reported in micro-buffers, so what was played is known to within about 1.5 ms.
            var frames = output.takeFrames()
            while (frames > 0) {
                val chunk = minOf(frames, MICRO_BUFFER_FRAMES)
                frames -= chunk
                elapsed += chunk * 1000.0 / SAMPLE_RATE
                output.reportPlayed(chunk)
            }
        }
        return this
    }

    /** Every Note started, in order. */
    val notes: List<Played> get() = played.toList()

    /** How long the count-in lasted (about 0 without one). */
    val countInMillis: Double get() = checkNotNull(mainStartedAt) { "The Song never started" }

    /** Whether [note]'s channel is muted. */
    fun isMuted(note: Played): Boolean =
        score.channelMutes(settings::isHeard)[note.channel] == true

    private class Emitter : IEventEmitter {
        val listeners = mutableListOf<() -> Unit>()
        override fun on(value: () -> Unit): () -> Unit = value.also { listeners += it }.let { { listeners -= value } }
        override fun off(value: () -> Unit) {
            listeners -= value
        }
    }

    private class EmitterOf<T> : IEventEmitterOfT<T> {
        val listeners = mutableListOf<(T) -> Unit>()
        override fun on(value: (T) -> Unit): () -> Unit = value.also { listeners += it }.let { { listeners -= value } }
        override fun off(value: (T) -> Unit) {
            listeners -= value
        }
    }

    /** An audio output that plays nothing and is driven by the test. */
    private class SilentOutput : ISynthOutput {
        override val sampleRate = SAMPLE_RATE
        override val ready = Emitter()
        override val samplesPlayed = EmitterOf<Double>()
        override val sampleRequest = Emitter()
        private var frames = 0

        fun requestSamples() = sampleRequest.listeners.toList().forEach { it() }
        fun takeFrames(): Int = frames.also { frames = 0 }
        fun reportPlayed(frames: Int) = samplesPlayed.listeners.toList().forEach { it(frames.toDouble()) }

        override fun open(bufferTimeInMilliseconds: Double) = ready.listeners.toList().forEach { it() }
        override fun addSamples(samples: Float32Array) {
            frames += samples.data.size / AUDIO_CHANNELS
        }
        override fun play() = Unit
        override fun destroy() = Unit
        override fun pause() = Unit
        override fun resetSamples() = Unit
        override fun activate() = Unit
        override suspend fun enumerateOutputDevices() = alphaTab.collections.List<ISynthOutputDevice>()
        override suspend fun setOutputDevice(device: ISynthOutputDevice?) = Unit
        override suspend fun getOutputDevice(): ISynthOutputDevice? = null
    }

    private companion object {
        const val SAMPLE_RATE = 44_100.0
        const val AUDIO_CHANNELS = 2
        const val MICRO_BUFFER_FRAMES = 64
        const val BUFFER_MILLIS = 500.0
    }
}
