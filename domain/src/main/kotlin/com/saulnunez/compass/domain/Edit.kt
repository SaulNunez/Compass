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

/**
 * Inserts an empty Measure into every Track before Measure [at]; [at] one past the last Measure
 * appends it. It takes the time signature, key signature and Tempo of the Measure before it
 * (or of the first Measure when inserted at the start), so no change moves.
 */
data class InsertMeasure(val at: Int) : Edit {
    override fun applyTo(song: Song): Song {
        require(at in 0..song.measures.size) { "Cannot insert a Measure at ${at + 1}" }
        val inserted = song.measures[(at - 1).coerceAtLeast(0)]
        return song.copy(
            measures = song.measures.toMutableList().apply { add(at, inserted) },
            tracks = song.tracks.map { track ->
                track.copy(voice = Voice(track.voice.measures.toMutableList().apply { add(at, emptyList()) }))
            },
        )
    }
}

/** Removes Measure [measure] from every Track; what followed it moves up. A Song keeps at least one Measure. */
data class DeleteMeasure(val measure: Int) : Edit {
    override fun applyTo(song: Song): Song {
        require(song.measures.size > 1) { "A Song keeps at least one Measure" }
        return song.copy(
            measures = song.measures.toMutableList().apply { removeAt(measure) },
            tracks = song.tracks.map { track ->
                track.copy(voice = Voice(track.voice.measures.toMutableList().apply { removeAt(measure) }))
            },
        )
    }
}

/** Changes the time signature at Measure [measure]; see [changeFrom] for how far it reaches. */
data class ChangeTimeSignature(val measure: Int, val timeSignature: TimeSignature) : Edit {
    override fun applyTo(song: Song): Song =
        song.changeFrom(measure, Measure::timeSignature) { it.copy(timeSignature = timeSignature) }
}

/** Changes the key signature at Measure [measure]; see [changeFrom] for how far it reaches. */
data class ChangeKeySignature(val measure: Int, val keySignature: KeySignature) : Edit {
    override fun applyTo(song: Song): Song =
        song.changeFrom(measure, Measure::keySignature) { it.copy(keySignature = keySignature) }
}

/** Changes the Tempo at Measure [measure]; see [changeFrom] for how far it reaches. */
data class ChangeTempo(val measure: Int, val tempo: Tempo) : Edit {
    override fun applyTo(song: Song): Song =
        song.changeFrom(measure, Measure::tempo) { it.copy(tempo = tempo) }
}

/**
 * Gives Measure [measure] the time signature, key signature and Tempo of [settings] in one edit;
 * each setting that differs changes as [ChangeTimeSignature], [ChangeKeySignature] and [ChangeTempo] do.
 */
data class ChangeMeasure(val measure: Int, val settings: Measure) : Edit {
    override fun applyTo(song: Song): Song = listOf(
        ChangeTimeSignature(measure, settings.timeSignature),
        ChangeKeySignature(measure, settings.keySignature),
        ChangeTempo(measure, settings.tempo),
    ).fold(song) { edited, edit -> edit.applyTo(edited) }
}

/** Adds a Track after the last one, with every Measure of the Song left empty. */
data class AddTrack(val name: String, val instrument: Instrument, val sound: Sound) : Edit {
    override fun applyTo(song: Song): Song =
        song.copy(tracks = song.tracks + Track(name, instrument, Voice(List(song.measures.size) { emptyList() }), sound))
}

/** Removes the Track at index [track]; the Tracks after it move up. A Song keeps at least one Track. */
data class RemoveTrack(val track: Int) : Edit {
    override fun applyTo(song: Song): Song {
        require(song.tracks.size > 1) { "A Song keeps at least one Track" }
        return song.copy(tracks = song.tracks.toMutableList().apply { removeAt(track) })
    }
}

data class RenameTrack(val track: Int, val name: String) : Edit {
    override fun applyTo(song: Song): Song = song.editTrack(track) { it.copy(name = name) }
}

/**
 * Tunes the Track at index [track] to [tuning], which must have as many strings. Every fret stays
 * as written, so the Notes sound at new pitches.
 */
data class ChangeTuning(val track: Int, val tuning: Tuning) : Edit {
    override fun applyTo(song: Song): Song = song.editTrack(track) {
        require(tuning.stringCount == it.instrument.stringCount) {
            "A ${it.instrument.stringCount}-string Track can't take a ${tuning.stringCount}-string Tuning"
        }
        it.copy(instrument = it.instrument.copy(tuning = tuning))
    }
}

/** Gives the Track at index [track] a [sound] suited to its Instrument. */
data class ChangeSound(val track: Int, val sound: Sound) : Edit {
    override fun applyTo(song: Song): Song = song.editTrack(track) { it.copy(sound = sound) }
}

/**
 * Gives the Track at index [track] a new name, Tuning and Sound in one edit, each changed as
 * [RenameTrack], [ChangeTuning] and [ChangeSound] do.
 */
data class ChangeTrack(val track: Int, val name: String, val tuning: Tuning, val sound: Sound) : Edit {
    override fun applyTo(song: Song): Song = listOf(RenameTrack(track, name), ChangeTuning(track, tuning), ChangeSound(track, sound))
        .fold(song) { edited, edit -> edit.applyTo(edited) }
}

private fun Song.editTrack(track: Int, change: (Track) -> Track): Song =
    copy(tracks = tracks.toMutableList().apply { set(track, change(get(track))) })

/**
 * Applies [change] to Measure [measure] and to the Measures after it that share its old
 * [setting], so the change lasts until the next change already written (or the end of the Song).
 */
private fun <T> Song.changeFrom(measure: Int, setting: (Measure) -> T, change: (Measure) -> Measure): Song {
    val old = setting(measures[measure])
    val end = (measure until measures.size).firstOrNull { setting(measures[it]) != old } ?: measures.size
    return copy(measures = measures.mapIndexed { index, it -> if (index in measure until end) change(it) else it })
}

private fun Song.editMeasure(track: Int, measure: Int, change: (List<VoiceElement>) -> List<VoiceElement>): Song {
    val edited = tracks[track].let { it.copy(voice = Voice(it.voice.measures.toMutableList().apply { set(measure, change(get(measure))) })) }
    return copy(tracks = tracks.toMutableList().apply { set(track, edited) })
}
