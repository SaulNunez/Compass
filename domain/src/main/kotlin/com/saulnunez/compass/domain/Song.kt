package com.saulnunez.compass.domain

data class TimeSignature(val beats: Int, val beatUnit: Int)

/** Sharps (positive) or flats (negative) in the key, and whether it is a minor key. */
data class KeySignature(val fifths: Int, val isMinor: Boolean = false) {
    companion object {
        val C_MAJOR = KeySignature(0)

        /** Every key signature from 7 flats to 7 sharps, major keys first. */
        val ALL: List<KeySignature> = listOf(false, true).flatMap { isMinor -> (-7..7).map { KeySignature(it, isMinor) } }
    }
}

@JvmInline
value class Tempo(val beatsPerMinute: Int)

/** A span of a Song shared by every Track. */
data class Measure(
    val timeSignature: TimeSignature,
    val keySignature: KeySignature,
    val tempo: Tempo,
)

/**
 * How long a Song's Pickup lasts, in 16th notes. It must be shorter than the first Measure's time
 * signature; the Pickup is the last part of that Measure, so a one-beat Pickup in 4/4 is beat 4.
 */
data class Pickup(val sixteenths: Int) {
    init {
        require(sixteenths >= 1) { "A Pickup lasts at least a 16th note" }
    }

    internal val ticks: Int get() = sixteenths * TICKS_PER_WHOLE / 16
}

/** How long a full Measure of [this] time signature lasts, in ticks. */
internal val TimeSignature.ticks: Int get() = TICKS_PER_WHOLE * beats / beatUnit

/**
 * A named, contiguous range of Measures, from Measure [first] to Measure [last] (0-based, both included).
 */
data class Section(val name: String, val first: Int, val last: Int) {
    init {
        require(name.isNotBlank()) { "A Section needs a name" }
        require(first in 0..last) { "Section '$name' must start at or before its last Measure" }
    }

    operator fun contains(measure: Int): Boolean = measure in first..last
}

/**
 * A Song's stable identity, kept across edits, Backups and Song files.
 * Limited to letters, digits, '-' and '_' because it is also used as a file name.
 */
@JvmInline
value class SongId(val value: String) {
    init {
        require(value.matches(SAFE_ID)) { "Not a valid SongId: '$value'" }
    }
}

private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,64}")

data class Song(
    val id: SongId,
    val title: String,
    val artist: String,
    val measures: List<Measure>,
    val tracks: List<Track>,
    /** In Measure order; no two share a Measure. */
    val sections: List<Section> = emptyList(),
    /** Set when the first Measure is a Pickup. */
    val pickup: Pickup? = null,
) {
    init {
        if (pickup != null) {
            val first = measures.firstOrNull()
            require(first != null && pickup.ticks < first.timeSignature.ticks) {
                "A Pickup must be shorter than the first Measure's time signature"
            }
        }
        sections.forEach { section ->
            require(section.last < measures.size) { "Section '${section.name}' reaches past the last Measure" }
        }
        sections.zipWithNext().forEach { (before, after) ->
            require(before.last < after.first) { "Sections '${before.name}' and '${after.name}' overlap or are out of order" }
        }
        tracks.forEach { track ->
            require(track.voice.measures.size == measures.size) {
                "Track '${track.name}' has ${track.voice.measures.size} Measures but the Song has ${measures.size}"
            }
        }
    }
}

/**
 * Whether Measures [first] to [last] (0-based) can be a Section: within the Song and not sharing a
 * Measure with any Section other than the one at index [ignoring].
 */
fun Song.canPlaceSection(first: Int, last: Int, ignoring: Int? = null): Boolean =
    first in 0..last && last < measures.size &&
        sections.withIndex().none { (index, section) -> index != ignoring && section.first <= last && first <= section.last }

/** Measures a new Song starts with, so there is room to write straight away. */
const val NEW_SONG_MEASURE_COUNT = 4

fun newSong(
    id: SongId,
    title: String,
    artist: String,
    startingMeasure: Measure,
    trackName: String,
    instrument: Instrument,
): Song = Song(
    id = id,
    title = title,
    artist = artist,
    measures = List(NEW_SONG_MEASURE_COUNT) { startingMeasure },
    tracks = listOf(Track(trackName, instrument, Voice(List(NEW_SONG_MEASURE_COUNT) { emptyList() }))),
)

/** How much of a Measure's time signature a Track has written. */
enum class MeasureFill {
    /** Nothing written yet. */
    UNWRITTEN,

    /** Shorter than the time signature. */
    INCOMPLETE,
    FULL,

    /** Longer than the time signature. */
    OVERFULL,
}

/** How long Measure [measure] (0-based) lasts in ticks: its time signature, or the Pickup's length. */
internal fun Song.measureTicks(measure: Int): Int =
    if (measure == 0 && pickup != null) pickup.ticks else measures[measure].timeSignature.ticks

/** How much of Measure [measure] (0-based) the Track at index [track] has filled; a Pickup is full at its own length. */
fun Song.measureFill(track: Int, measure: Int): MeasureFill {
    val elements = tracks[track].voice.measures[measure]
    if (elements.isEmpty()) return MeasureFill.UNWRITTEN
    val written = elements.sumOf { it.duration.ticks }
    val required = measureTicks(measure)
    return when {
        written < required -> MeasureFill.INCOMPLETE
        written > required -> MeasureFill.OVERFULL
        else -> MeasureFill.FULL
    }
}

/**
 * How long the Track at index [track] rests at the start of the Pickup: a Track that writes less
 * than the Pickup plays what it wrote at the Pickup's end. 0 with no Pickup, or when the Track fills it or more.
 */
internal fun Song.pickupLeadIn(track: Int): Int {
    if (pickup == null) return 0
    return (pickup.ticks - tracks[track].voice.measures[0].sumOf { it.duration.ticks }).coerceAtLeast(0)
}

/**
 * The Rests, longest first, a Track at index [track] rests for at the start of the Pickup before
 * what it wrote (see [pickupLeadIn]), down to a 32nd note.
 */
fun Song.restsLeadingIntoPickup(track: Int): List<Rest> {
    var left = pickupLeadIn(track)
    return NoteValue.entries.sortedByDescending { it.ticks }.flatMap { value ->
        List(left / value.ticks) { Rest(Duration(value)) }.also { left %= value.ticks }
    }
}

/**
 * The number Measure [measure] (0-based) is shown with. Measure 1 is the first full Measure, so a
 * Pickup, which is not numbered, takes 0 where a number has to be typed.
 */
fun Song.measureNumber(measure: Int): Int = if (pickup != null) measure else measure + 1

/** The Measure (0-based) shown as [number] (see [measureNumber]), or null when the Song has none. */
fun Song.measureNumbered(number: Int): Int? = (if (pickup != null) number else number - 1).takeIf { it in measures.indices }

/** Whether Measure [measure] (0-based) is the Song's Pickup. */
fun Song.isPickup(measure: Int): Boolean = measure == 0 && pickup != null
