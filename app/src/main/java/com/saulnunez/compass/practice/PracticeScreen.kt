package com.saulnunez.compass.practice

import alphaTab.AlphaTabView
import alphaTab.PlayerMode
import alphaTab.Settings
import alphaTab.StaveProfile
import alphaTab.collections.DoubleList
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.saulnunez.compass.R
import com.saulnunez.compass.domain.Song
import com.saulnunez.compass.notation.toAlphaTabScore
import com.saulnunez.compass.ui.theme.CompassTheme

/**
 * Practice mode: reads a Song without any way to change it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(song: Song, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(song.title)
                        Text(
                            stringResource(R.string.practice_mode_subtitle, song.artist),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        TablatureView(song, Modifier.padding(innerPadding).fillMaxSize())
    }
}

@Composable
private fun TablatureView(song: Song, modifier: Modifier = Modifier) {
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
                view.api.renderScore(score, DoubleList(0.0))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun PracticeScreenPreview() {
    CompassTheme {
        PracticeScreen(demoSong)
    }
}
