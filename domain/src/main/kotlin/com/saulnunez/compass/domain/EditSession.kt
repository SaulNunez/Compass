package com.saulnunez.compass.domain

/** Where Edit mode writes next: a Measure, a beat within it, and a string (all as in [Edit]; strings from 1). */
data class Cursor(val measure: Int, val beat: Int, val string: Int)

/**
 * One editing session of a Song in Edit mode: the Song as edited so far, the cursor, the
 * chosen Duration and the undo/redo history of the session's edits. Immutable; every action
 * returns the next session. Every change to the Song goes through an [Edit].
 */
class EditSession private constructor(
    val song: Song,
    val cursor: Cursor,
    /** The Duration the next written Note or Rest gets; follows the element under the cursor. */
    val duration: Duration,
    /** The index of the Track being edited. */
    val track: Int,
    /**
     * For each Track of [song], its index when the session started, or null if added since; lets
     * Practice settings follow the Tracks (see [reconcile]).
     */
    private val trackOrigins: List<Int?> = song.tracks.indices.toList(),
    /** The digit just typed, when another digit typed straight after it can make a two-digit fret. */
    private val pendingDigit: Int? = null,
    /** What to go back to on undo, most recent last. */
    private val undoStack: List<Snapshot> = emptyList(),
    private val redoStack: List<Snapshot> = emptyList(),
    /** The Measures last copied with [copySection], ready to paste. Not part of the Song, so undo leaves it. */
    private val copied: CopiedMeasures? = null,
    /** Where the Measures and Sections of the Song the session started with are in [song] now. */
    private val restructure: Restructure = Restructure.none(song),
) {

    /** The Song, the Track being edited, where the cursor was and where things were moved, before or after an edit. */
    private data class Snapshot(
        val song: Song,
        val track: Int,
        val cursor: Cursor,
        val trackOrigins: List<Int?>,
        val restructure: Restructure,
    )

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    /** Goes back to the Song before the latest edit, with the cursor where that edit was made. */
    fun undo(): EditSession {
        val previous = undoStack.lastOrNull() ?: return this
        return copy(
            song = previous.song,
            track = previous.track,
            trackOrigins = previous.trackOrigins,
            restructure = previous.restructure,
            undoStack = undoStack.dropLast(1),
            redoStack = redoStack + snapshot(),
        ).moveTo(previous.cursor)
    }

    /** Brings back the latest undone edit. */
    fun redo(): EditSession {
        val next = redoStack.lastOrNull() ?: return this
        return copy(
            song = next.song,
            track = next.track,
            trackOrigins = next.trackOrigins,
            restructure = next.restructure,
            undoStack = undoStack + snapshot(),
            redoStack = redoStack.dropLast(1),
        ).moveTo(next.cursor)
    }

    /**
     * Writes [digit] as the fret of a Note on the cursor's string. A digit typed straight after
     * another one makes a two-digit fret with it (1 then 2 is fret 12), up to [HIGHEST_FRET].
     * Notes already on other strings at the cursor stay, making a Chord.
     */
    fun typeDigit(digit: Int): EditSession {
        require(digit in 0..9) { "Not a digit: $digit" }
        val twoDigitFret = pendingDigit?.let { it * 10 + digit }?.takeIf { it <= HIGHEST_FRET }
        val fret = twoDigitFret ?: digit
        // The second digit of a two-digit fret amends the first, so both are undone together.
        val note = Note(cursor.string, fret, duration)
        val written = apply(WriteNote(track, cursor, note), isNewEdit = twoDigitFret == null)
        val canTakeAnotherDigit = twoDigitFret == null && fret != 0 && fret * 10 <= HIGHEST_FRET
        return written.copy(pendingDigit = if (canTakeAnotherDigit) fret else null)
    }

    /**
     * Uses [value] from now on, keeping the dot and triplet. On a Note or Rest it changes that
     * element's Duration; on the free beat after the last element it applies to what is written next.
     */
    fun chooseNoteValue(value: NoteValue): EditSession = chooseDuration(duration.copy(value = value))

    /** Dots or undots the chosen Duration, like [chooseNoteValue]. */
    fun toggleDot(): EditSession = chooseDuration(duration.copy(isDotted = !duration.isDotted))

    /** Makes the chosen Duration a triplet or not, like [chooseNoteValue]. */
    fun toggleTriplet(): EditSession = chooseDuration(duration.copy(isTriplet = !duration.isTriplet))

    /** The Note on the cursor's string at the cursor's beat, if any. */
    val noteAtCursor: Note? get() = elementAtCursor?.noteOn(cursor.string)

    /** Whether the Note at the cursor is tied, or could be: there is a Note just before it on its string. */
    val canTie: Boolean
        get() = noteAtCursor != null &&
            song.tracks[track].voice.elementBefore(cursor.measure, cursor.beat)?.noteOn(cursor.string) != null

    /** Ties the Note at the cursor to the Note just before it on its string, or unties it. */
    fun toggleTie(): EditSession {
        val note = noteAtCursor ?: return this
        if (!canTie) return this
        return apply(SetTie(track, cursor, isTied = !note.isTied))
    }

    /** The Techniques the Track's Instrument offers, in toolbar order. */
    val availableTechniques: List<Technique>
        get() = Technique.offeredOn(song.tracks[track].instrument.kind)

    /** Adds [technique] to the Note at the cursor (replacing one it cannot be played with), or takes it off. */
    fun toggleTechnique(technique: Technique): EditSession {
        val note = noteAtCursor ?: return this
        if (technique !in availableTechniques) return this
        val isAdded = technique !in note.techniques
        return apply(SetTechnique(track, cursor, technique, isAdded))
    }

    /** Writes a Rest at the cursor, replacing the Note there but keeping its Duration. */
    fun writeRest(): EditSession = apply(WriteElement(track, cursor.measure, cursor.beat, Rest(duration)))

    /**
     * Removes the Note or Rest under the cursor; the cursor stays on the beat that moves up.
     * In a Chord only the Note on the cursor's string goes, unless the cursor's string has none.
     */
    fun delete(): EditSession {
        val element = elementAtCursor ?: return this
        if (element is Chord && element.noteOn(cursor.string) != null) {
            return apply(DeleteNote(track, cursor))
        }
        return apply(DeleteElement(track, cursor.measure, cursor.beat)).let { it.moveTo(it.cursor) }
    }

    /** Inserts an empty Measure before the cursor's Measure, in every Track, and moves the cursor into it. */
    fun insertMeasureBefore(): EditSession =
        apply(InsertMeasure(cursor.measure)).moveTo(cursor.copy(beat = 0))

    /** Inserts an empty Measure after the cursor's Measure, in every Track, and moves the cursor into it. */
    fun insertMeasureAfter(): EditSession =
        apply(InsertMeasure(cursor.measure + 1)).moveTo(cursor.copy(measure = cursor.measure + 1, beat = 0))

    /** Whether the cursor's Measure can be deleted: every Song keeps at least one Measure. */
    val canDeleteMeasure: Boolean get() = song.measures.size > 1

    /** Deletes the cursor's Measure from every Track; the cursor stays on the Measure that moves up, or the new last one. */
    fun deleteMeasure(): EditSession {
        if (!canDeleteMeasure) return this
        val deleted = apply(DeleteMeasure(cursor.measure))
        return deleted.moveTo(cursor.copy(measure = cursor.measure.coerceAtMost(deleted.song.measures.lastIndex), beat = 0))
    }

    /** The time signature, key signature and Tempo of the cursor's Measure. */
    val measureAtCursor: Measure get() = song.measures[cursor.measure]

    /**
     * Gives the cursor's Measure the time signature, key signature and Tempo of [settings], as one
     * edit; each changed setting lasts until the next change of that setting already written.
     */
    fun changeMeasure(settings: Measure): EditSession =
        apply(ChangeMeasure(cursor.measure, settings)).let { changed ->
            // A shorter time signature can fill the Measure, taking away the free beat the cursor was on.
            changed.moveTo(cursor.copy(beat = cursor.beat.coerceAtMost(changed.lastBeat(cursor.measure))))
        }

    fun changeTimeSignature(timeSignature: TimeSignature): EditSession =
        changeMeasure(measureAtCursor.copy(timeSignature = timeSignature))

    fun changeKeySignature(keySignature: KeySignature): EditSession =
        changeMeasure(measureAtCursor.copy(keySignature = keySignature))

    fun changeTempo(tempo: Tempo): EditSession = changeMeasure(measureAtCursor.copy(tempo = tempo))

    /** Adds a Track with every Measure of the Song, and edits it from now on. */
    fun addTrack(name: String, instrument: Instrument, sound: Sound): EditSession =
        // Copied Measures hold one part per Track, so they no longer fit once the Tracks change.
        apply(AddTrack(name, instrument, sound)) { it + null }.copy(copied = null).let { it.editTrack(it.song.tracks.lastIndex) }

    /** Edits the Track at index [track] from now on, keeping the cursor's Measure and, where it can, its string. */
    fun editTrack(track: Int): EditSession {
        require(track in song.tracks.indices) { "No Track $track" }
        val string = cursor.string.coerceAtMost(song.tracks[track].instrument.stringCount)
        return copy(track = track).moveTo(cursor.copy(beat = 0, string = string))
    }

    /**
     * The Practice settings [settings], saved for the Song as it was when this session started,
     * reconciled with the edits made since: the Track being read and the muted and soloed Tracks
     * are followed as Tracks before them are added or removed. A removed Track is dropped: its mute
     * and solo go, and reading it goes back to the first Track. A Section Loop follows its Section
     * and goes with it; a Measure-range Loop shifts and shrinks with inserted and deleted Measures,
     * taking in Measures inserted inside it, and is cleared only when none of its Measures remain.
     * Undone edits are undone here too.
     */
    fun reconcile(settings: PracticeSettings): PracticeSettings {
        fun followed(tracks: Set<Int>) = tracks.map(trackOrigins::indexOf).filter { it >= 0 }.toSet()
        return settings.copy(
            readTrack = trackOrigins.indexOf(settings.readTrack).coerceAtLeast(0),
            mutedTracks = followed(settings.mutedTracks),
            soloedTracks = followed(settings.soloedTracks),
            loop = settings.loop?.let(restructure::reconcile),
        )
    }

    /** Whether the Track being edited can be removed: every Song keeps at least one Track. */
    val canRemoveTrack: Boolean get() = song.tracks.size > 1

    /** Removes the Track being edited and edits the one before it (or the new first one). */
    fun removeTrack(): EditSession {
        if (!canRemoveTrack) return this
        return apply(RemoveTrack(track)) { origins -> origins.filterIndexed { index, _ -> index != track } }
            .copy(copied = null)
            .let { it.editTrack((track - 1).coerceAtLeast(0)) }
    }

    fun renameTrack(name: String): EditSession = apply(RenameTrack(track, name))

    /** Tunes the Track being edited to [tuning], keeping every fret. */
    fun changeTuning(tuning: Tuning): EditSession = apply(ChangeTuning(track, tuning))

    fun changeSound(sound: Sound): EditSession = apply(ChangeSound(track, sound))

    /** Renames, retunes and changes the Sound of the Track being edited, as one edit. */
    fun changeTrack(name: String, tuning: Tuning, sound: Sound): EditSession = apply(ChangeTrack(track, name, tuning, sound))
    /** The index of the Section holding the cursor's Measure, or null when it is in none. */
    private val sectionIndexAtCursor: Int?
        get() = song.sections.indexOfFirst { cursor.measure in it }.takeIf { it >= 0 }

    /** The Section holding the cursor's Measure, or null when it is in none. */
    val sectionAtCursor: Section? get() = sectionIndexAtCursor?.let { song.sections[it] }

    /** Whether Measures [first] to [last] (0-based) can be a new Section: no other Section holds any of them. */
    fun canAddSection(first: Int, last: Int): Boolean = song.canPlaceSection(first, last)

    /** Whether the Section under the cursor can span Measures [first] to [last] (0-based) instead. */
    fun canResizeSection(first: Int, last: Int): Boolean =
        sectionIndexAtCursor?.let { song.canPlaceSection(first, last, ignoring = it) } ?: false

    /** Names Measures [first] to [last] (0-based) as a new Section called [name], unless they belong to another Section. */
    fun addSection(name: String, first: Int, last: Int): EditSession {
        if (name.isBlank() || !canAddSection(first, last)) return this
        return apply(AddSection(Section(name.trim(), first, last)))
    }

    /** Renames the Section under the cursor. */
    fun renameSection(name: String): EditSession {
        val section = sectionIndexAtCursor ?: return this
        if (name.isBlank()) return this
        return apply(RenameSection(section, name.trim()))
    }

    /** Makes the Section under the cursor span Measures [first] to [last] (0-based), unless another Section holds any of them. */
    fun resizeSection(first: Int, last: Int): EditSession {
        val section = sectionIndexAtCursor ?: return this
        if (!canResizeSection(first, last)) return this
        return apply(ResizeSection(section, first, last))
    }

    /** Renames the Section under the cursor and makes it span Measures [first] to [last] (0-based), as one edit. */
    fun changeSection(name: String, first: Int, last: Int): EditSession {
        val section = sectionIndexAtCursor ?: return this
        if (name.isBlank() || !canResizeSection(first, last)) return this
        return apply(ChangeSection(section, name.trim(), first, last))
    }

    /** Removes the Section under the cursor, keeping its Measures. */
    fun removeSection(): EditSession {
        val section = sectionIndexAtCursor ?: return this
        return apply(RemoveSection(section))
    }

    /** Copies the Measures of the Section under the cursor, in every Track, to paste with [pasteMeasures]. */
    fun copySection(): EditSession {
        val section = sectionAtCursor ?: return this
        return copy(copied = song.copyMeasures(section.first, section.last))
    }

    /** Whether there are copied Measures to paste. */
    val canPaste: Boolean get() = copied != null && copied.tracks.size == song.tracks.size

    /** Pastes the copied Measures before the cursor's Measure, in every Track; the cursor moves to the first of them. */
    fun pasteMeasures(): EditSession {
        val measures = copied?.takeIf { canPaste } ?: return this
        return apply(PasteMeasures(cursor.measure, measures)).moveTo(cursor.copy(beat = 0))
    }

    private fun chooseDuration(chosen: Duration): EditSession =
        if (elementAtCursor != null) {
            apply(ChangeDuration(track, cursor.measure, cursor.beat, chosen)).copy(duration = chosen)
        } else {
            copy(duration = chosen)
        }

    fun moveToNextBeat(): EditSession = when {
        cursor.beat < lastBeat(cursor.measure) -> moveTo(cursor.copy(beat = cursor.beat + 1))
        cursor.measure < song.measures.lastIndex -> moveTo(cursor.copy(measure = cursor.measure + 1, beat = 0))
        else -> moveTo(cursor)
    }

    fun moveToPreviousBeat(): EditSession = when {
        cursor.beat > 0 -> moveTo(cursor.copy(beat = cursor.beat - 1))
        cursor.measure > 0 -> moveTo(cursor.copy(measure = cursor.measure - 1, beat = lastBeat(cursor.measure - 1)))
        else -> moveTo(cursor)
    }

    fun moveToNextMeasure(): EditSession = moveToMeasure(cursor.measure + 1)

    fun moveToPreviousMeasure(): EditSession = moveToMeasure(cursor.measure - 1)

    /** To the first beat of Measure [measure] (0-based), or the nearest Measure there is. */
    fun moveToMeasure(measure: Int): EditSession =
        moveTo(cursor.copy(measure = measure.coerceIn(song.measures.indices), beat = 0))

    /** Towards string 1, the top line of the Tablature. */
    fun moveToStringAbove(): EditSession = moveTo(cursor.copy(string = (cursor.string - 1).coerceAtLeast(1)))

    fun moveToStringBelow(): EditSession =
        moveTo(cursor.copy(string = (cursor.string + 1).coerceAtMost(song.tracks[track].instrument.stringCount)))

    /** The Note, Chord or Rest under the cursor, or null on the free beat after the last one. */
    val elementAtCursor: VoiceElement?
        get() = elementsOf(cursor.measure).getOrNull(cursor.beat)

    private fun moveTo(target: Cursor): EditSession {
        val element = elementsOf(target.measure).getOrNull(target.beat)
        return copy(cursor = target, duration = element?.duration ?: duration)
    }

    private fun elementsOf(measure: Int) = song.tracks[track].voice.measures[measure]

    /** The last beat the cursor can reach: one past the last element, unless the Measure has no room left. */
    private fun lastBeat(measure: Int): Int {
        val size = elementsOf(measure).size
        val hasRoom = song.measureFill(track, measure) in setOf(MeasureFill.UNWRITTEN, MeasureFill.INCOMPLETE)
        return if (hasRoom) size else size - 1
    }

    /** Applies [edit]; [moveTracks] tells how it moves the Song's Tracks, if it adds or removes any. */
    private fun apply(edit: Edit, isNewEdit: Boolean = true, moveTracks: (List<Int?>) -> List<Int?> = { it }): EditSession {
        val edited = edit.applyTo(song)
        if (edited == song) return copy()
        return copy(
            song = edited,
            trackOrigins = moveTracks(trackOrigins),
            undoStack = if (isNewEdit) undoStack + snapshot() else undoStack,
            redoStack = emptyList(),
            restructure = restructure.then(edit.restructure(song)),
        )
    }

    private fun snapshot() = Snapshot(song, track, cursor, trackOrigins, restructure)

    private fun copy(
        song: Song = this.song,
        track: Int = this.track,
        trackOrigins: List<Int?> = this.trackOrigins,
        cursor: Cursor = this.cursor,
        duration: Duration = this.duration,
        pendingDigit: Int? = null,
        undoStack: List<Snapshot> = this.undoStack,
        redoStack: List<Snapshot> = this.redoStack,
        copied: CopiedMeasures? = this.copied,
        restructure: Restructure = this.restructure,
    ) = EditSession(song, cursor, duration, track, trackOrigins, pendingDigit, undoStack, redoStack, copied, restructure)

    companion object {
        /** Starts editing the Track at index [track] of [song], with the cursor on its first beat and top string. */
        fun start(song: Song, track: Int = 0): EditSession {
            require(track in song.tracks.indices) { "No Track $track" }
            require(song.measures.isNotEmpty()) { "A Song needs a Measure to edit" }
            return EditSession(song, Cursor(measure = 0, beat = 0, string = 1), Duration.QUARTER, track)
        }
    }
}

/** The highest fret the number pad accepts. */
const val HIGHEST_FRET = 24
