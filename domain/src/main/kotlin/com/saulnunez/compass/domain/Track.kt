package com.saulnunez.compass.domain

/** A Track's single rhythmic line: its elements for each Measure of the Song, in order. */
data class Voice(val measures: List<List<VoiceElement>>) {

    /** The element just before [beat] of Measure [measure], reaching back into the previous Measure; null when there is none. */
    fun elementBefore(measure: Int, beat: Int): VoiceElement? = when {
        beat > 0 -> measures[measure][beat - 1]
        measure > 0 -> measures[measure - 1].lastOrNull()
        else -> null
    }

    /**
     * This Voice with every tied Note given the fret of the Note it is tied to, and untied when
     * there is no Note just before it on its string any more.
     */
    internal fun withTiesFollowed(): Voice {
        var previous: VoiceElement? = null
        return Voice(
            measures.map { elements ->
                elements.map { element ->
                    val before = previous
                    element.mapNotes { note ->
                        if (!note.isTied) return@mapNotes note
                        val origin = before?.noteOn(note.string)
                        if (origin == null) note.copy(isTied = false) else note.copy(fret = origin.fret)
                    }.also { previous = it }
                }.also { if (elements.isEmpty()) previous = null }
            },
        )
    }
}

data class Track(val name: String, val instrument: Instrument, val voice: Voice) {
    init {
        voice.measures.flatten().flatMap { it.notes }.forEach { note ->
            require(note.string in 1..instrument.stringCount) {
                "String ${note.string} is not on a ${instrument.stringCount}-string ${instrument.kind}"
            }
            require(note.fret >= 0) { "Fret ${note.fret} is below the nut" }
        }
    }
}
