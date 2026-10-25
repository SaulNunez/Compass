package com.saulnunez.compass.notation

import alphaTab.midi.MidiTickLookup
import alphaTab.model.Score
import alphaTab.synth.PlaybackRange

/**
 * The range of MIDI ticks alphaTab's player loops to repeat [measures] (0-based) of [score] (the
 * Score this lookup was generated for): from the start of the first Measure to the end of the last,
 * so a repeat starts the moment the last Measure ends. Null when the Score doesn't have them.
 */
internal fun MidiTickLookup.playbackRangeOf(score: Score, measures: IntRange): PlaybackRange? {
    val masterBars = score.masterBars
    if (measures.isEmpty() || measures.first < 0 || measures.last >= masterBars.length.toInt()) return null
    return PlaybackRange().apply {
        startTick = getMasterBarStart(masterBars[measures.first])
        endTick = getMasterBar(masterBars[measures.last]).end
    }
}
