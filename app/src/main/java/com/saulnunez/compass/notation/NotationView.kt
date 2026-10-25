package com.saulnunez.compass.notation

import alphaTab.AlphaTabView
import alphaTab.PlayerMode
import alphaTab.ScrollMode
import alphaTab.Settings
import alphaTab.model.Score
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import com.saulnunez.compass.domain.ShownViews
import com.saulnunez.compass.domain.Song

/** What the alphaTab view last drew, so recomposition only re-renders what changed. */
private data class Drawn(val score: Score, val views: ShownViews, val controls: PlayerControls?)

/**
 * Draws the first Track of [song] in the [shownViews] with alphaTab, as wrapped lines that scroll
 * vertically; redraws whenever [song] or [shownViews] changes.
 *
 * With a [player], the view also plays the Song (every Track) with [controls] applied: a cursor
 * follows Playback, the view scrolls along, and tapping a Measure starts Playback there.
 * Without one, it only draws.
 */
@Composable
fun NotationView(
    song: Song,
    shownViews: ShownViews,
    modifier: Modifier = Modifier,
    player: NotationPlayer? = null,
    controls: PlayerControls? = null,
) {
    // alphaTab's native view can't run in the Compose preview.
    if (LocalInspectionMode.current) {
        Box(modifier)
        return
    }

    val playable = player != null
    val settings = remember(playable) {
        Settings().apply {
            if (playable) {
                // The synthesizer loads alphaTab's built-in soundfont by itself once the player is ready.
                this.player.playerMode = PlayerMode.EnabledSynthesizer
                this.player.enableCursor = true
                this.player.scrollMode = ScrollMode.Continuous
                // Compass handles taps itself (start at the tapped Measure); alphaTab's own handling
                // would also select ranges and clear Loops.
                this.player.enableUserInteraction = false
            } else {
                this.player.playerMode = PlayerMode.Disabled
            }
        }
    }
    val score = remember(song) { song.toAlphaTabScore(settings) }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            AlphaTabView(context, null).apply {
                // Listen before the settings create the player, so its first ready event isn't missed.
                player?.attach(api)
                this.settings = settings
            }
        },
        onRelease = { player?.detach() },
        update = { view ->
            // update runs on every recomposition; only re-render what changed.
            val drawn = view.tag as Drawn?
            if (drawn?.views != shownViews) {
                settings.show(shownViews)
                // Re-applying the settings re-renders whatever Score the view holds.
                view.settings = settings
            }
            if (drawn?.score !== score) {
                // Setting tracks (rather than calling renderScore) lets alphaTab wait until the
                // view is visible and measured; rendering earlier is skipped at width 0.
                view.tracks = listOf(score.tracks[0])
            }
            if (controls != null && drawn?.controls != controls) view.api.apply(controls)
            view.tag = Drawn(score, shownViews, controls)
        },
    )
}
