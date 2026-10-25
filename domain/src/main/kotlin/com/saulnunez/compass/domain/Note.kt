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

/** Something that takes up time in a Voice: a Note or a Rest. */
sealed interface VoiceElement {
    val duration: Duration
}

/** A sound on one string (1 = highest-pitched string) at one fret (0 = open) for a duration. */
data class Note(val string: Int, val fret: Int, override val duration: Duration) : VoiceElement

data class Rest(override val duration: Duration) : VoiceElement
