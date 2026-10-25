package com.saulnunez.compass.domain

/** A Track's single rhythmic line: its elements for each Measure of the Song, in order. */
data class Voice(val measures: List<List<VoiceElement>>)

data class Track(
    val name: String,
    val instrument: Instrument,
    val voice: Voice,
    val sound: Sound = Sound.defaultFor(instrument.kind),
) {
    init {
        require(sound.kind == instrument.kind) { "A ${instrument.kind} Track can't use the $sound Sound" }
        voice.measures.flatten().filterIsInstance<Note>().forEach { note ->
            require(note.string in 1..instrument.stringCount) {
                "String ${note.string} is not on a ${instrument.stringCount}-string ${instrument.kind}"
            }
            require(note.fret >= 0) { "Fret ${note.fret} is below the nut" }
        }
    }
}
