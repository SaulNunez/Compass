package com.saulnunez.compass.notation

import alphaTab.Settings
import alphaTab.collections.DoubleList
import alphaTab.model.Automation
import alphaTab.model.Bar
import alphaTab.model.Clef
import alphaTab.model.Beat
import alphaTab.model.BendPoint
import alphaTab.model.BendType
import alphaTab.model.KeySignatureType
import alphaTab.model.MasterBar
import alphaTab.model.Score
import alphaTab.model.SlideOutType
import alphaTab.model.Staff
import alphaTab.model.VibratoType
import alphaTab.model.Voice
import com.saulnunez.compass.domain.InstrumentKind
import com.saulnunez.compass.domain.KeySignature
import com.saulnunez.compass.domain.Note
import com.saulnunez.compass.domain.NoteValue
import com.saulnunez.compass.domain.Technique
import com.saulnunez.compass.domain.notes
import com.saulnunez.compass.domain.Song
import com.saulnunez.compass.domain.Track
import alphaTab.model.Duration as AlphaTabDuration
import alphaTab.model.KeySignature as AlphaTabKeySignature
import alphaTab.model.Note as AlphaTabNote
import alphaTab.model.Track as AlphaTabTrack
import alphaTab.model.Tuning as AlphaTabTuning

/**
 * Translates a Compass Song into alphaTab's Score so alphaTab can draw and play it (ADR-0001).
 * The Song stays the source of truth; the Score is rebuilt whenever it is needed.
 * [settings] must be the same Settings the rendering view uses.
 */
fun Song.toAlphaTabScore(settings: Settings): Score {
    val score = Score()
    score.title = title
    score.artist = artist

    var previousTempo: Int? = null
    measures.forEach { measure ->
        val masterBar = MasterBar()
        masterBar.timeSignatureNumerator = measure.timeSignature.beats.toDouble()
        masterBar.timeSignatureDenominator = measure.timeSignature.beatUnit.toDouble()
        val bpm = measure.tempo.beatsPerMinute
        if (bpm != previousTempo) {
            masterBar.tempoAutomations.push(
                Automation.buildTempoAutomation(isLinear = false, ratioPosition = 0.0, value = bpm.toDouble(), reference = QUARTER_NOTE_REFERENCE),
            )
            previousTempo = bpm
        }
        score.addMasterBar(masterBar)
    }

    tracks.forEach { track ->
        val alphaTabTrack = track.toAlphaTabTrack()
        score.addTrack(alphaTabTrack)
        alphaTabBarsFor(track).forEach(alphaTabTrack.staves[0]::addBar)
    }

    // Compass has no dynamics; without this alphaTab draws its default forte under the first Note.
    score.stylesheet.hideDynamics = true
    score.finish(settings)
    return score
}

private fun Song.alphaTabBarsFor(track: Track): List<Bar> = measures.mapIndexed { index, measure ->
    Bar().apply {
        clef = when (track.instrument.kind) {
            InstrumentKind.GUITAR -> Clef.G2
            InstrumentKind.BASS -> Clef.F4
        }
        keySignature = measure.keySignature.toAlphaTab()
        keySignatureType = if (measure.keySignature.isMinor) KeySignatureType.Minor else KeySignatureType.Major
        addVoice(track.alphaTabVoiceFor(index))
    }
}

private fun Track.toAlphaTabTrack(): AlphaTabTrack {
    val alphaTabTrack = AlphaTabTrack()
    alphaTabTrack.name = name

    val staff = Staff()
    // Both list strings from the top tablature line (Compass string 1) downwards.
    staff.stringTuning = AlphaTabTuning("", DoubleList(*instrument.tuning.openStrings.map { it.midi.toDouble() }.toDoubleArray()))
    // Guitar and bass are written an octave above how they sound, so Standard notation shows
    // them that way (as alphaTab's own file importers do).
    staff.displayTranspositionPitch = WRITTEN_OCTAVE_UP
    alphaTabTrack.addStaff(staff)
    return alphaTabTrack
}

private fun Track.alphaTabVoiceFor(measureIndex: Int): Voice {
    val alphaTabVoice = Voice()
    val elements = voice.measures[measureIndex]
    if (elements.isEmpty()) {
        alphaTabVoice.addBeat(Beat().apply { isEmpty = true })
    }
    elements.forEach { element ->
        val beat = Beat()
        beat.duration = element.duration.value.toAlphaTab()
        if (element.duration.isDotted) beat.dots = 1.0
        if (element.duration.isTriplet) {
            // Three in the time of two.
            beat.tupletNumerator = 3.0
            beat.tupletDenominator = 2.0
        }
        // A Chord is one beat with a Note per string; a Rest is a beat with none.
        element.notes.forEach { beat.addNote(it.toAlphaTab(instrument.stringCount)) }
        // alphaTab marks slap and pop on the beat rather than on each Note.
        beat.slap = element.notes.any { Technique.SLAP in it.techniques }
        beat.pop = element.notes.any { Technique.POP in it.techniques }
        alphaTabVoice.addBeat(beat)
    }
    return alphaTabVoice
}

private fun Note.toAlphaTab(stringCount: Int): AlphaTabNote = AlphaTabNote().also { note ->
    // alphaTab counts strings from the lowest; Compass counts from the highest.
    note.string = (stringCount - string + 1).toDouble()
    note.fret = fret.toDouble()
    // alphaTab finds the Note it is tied to (the one before it on the same string) in Score.finish.
    note.isTieDestination = isTied
    techniques.forEach { technique ->
        when (technique) {
            // The Note it leads into (the next one on the string) is found in Score.finish.
            Technique.HAMMER_ON_PULL_OFF -> note.isHammerPullOrigin = true
            Technique.SLIDE -> note.slideOutType = SlideOutType.Legato
            Technique.BEND -> note.bend(BendPoint(0.0, 0.0), BendPoint(BEND_PEAK, WHOLE_STEP), BendPoint(BEND_END, WHOLE_STEP))
            Technique.BEND_AND_RELEASE -> note.bend(
                BendPoint(0.0, 0.0), BendPoint(BEND_PEAK, WHOLE_STEP), BendPoint(RELEASE_START, WHOLE_STEP), BendPoint(BEND_END, 0.0),
            )
            Technique.VIBRATO -> note.vibrato = VibratoType.Slight
            Technique.PALM_MUTE -> note.isPalmMute = true
            Technique.DEAD_NOTE -> note.isDead = true
            Technique.SLAP, Technique.POP -> Unit
        }
    }
}

/** Score.finish reads Custom bend points as a bend, bend and release, etc. by their shape. */
private fun AlphaTabNote.bend(vararg points: BendPoint) {
    bendType = BendType.Custom
    points.forEach(::addBendPoint)
}

private fun NoteValue.toAlphaTab(): AlphaTabDuration = when (this) {
    NoteValue.WHOLE -> AlphaTabDuration.Whole
    NoteValue.HALF -> AlphaTabDuration.Half
    NoteValue.QUARTER -> AlphaTabDuration.Quarter
    NoteValue.EIGHTH -> AlphaTabDuration.Eighth
    NoteValue.SIXTEENTH -> AlphaTabDuration.Sixteenth
    NoteValue.THIRTY_SECOND -> AlphaTabDuration.ThirtySecond
}

private fun KeySignature.toAlphaTab(): AlphaTabKeySignature = AlphaTabKeySignature.fromValue(fifths.toDouble())

/** A bend's height in alphaTab's quarter tones. */
private const val WHOLE_STEP = 4.0

/** Positions along a bent Note, from 0 (its start) to 60 (its end). */
private const val BEND_PEAK = 15.0
private const val RELEASE_START = 30.0
private const val BEND_END = 60.0

/** alphaTab's tempo reference code for "beats are quarter notes". */
private const val QUARTER_NOTE_REFERENCE = 2.0

/** alphaTab's display transposition (in semitones) that writes Notes an octave above their pitch. */
private const val WRITTEN_OCTAVE_UP = -12.0
