package com.saulnunez.compass.practice

import alphaTab.AlphaTabView
import alphaTab.PlayerMode
import alphaTab.Settings
import alphaTab.model.Score
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.saulnunez.compass.R
import com.saulnunez.compass.domain.ShownViews
import com.saulnunez.compass.domain.Song
import com.saulnunez.compass.notation.show
import com.saulnunez.compass.notation.toAlphaTabScore
import com.saulnunez.compass.ui.BackToLibraryButton
import com.saulnunez.compass.ui.theme.CompassTheme

/**
 * Practice mode: reads a Song without any way to change it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(song: Song, onBack: () -> Unit, onShare: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(song.title)
                        Text(
                            if (song.artist.isBlank()) {
                                stringResource(R.string.practice_mode)
                            } else {
                                stringResource(R.string.practice_mode_subtitle, song.artist)
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                navigationIcon = { BackToLibraryButton(onBack) },
                actions = { TextButton(onClick = onShare) { Text(stringResource(R.string.song_share)) } },
            )
        },
    ) { innerPadding ->
        // In-session for now; it becomes part of the Song's Practice settings later (#6).
        var shownViews by rememberSaveable { mutableStateOf(ShownViews.TABLATURE) }
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            ShownViewsChoice(
                selected = shownViews,
                onSelect = { shownViews = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            NotationView(song, shownViews, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun ShownViewsChoice(selected: ShownViews, onSelect: (ShownViews) -> Unit, modifier: Modifier = Modifier) {
    val options = ShownViews.entries
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { index, views ->
            SegmentedButton(
                selected = views == selected,
                onClick = { onSelect(views) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(stringResource(views.label()))
            }
        }
    }
}

@StringRes
private fun ShownViews.label(): Int = when (this) {
    ShownViews.TABLATURE -> R.string.view_tablature
    ShownViews.STANDARD_NOTATION -> R.string.view_standard_notation
    ShownViews.BOTH -> R.string.view_both
}

/** What the alphaTab view last drew, so recomposition only re-renders what changed. */
private data class Drawn(val score: Score, val views: ShownViews)

/** Draws the Song's first Track in the [shownViews], as wrapped lines that scroll vertically. */
@Composable
private fun NotationView(song: Song, shownViews: ShownViews, modifier: Modifier = Modifier) {
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

@Preview(showBackground = true)
@Composable
private fun PracticeScreenPreview() {
    CompassTheme {
        PracticeScreen(demoSong, onBack = {}, onShare = {})
    }
}
