package com.saulnunez.compass.notation

import alphaTab.Settings
import alphaTab.collections.DoubleList
import alphaTab.model.Automation
import alphaTab.model.Bar
import alphaTab.model.Clef
import alphaTab.model.Beat
import alphaTab.model.KeySignatureType
import alphaTab.model.MasterBar
import alphaTab.model.Score
import alphaTab.model.Staff
import alphaTab.model.Voice
import com.saulnunez.compass.domain.InstrumentKind
import com.saulnunez.compass.domain.KeySignature
import com.saulnunez.compass.domain.Note
import com.saulnunez.compass.domain.NoteValue
import com.saulnunez.compass.domain.Rest
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

    tracks.forEachIndexed { index, track ->
        val alphaTabTrack = track.toAlphaTabTrack(index)
        score.addTrack(alphaTabTrack)
        alphaTabBarsFor(track).forEach(alphaTabTrack.staves[0]::addBar)
    }

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

private fun Track.toAlphaTabTrack(index: Int): AlphaTabTrack {
    val alphaTabTrack = AlphaTabTrack()
    alphaTabTrack.name = name
    alphaTabTrack.playbackInfo.apply {
        // Set before score.finish, which carries the program into the Track's first beat.
        program = instrument.kind.generalMidiProgram().toDouble()
        // Every Track needs channels of its own so its Sound, mute/solo and bends don't leak into other
        // Tracks; alphaTab gives each Track two (the second carries bends).
        primaryChannel = PLAYBACK_CHANNELS[(index * 2) % PLAYBACK_CHANNELS.size].toDouble()
        secondaryChannel = PLAYBACK_CHANNELS[(index * 2 + 1) % PLAYBACK_CHANNELS.size].toDouble()
    }

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
        when (element) {
            is Note -> beat.addNote(
                AlphaTabNote().apply {
                    // alphaTab counts strings from the lowest; Compass counts from the highest.
                    string = (instrument.stringCount - element.string + 1).toDouble()
                    fret = element.fret.toDouble()
                },
            )
            is Rest -> Unit
        }
        alphaTabVoice.addBeat(beat)
    }
    return alphaTabVoice
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

/**
 * The General MIDI Sound a Track plays with until Tracks get a choice of Sound (#9):
 * clean electric guitar and fingered bass.
 */
private fun InstrumentKind.generalMidiProgram(): Int = when (this) {
    InstrumentKind.GUITAR -> 27
    InstrumentKind.BASS -> 33
}

/**
 * MIDI channels for Tracks, two per Track. Channel 9 is percussion, so it is skipped; that leaves
 * room for 7 Tracks before channels are shared.
 */
private val PLAYBACK_CHANNELS = (0..15).filter { it != 9 }.take(14)

/** alphaTab's tempo reference code for "beats are quarter notes". */
private const val QUARTER_NOTE_REFERENCE = 2.0

/** alphaTab's display transposition (in semitones) that writes Notes an octave above their pitch. */
private const val WRITTEN_OCTAVE_UP = -12.0
