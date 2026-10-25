package com.saulnunez.compass.domain

/** The written note value, from a whole note down to a 32nd note. */
enum class NoteValue(internal val ticks: Int) {
    WHOLE(TICKS_PER_WHOLE),
    HALF(TICKS_PER_WHOLE / 2),
    QUARTER(TICKS_PER_WHOLE / 4),
    EIGHTH(TICKS_PER_WHOLE / 8),
    SIXTEENTH(TICKS_PER_WHOLE / 16),
    THIRTY_SECOND(TICKS_PER_WHOLE / 32),
}

/** How long a Note or Rest lasts: a note value, optionally dotted (×3/2) and/or a triplet (×2/3). */
data class Duration(val value: NoteValue, val isDotted: Boolean = false, val isTriplet: Boolean = false) {

    /** The length in ticks of a whole note divided by [TICKS_PER_WHOLE], exact for every Duration Compass offers. */
    internal val ticks: Int
        get() {
            var ticks = value.ticks
            if (isDotted) ticks = ticks * 3 / 2
            if (isTriplet) ticks = ticks * 2 / 3
            return ticks
        }

    companion object {
        val WHOLE = Duration(NoteValue.WHOLE)
        val HALF = Duration(NoteValue.HALF)
        val QUARTER = Duration(NoteValue.QUARTER)
        val EIGHTH = Duration(NoteValue.EIGHTH)
        val SIXTEENTH = Duration(NoteValue.SIXTEENTH)
        val THIRTY_SECOND = Duration(NoteValue.THIRTY_SECOND)
    }
}

/** Divisible down to a dotted or triplet 32nd note, so every Duration is a whole number of ticks. */
internal const val TICKS_PER_WHOLE = 3840

/** Something that takes up time in a Voice: a Note, a Chord or a Rest. */
sealed interface VoiceElement {
    val duration: Duration
}

/**
 * A sound on one string (1 = highest-pitched string) at one fret (0 = open) for a duration.
 * A tied Note ([isTied]) is not played again: it holds on the Note just before it on the same
 * string, and has its fret.
 */
data class Note(
    val string: Int,
    val fret: Int,
    override val duration: Duration,
    val isTied: Boolean = false,
    val techniques: Set<Technique> = emptySet(),
) : VoiceElement {
    init {
        require(techniques.none { technique -> techniques.any(technique::excludes) }) {
            "Techniques that cannot be played together: $techniques"
        }
    }
}

/** Several Notes that start together on different strings and last the same Duration, listed from string 1 down. */
data class Chord(val notes: List<Note>) : VoiceElement {
    init {
        require(notes.size >= 2) { "A Chord has at least two Notes" }
        require(notes.zipWithNext().all { (above, below) -> above.string < below.string }) {
            "A Chord's Notes are on different strings, listed from string 1 down"
        }
        require(notes.all { it.duration == notes.first().duration }) { "A Chord's Notes all last the same Duration" }
    }

    override val duration: Duration get() = notes.first().duration
}

data class Rest(override val duration: Duration) : VoiceElement

/** The Notes sounding at this element: one for a Note, several for a Chord, none for a Rest. */
val VoiceElement.notes: List<Note>
    get() = when (this) {
        is Note -> listOf(this)
        is Chord -> notes
        is Rest -> emptyList()
    }

/** The Note of this element on [string], if any. */
fun VoiceElement.noteOn(string: Int): Note? = notes.firstOrNull { it.string == string }

/** This element with [change] applied to each of its Notes. */
internal fun VoiceElement.mapNotes(change: (Note) -> Note): VoiceElement = when (this) {
    is Note -> change(this)
    is Chord -> Chord(notes.map(change))
    is Rest -> this
}

/** [notes] as one element: a Note for one, a Chord for several, a Rest of [duration] for none. */
internal fun elementOf(notes: List<Note>, duration: Duration): VoiceElement = when (notes.size) {
    0 -> Rest(duration)
    1 -> notes.single()
    else -> Chord(notes.sortedBy { it.string })
}
