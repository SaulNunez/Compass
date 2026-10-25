package com.saulnunez.compass.notation

import alphaTab.AlphaTabApiBase
import alphaTab.synth.PlayerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.saulnunez.compass.domain.Cursor

/**
 * Starts and stops Playback of the Song a [NotationView] shows, and tells Compose whether it is
 * playing. If [playsOnTap], tapping a Measure in the view starts Playback there.
 */
@Stable
class NotationPlayer internal constructor(private val playsOnTap: Boolean) {

    /** Whether the Sounds and the Song are loaded, so Playback can start. */
    var isReady by mutableStateOf(false)
        private set

    var isPlaying by mutableStateOf(false)
        private set

    /** Called each time Playback starts. */
    internal var onStarted: () -> Unit = {}

    private var api: AlphaTabApiBase<*>? = null
    private var unsubscribe: List<() -> Unit> = emptyList()

    /** Where Stop goes back to: the start of the Song, or the Measure Playback was last started from. */
    private var startTick = 0.0

    fun play() {
        if (api?.play() == true) onStarted()
    }

    /** Starts Playback at the Edit mode [cursor] in the Track at index [track]; Stop goes back there. */
    fun playFrom(cursor: Cursor, track: Int) {
        val api = api ?: return
        val score = api.score ?: return
        val tickCache = api.tickCache ?: return
        startTick = tickCache.startTickOf(score, track, cursor)
        api.tickPosition = startTick
        if (!isPlaying) play()
    }

    /** Stops Playback and goes back to where it started. */
    fun stop() {
        val api = api ?: return
        api.pause()
        api.tickPosition = startTick
    }

    /** Starts Playback at the beginning of Measure [measure] (0-based), or jumps there if already playing. */
    fun playFrom(measure: Int) {
        val api = api ?: return
        val masterBar = api.score?.masterBars?.takeIf { measure < it.length.toInt() }?.get(measure) ?: return
        val tickCache = api.tickCache ?: return
        startTick = tickCache.getMasterBarStart(masterBar)
        api.tickPosition = startTick
        if (!isPlaying) play()
    }

    internal fun attach(api: AlphaTabApiBase<*>) {
        detach()
        this.api = api
        startTick = 0.0
        isReady = api.isReadyForPlayback
        isPlaying = api.playerState == PlayerState.Playing
        unsubscribe = listOfNotNull(
            // Fires again whenever the MIDI is reloaded; the position is back at the start then.
            api.playerReady.on {
                isReady = true
                startTick = 0.0
            },
            api.playerStateChanged.on { isPlaying = it.state == PlayerState.Playing },
            // Measures are shared by every Track, so a bar's index is its Measure's index.
            if (playsOnTap) api.beatMouseDown.on { beat -> playFrom(beat.voice.bar.index.toInt()) } else null,
        )
    }

    internal fun detach() {
        unsubscribe.forEach { it() }
        unsubscribe = emptyList()
        api = null
        isReady = false
        isPlaying = false
    }
}

/** A [NotationPlayer]; with [playsOnTap], tapping a Measure in the view starts Playback there. */
@Composable
fun rememberNotationPlayer(playsOnTap: Boolean = true): NotationPlayer = remember(playsOnTap) { NotationPlayer(playsOnTap) }
