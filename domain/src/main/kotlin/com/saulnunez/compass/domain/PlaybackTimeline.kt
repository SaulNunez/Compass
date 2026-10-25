package com.saulnunez.compass.domain

/**
 * What Playback sounds and when, in milliseconds from the moment Playback starts.
 * It defines Playback's behaviour; the player is driven to match it.
 */
data class PlaybackTimeline(
    /** The Measures played, in order. */
    val measures: List<TimedMeasure>,
    /** Every Note played, in order of [TimedNote.startMillis] within each Track. */
    val notes: List<TimedNote>,
    /** The count-in, then the click on every beat, in order. */
    val clicks: List<Click>,
)

/** A Measure as played: when it starts, how long it lasts and its Tempo after the tempo percentage. */
data class TimedMeasure(val measure: Int, val startMillis: Double, val lengthMillis: Double, val beatsPerMinute: Double)

/** A Note of Track [track] in Measure [measure] (both 0-based) as played. */
data class TimedNote(val startMillis: Double, val lengthMillis: Double, val track: Int, val measure: Int, val note: Note)

/** A metronome click on a beat, either of the count-in or of the click track. */
data class Click(val startMillis: Double, val isCountIn: Boolean)

/**
 * Turns this Song and its [settings] into the [PlaybackTimeline] Playback follows when it starts at
 * Measure [fromMeasure] (0-based): every Tempo scaled by the tempo percentage, all Tracks together
 * except those muted or left out of a solo, the click on every beat when it is on, and a single
 * count-in before the first Measure.
 *
 * With a Loop, Playback stays within the Loop's Measures (starting at its first Measure when
 * [fromMeasure] is outside it), plays to its last Measure and goes straight back to its first,
 * with no gap and no further count-in. Playback repeats a Loop until stopped, so the timeline
 * lays out [passes] times through it (the first starting at [fromMeasure]).
 */
fun Song.playbackTimeline(settings: PracticeSettings, fromMeasure: Int = 0, passes: Int = 1): PlaybackTimeline {
    require(fromMeasure in measures.indices) { "No Measure ${fromMeasure + 1} in a Song of ${measures.size}" }
    require(passes >= 1) { "Playback goes through a Loop at least once" }
    val loop = settings.loop?.measuresIn(this)
    val start = if (loop == null || fromMeasure in loop) fromMeasure else loop.first
    val played = if (loop == null) {
        (start until measures.size).toList()
    } else {
        (start..loop.last) + List(passes - 1) { loop }.flatten()
    }
    val timedMeasures = mutableListOf<TimedMeasure>()
    val notes = mutableListOf<TimedNote>()
    val clicks = mutableListOf<Click>()

    var measureStart = 0.0
    if (settings.countIn) {
        // One Measure of clicks in the time signature and Tempo Playback starts in.
        val first = timing(measures[start], settings)
        clicks += first.beatStarts(0.0).map { Click(it, isCountIn = true) }
        measureStart = first.lengthMillis
    }

    for (index in played) {
        val timing = timing(measures[index], settings)
        timedMeasures += TimedMeasure(index, measureStart, timing.lengthMillis, timing.beatsPerMinute)
        if (settings.click) clicks += timing.beatStarts(measureStart).map { Click(it, isCountIn = false) }

        tracks.forEachIndexed { trackIndex, track ->
            if (!settings.isHeard(trackIndex)) return@forEachIndexed
            var ticksBefore = 0
            track.voice.measures[index].forEach { element ->
                val ticks = element.duration.ticks
                // A Chord's Notes all start together.
                element.notes.forEach { note ->
                    val start = measureStart + timing.millis(ticksBefore)
                    notes += TimedNote(start, timing.millis(ticks), trackIndex, index, note)
                }
                ticksBefore += ticks
            }
        }
        measureStart += timing.lengthMillis
    }
    return PlaybackTimeline(timedMeasures, notes, clicks)
}

/** How a Measure's time signature and Tempo play out at the tempo percentage. */
private class MeasureTiming(val signature: TimeSignature, val beatsPerMinute: Double) {
    private val beatTicks = TICKS_PER_WHOLE / signature.beatUnit
    val lengthMillis = millis(signature.beats * beatTicks)

    /** How long [ticks] last; Tempos count quarter notes per minute. */
    fun millis(ticks: Int): Double = ticks * MILLIS_PER_MINUTE / (beatsPerMinute * TICKS_PER_QUARTER)

    fun beatStarts(measureStart: Double) = List(signature.beats) { measureStart + millis(it * beatTicks) }
}

private fun timing(measure: Measure, settings: PracticeSettings) =
    MeasureTiming(measure.timeSignature, settings.tempoPercentage.scale(measure.tempo))

private const val MILLIS_PER_MINUTE = 60_000.0
private const val TICKS_PER_QUARTER = TICKS_PER_WHOLE / 4
