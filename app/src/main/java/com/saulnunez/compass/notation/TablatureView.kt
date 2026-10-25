package com.saulnunez.compass.notation

import alphaTab.AlphaTabView
import alphaTab.PlayerMode
import alphaTab.Settings
import alphaTab.StaveProfile
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import com.saulnunez.compass.domain.Song

/** Draws the first Track of [song] as Tablature with alphaTab; redraws whenever [song] changes. */
@Composable
fun TablatureView(song: Song, modifier: Modifier = Modifier) {
    // alphaTab's native view can't run in the Compose preview.
    if (LocalInspectionMode.current) {
        Box(modifier)
        return
    }

    val settings = remember {
        Settings().apply {
            display.staveProfile = StaveProfile.Tab
            player.playerMode = PlayerMode.Disabled
        }
    }
    val score = remember(song) { song.toAlphaTabScore(settings) }
    AndroidView(
        modifier = modifier,
        factory = { context -> AlphaTabView(context, null).apply { this.settings = settings } },
        update = { view ->
            // update runs on every recomposition; only re-render when the Song changed.
            if (view.tag !== score) {
                view.tag = score
                // Setting tracks (rather than calling renderScore) lets alphaTab wait until the
                // view is visible and measured; rendering earlier is skipped at width 0.
                view.tracks = listOf(score.tracks[0])
            }
        },
    )
}
