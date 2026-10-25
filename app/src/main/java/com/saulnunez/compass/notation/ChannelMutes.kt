package com.saulnunez.compass.notation

import alphaTab.model.Score

/**
 * Whether alphaTab should mute each MIDI channel so Playback of [this] Score leaves out every Track
 * that [isHeard] (by Track index) says is silent: the mute and solo of the Practice settings.
 *
 * alphaTab mutes channels, not Tracks, and Compass gives Tracks their channels by position, so a
 * Track's channels change when a Track before it is removed. Every channel is listed, unmuted unless
 * a silent Track plays on it, so no mute is left behind on a channel that now belongs to another
 * Track. Solo is done with mutes too (rather than alphaTab's own solo), as a solo left on an unused
 * channel would silence everything. A channel shared by Tracks (past 7 Tracks) stays unmuted while
 * any of them is heard.
 */
internal fun Score.channelMutes(isHeard: (track: Int) -> Boolean): Map<Int, Boolean> {
    val heardOn = mutableMapOf<Int, Boolean>()
    for (index in 0 until tracks.length.toInt()) {
        val info = tracks[index].playbackInfo
        for (channel in listOf(info.primaryChannel.toInt(), info.secondaryChannel.toInt())) {
            heardOn[channel] = heardOn[channel] == true || isHeard(index)
        }
    }
    return (0 until MIDI_CHANNELS).associateWith { heardOn[it] == false }
}

private const val MIDI_CHANNELS = 16
