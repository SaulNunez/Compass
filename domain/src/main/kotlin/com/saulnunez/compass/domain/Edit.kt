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

/**
 * Writes [note] on its string at [beat]. A Note already on that string is replaced; Notes on
 * other strings stay, so the beat becomes a Chord and [note] takes their Duration. A Rest is
 * replaced; [beat] one past the last element appends [note].
 */
data class WriteNote(val track: Int, val measure: Int, val beat: Int, val note: Note) : Edit {
    override fun applyTo(song: Song): Song = song.editMeasure(track, measure) { elements ->
        require(beat in 0..elements.size) { "Beat $beat is outside Measure ${measure + 1}" }
        val existing = elements.getOrNull(beat) ?: return@editMeasure elements + note
        val others = existing.notes.filter { it.string != note.string }
        val duration = if (others.isEmpty()) note.duration else existing.duration
        elements.toMutableList().apply { set(beat, elementOf(others + note.copy(duration = duration), duration)) }
    }
}

/** Removes the Note on [string] from the Chord at [beat], leaving its other Notes. */
data class DeleteNote(val track: Int, val measure: Int, val beat: Int, val string: Int) : Edit {
    override fun applyTo(song: Song): Song = song.editMeasure(track, measure) { elements ->
        val chord = elements[beat]
        require(chord is Chord && chord.noteOn(string) != null) { "No Chord Note on string $string at beat ${beat + 1}" }
        elements.toMutableList().apply { set(beat, elementOf(chord.notes.filter { it.string != string }, chord.duration)) }
    }
}

/**
 * Ties the Note on [string] at [beat] to the Note just before it on the same string, which
 * may be in the previous Measure, giving it that fret; or unties it when [isTied] is false.
 */
data class SetTie(val track: Int, val measure: Int, val beat: Int, val string: Int, val isTied: Boolean) : Edit {
    override fun applyTo(song: Song): Song {
        if (isTied) {
            requireNotNull(song.tracks[track].voice.elementBefore(measure, beat)?.noteOn(string)) {
                "No Note before beat ${beat + 1} on string $string to tie to"
            }
        }
        return song.editMeasure(track, measure) { elements ->
            elements.toMutableList().apply {
                set(beat, get(beat).mapNotes { if (it.string == string) it.copy(isTied = isTied) else it })
            }
        }
    }
}

/**
 * Adds [technique] to the Note on [string] at [beat], taking off any Technique it cannot be
 * played with; or takes it off when [isAdded] is false.
 */
data class SetTechnique(
    val track: Int,
    val measure: Int,
    val beat: Int,
    val string: Int,
    val technique: Technique,
    val isAdded: Boolean,
) : Edit {
    override fun applyTo(song: Song): Song {
        require(!isAdded || technique.isOfferedOn(song.tracks[track].instrument.kind)) {
            "$technique is not played on ${song.tracks[track].instrument.kind}"
        }
        return song.editMeasure(track, measure) { elements ->
            require(elements[beat].noteOn(string) != null) { "No Note on string $string at beat ${beat + 1}" }
            elements.toMutableList().apply {
                set(beat, get(beat).mapNotes { note ->
                    if (note.string != string) return@mapNotes note
                    val kept = note.techniques.filterNot { it == technique || technique.excludes(it) }.toSet()
                    note.copy(techniques = if (isAdded) kept + technique else kept)
                })
            }
        }
    }
}

/** Gives the Note, Chord or Rest at [beat] a new [duration]. */
data class ChangeDuration(val track: Int, val measure: Int, val beat: Int, val duration: Duration) : Edit {
    override fun applyTo(song: Song): Song = song.editMeasure(track, measure) { elements ->
        elements.toMutableList().apply {
            set(beat, when (val element = get(beat)) {
                is Note -> element.copy(duration = duration)
                is Chord -> Chord(element.notes.map { it.copy(duration = duration) })
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
                track.copy(voice = Voice(track.voice.measures.toMutableList().apply { add(at, emptyList()) }).withTiesFollowed())
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
                track.copy(voice = Voice(track.voice.measures.toMutableList().apply { removeAt(measure) }).withTiesFollowed())
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
    val edited = tracks[track].let { it.copy(voice = Voice(it.voice.measures.toMutableList().apply { set(measure, change(get(measure))) }).withTiesFollowed()) }
    return copy(tracks = tracks.toMutableList().apply { set(track, edited) })
}
