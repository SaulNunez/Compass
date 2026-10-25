package com.saulnunez.compass.practice

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.saulnunez.compass.R
import com.saulnunez.compass.domain.PracticeSettings
import com.saulnunez.compass.domain.ShownViews
import com.saulnunez.compass.domain.Song
import com.saulnunez.compass.domain.TempoPercentage
import com.saulnunez.compass.domain.measuresIn
import com.saulnunez.compass.notation.NotationPlayer
import com.saulnunez.compass.notation.NotationView
import com.saulnunez.compass.notation.playerControls
import com.saulnunez.compass.notation.rememberNotationPlayer
import com.saulnunez.compass.ui.BackToLibraryButton
import com.saulnunez.compass.ui.theme.CompassTheme
import kotlin.math.roundToInt

/**
 * Practice mode: reads and plays a Song without any way to change it. The only way to change the
 * Song is the explicit [onEdit] action, which switches to Edit mode. Changes to the [settings]
 * (the Song's Practice settings) go to [onSettingsChange]; [onPlaybackStarted] is called each
 * time Playback starts. The screen stays on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    song: Song,
    settings: PracticeSettings,
    onSettingsChange: (PracticeSettings) -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
    onPlaybackStarted: () -> Unit = {},
) {
    val view = LocalView.current
    DisposableEffect(view) {
        // Hands are on the instrument, so the screen must not lock while practicing.
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    val player = rememberNotationPlayer()
    val currentOnPlaybackStarted by rememberUpdatedState(onPlaybackStarted)
    SideEffect { player.onStarted = { currentOnPlaybackStarted() } }
    // A Loop the Song no longer has Measures for plays nothing different.
    val loopMeasures = settings.loop?.measuresIn(song)
    SideEffect { player.loop = loopMeasures }

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
                actions = {
                    TextButton(onClick = onShare) { Text(stringResource(R.string.song_share)) }
                    TextButton(onClick = onEdit) { Text(stringResource(R.string.enter_edit_mode)) }
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            ShownViewsChoice(
                selected = settings.shownViews,
                onSelect = { onSettingsChange(settings.copy(shownViews = it)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            NotationView(
                song = song,
                shownViews = settings.shownViews,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                player = player,
                controls = settings.playerControls(),
            )
            PlaybackControls(
                song = song,
                player = player,
                settings = settings,
                onSettingsChange = onSettingsChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** Play/stop, the tempo percentage, the click, the count-in and the Loop. */
@Composable
private fun PlaybackControls(
    song: Song,
    player: NotationPlayer,
    settings: PracticeSettings,
    onSettingsChange: (PracticeSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        // Follows the slider while it is dragged; saved once the drag ends.
        var percent by remember(settings.tempoPercentage) { mutableFloatStateOf(settings.tempoPercentage.percent.toFloat()) }
        Text(stringResource(R.string.tempo_percentage, percent.roundToInt()), style = MaterialTheme.typography.labelLarge)
        val range = TempoPercentage.RANGE
        Slider(
            value = percent,
            onValueChange = { percent = it },
            onValueChangeFinished = { onSettingsChange(settings.copy(tempoPercentage = TempoPercentage(percent.roundToInt()))) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            // Steps of 5%.
            steps = (range.last - range.first) / TEMPO_STEP - 1,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { if (player.isPlaying) player.stop() else player.play() },
                enabled = player.isReady,
            ) {
                Text(stringResource(if (player.isPlaying) R.string.playback_stop else R.string.playback_play))
            }
            FilterChip(
                selected = settings.click,
                onClick = { onSettingsChange(settings.copy(click = !settings.click)) },
                label = { Text(stringResource(R.string.playback_click)) },
            )
            FilterChip(
                selected = settings.countIn,
                onClick = { onSettingsChange(settings.copy(countIn = !settings.countIn)) },
                label = { Text(stringResource(R.string.playback_count_in)) },
            )
        }
        LoopChoice(song, settings.loop, onLoopChange = { onSettingsChange(settings.copy(loop = it)) })
    }
}

private const val TEMPO_STEP = 5

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

@Preview(showBackground = true)
@Composable
private fun PracticeScreenPreview() {
    CompassTheme {
        PracticeScreen(demoSong, PracticeSettings(), onSettingsChange = {}, onBack = {}, onEdit = {}, onShare = {})
    }
}
