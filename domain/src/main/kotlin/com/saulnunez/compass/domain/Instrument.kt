package com.saulnunez.compass.domain

/** A pitch as a MIDI note number (60 = middle C). */
@JvmInline
value class Pitch(val midi: Int)

enum class InstrumentKind { GUITAR, BASS }

/** The open-string pitches of an Instrument, from string 1 (highest) to the lowest string. */
data class Tuning(val openStrings: List<Pitch>) {
    val stringCount: Int get() = openStrings.size

    /** This Tuning with string [string] (from 1) tuned to [pitch]. */
    fun withString(string: Int, pitch: Pitch): Tuning {
        require(string in 1..stringCount) { "No string $string on a $stringCount-string Tuning" }
        return Tuning(openStrings.toMutableList().apply { set(string - 1, pitch) })
    }
}

data class Instrument(val kind: InstrumentKind, val tuning: Tuning) {
    val stringCount: Int get() = tuning.stringCount

    /** A Note's pitch follows from its string's Tuning plus its fret. */
    fun pitchOf(note: Note): Pitch = Pitch(tuning.openStrings[note.string - 1].midi + note.fret)
}
