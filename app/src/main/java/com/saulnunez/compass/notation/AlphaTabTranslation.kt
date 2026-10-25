package com.saulnunez.compass.notation

import alphaTab.Settings
import alphaTab.collections.DoubleList
import alphaTab.model.Automation
import alphaTab.model.Bar
import alphaTab.model.Beat
import alphaTab.model.KeySignatureType
import alphaTab.model.MasterBar
import alphaTab.model.Score
import alphaTab.model.Staff
import alphaTab.model.Voice
import com.saulnunez.compass.domain.Duration
import com.saulnunez.compass.domain.InstrumentKind
import com.saulnunez.compass.domain.KeySignature
import com.saulnunez.compass.domain.Note
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
 */
fun Song.toAlphaTabScore(settings: Settings = Settings()): Score {
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
        trackBars(track).forEach(alphaTabTrack.staves[0]::addBar)
    }

    score.finish(settings)
    return score
}

private fun Song.trackBars(track: Track): List<Bar> = measures.mapIndexed { index, measure ->
    Bar().apply {
        keySignature = measure.keySignature.toAlphaTab()
        keySignatureType = if (measure.keySignature.isMinor) KeySignatureType.Minor else KeySignatureType.Major
        addVoice(track.voiceForMeasure(index))
    }
}

private fun Track.toAlphaTabTrack(index: Int): AlphaTabTrack {
    val alphaTabTrack = AlphaTabTrack()
    alphaTabTrack.name = name
    alphaTabTrack.playbackInfo.program = when (instrument.kind) {
        InstrumentKind.GUITAR -> CLEAN_ELECTRIC_GUITAR_PROGRAM
        InstrumentKind.BASS -> FINGERED_BASS_PROGRAM
    }
    alphaTabTrack.playbackInfo.primaryChannel = (index * 2).toDouble()
    alphaTabTrack.playbackInfo.secondaryChannel = (index * 2 + 1).toDouble()

    val staff = Staff()
    // Both list strings from the top tablature line (Compass string 1) downwards.
    staff.stringTuning = AlphaTabTuning("", DoubleList(*instrument.tuning.openStrings.map { it.midi.toDouble() }.toDoubleArray()))
    staff.showTablature = true
    staff.showStandardNotation = true
    alphaTabTrack.addStaff(staff)
    return alphaTabTrack
}

private fun Track.voiceForMeasure(measureIndex: Int): Voice {
    val voice = Voice()
    val elements = this.voice.measures[measureIndex]
    if (elements.isEmpty()) {
        voice.addBeat(Beat().apply { isEmpty = true })
    }
    elements.forEach { element ->
        val beat = Beat()
        beat.duration = element.duration.toAlphaTab()
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
        voice.addBeat(beat)
    }
    return voice
}

private fun Duration.toAlphaTab(): AlphaTabDuration = when (this) {
    Duration.WHOLE -> AlphaTabDuration.Whole
    Duration.HALF -> AlphaTabDuration.Half
    Duration.QUARTER -> AlphaTabDuration.Quarter
    Duration.EIGHTH -> AlphaTabDuration.Eighth
    Duration.SIXTEENTH -> AlphaTabDuration.Sixteenth
    Duration.THIRTY_SECOND -> AlphaTabDuration.ThirtySecond
}

private fun KeySignature.toAlphaTab(): AlphaTabKeySignature = AlphaTabKeySignature.fromValue(fifths.toDouble())

/** alphaTab's tempo reference code for "beats are quarter notes". */
private const val QUARTER_NOTE_REFERENCE = 2.0

// General MIDI programs; Track Sounds will replace these.
private const val CLEAN_ELECTRIC_GUITAR_PROGRAM = 27.0
private const val FINGERED_BASS_PROGRAM = 33.0
