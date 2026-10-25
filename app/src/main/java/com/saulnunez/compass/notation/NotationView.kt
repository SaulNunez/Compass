package com.saulnunez.compass.notation

import alphaTab.AlphaTabView
import alphaTab.PlayerMode
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

/** Draws the first Track of [song] as Tablature with alphaTab; redraws whenever [song] changes. */
@Composable
fun TablatureView(song: Song, modifier: Modifier = Modifier) {
    NotationView(song, ShownViews.TABLATURE, modifier)
}

/** What the alphaTab view last drew, so recomposition only re-renders what changed. */
private data class Drawn(val score: Score, val views: ShownViews)

/**
 * Draws the first Track of [song] in the [shownViews] with alphaTab, as wrapped lines that scroll
 * vertically; redraws whenever [song] or [shownViews] changes.
 */
@Composable
fun NotationView(song: Song, shownViews: ShownViews, modifier: Modifier = Modifier) {
    // alphaTab's native view can't run in the Compose preview.
    if (LocalInspectionMode.current) {
        Box(modifier)
        return
    }

    val settings = remember {
        Settings().apply {
            player.playerMode = PlayerMode.Disabled
        }
    }
    val score = remember(song) { song.toAlphaTabScore(settings) }
    AndroidView(
        modifier = modifier,
        factory = { context -> AlphaTabView(context, null).apply { this.settings = settings } },
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
            view.tag = Drawn(score, shownViews)
        },
    )
}
