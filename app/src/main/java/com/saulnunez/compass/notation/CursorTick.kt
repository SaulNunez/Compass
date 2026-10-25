package com.saulnunez.compass.notation

import alphaTab.midi.MidiTickLookup
import alphaTab.model.Score
import com.saulnunez.compass.domain.Cursor

/**
 * The MIDI tick at which Playback from the Edit mode [cursor] starts in [score] (the Score this
 * lookup was generated for): the start of the element under the cursor in the Track at index
 * [track], or, on the free beat after a Measure's last element, where that element ends.
 */
internal fun MidiTickLookup.startTickOf(score: Score, track: Int, cursor: Cursor): Double {
    val masterBars = score.masterBars
    if (cursor.measure !in 0 until masterBars.length.toInt()) return 0.0
    val beats = score.tracks[track].staves[0].bars[cursor.measure].voices[0].beats
    val count = beats.length.toInt()
    if (cursor.beat < count) return getBeatStart(beats[cursor.beat])
    // Past the last element; an unwritten Measure holds a single empty beat that starts it.
    val last = beats[count - 1]
    if (last.isEmpty) return getMasterBarStart(masterBars[cursor.measure])
    val range = getRelativeBeatPlaybackRange(last) ?: return getBeatStart(last)
    return getMasterBarStart(masterBars[cursor.measure]) + range.endTick
}
