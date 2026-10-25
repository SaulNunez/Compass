package com.saulnunez.compass.domain

/**
 * A single change to a Song. Every change made in Edit mode is one of these, so all of
 * them can be saved and undone the same way.
 *
 * Positions are 0-based: [track] indexes the Song's Tracks, [measure] its Measures, and
 * [beat] the Notes and Rests of that Measure in the Track's Voice, in order.
 */
sealed interface Edit {
    fun applyTo(song: Song): Song
}

/** Writes [element] at [beat], replacing what is there; [beat] one past the last element appends it. */
data class WriteElement(val track: Int, val measure: Int, val beat: Int, val element: VoiceElement) : Edit {
    override fun applyTo(song: Song): Song = song.editMeasure(track, measure) { elements ->
        require(beat in 0..elements.size) { "Beat $beat is outside Measure ${measure + 1}" }
        if (beat == elements.size) elements + element else elements.toMutableList().apply { set(beat, element) }
    }
}

/** Gives the Note or Rest at [beat] a new [duration]. */
data class ChangeDuration(val track: Int, val measure: Int, val beat: Int, val duration: Duration) : Edit {
    override fun applyTo(song: Song): Song = song.editMeasure(track, measure) { elements ->
        elements.toMutableList().apply {
            set(beat, when (val element = get(beat)) {
                is Note -> element.copy(duration = duration)
                is Rest -> element.copy(duration = duration)
            })
        }
    }
}

/** Removes the Note or Rest at [beat]; what followed it moves up. */
data class DeleteElement(val track: Int, val measure: Int, val beat: Int) : Edit {
    override fun applyTo(song: Song): Song = song.editMeasure(track, measure) { elements ->
        elements.toMutableList().apply { removeAt(beat) }
    }
}

private fun Song.editMeasure(track: Int, measure: Int, change: (List<VoiceElement>) -> List<VoiceElement>): Song {
    val edited = tracks[track].let { it.copy(voice = Voice(it.voice.measures.toMutableList().apply { set(measure, change(get(measure))) })) }
    return copy(tracks = tracks.toMutableList().apply { set(track, edited) })
}
