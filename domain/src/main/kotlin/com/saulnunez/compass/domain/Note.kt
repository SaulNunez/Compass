package com.saulnunez.compass.domain

enum class Duration { WHOLE, HALF, QUARTER, EIGHTH, SIXTEENTH, THIRTY_SECOND }

/** Something that takes up time in a Voice: a Note or a Rest. */
sealed interface VoiceElement {
    val duration: Duration
}

/** A sound on one string (1 = highest-pitched string) at one fret (0 = open) for a duration. */
data class Note(val string: Int, val fret: Int, override val duration: Duration) : VoiceElement

data class Rest(override val duration: Duration) : VoiceElement
