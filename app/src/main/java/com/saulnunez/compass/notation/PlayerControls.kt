package com.saulnunez.compass.notation

import alphaTab.AlphaTabApiBase
import com.saulnunez.compass.domain.PracticeSettings

/**
 * The alphaTab player values that make its Playback match the playback timeline for some Practice
 * settings. alphaTab's playback speed scales every Tempo in the Song; its count-in plays one Measure
 * of clicks each time Playback starts.
 */
data class PlayerControls(val playbackSpeed: Double, val metronomeVolume: Double, val countInVolume: Double)

fun PracticeSettings.playerControls() = PlayerControls(
    playbackSpeed = tempoPercentage.percent / 100.0,
    metronomeVolume = if (click) CLICK_VOLUME else 0.0,
    countInVolume = if (countIn) CLICK_VOLUME else 0.0,
)

/** alphaTab keeps these and re-applies them whenever its player is (re)created. */
internal fun AlphaTabApiBase<*>.apply(controls: PlayerControls) {
    playbackSpeed = controls.playbackSpeed
    metronomeVolume = controls.metronomeVolume
    countInVolume = controls.countInVolume
}

private const val CLICK_VOLUME = 1.0
