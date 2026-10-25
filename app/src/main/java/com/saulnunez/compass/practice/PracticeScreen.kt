package com.saulnunez.compass.practice

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
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
import com.saulnunez.compass.ui.AdaptivePanes
import com.saulnunez.compass.ui.BackToLibraryButton
import com.saulnunez.compass.ui.Dropdown
import com.saulnunez.compass.ui.theme.CompassTheme
import kotlin.math.roundToInt

/**
 * Practice mode: reads and plays a Song without any way to change it. The only way to change the
 * Song is the explicit [onEdit] action, which switches to Edit mode. Changes to the [settings]
 * (the Song's Practice settings) go to [onSettingsChange]; [onPlaybackStarted] is called each
 * time Playback starts. The screen stays on. Every Track plays unless muted or left out of a solo;
 * the views show the Track being read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    song: Song,
    settings: PracticeSettings,
    onSettingsChange: (PracticeSettings) -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onSendSongFile: () -> Unit,
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
    val silencedTracks = song.tracks.indices.filterNot(settings::isHeard).toSet()
    SideEffect { player.silencedTracks = silencedTracks }

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
                    TextButton(onClick = onSendSongFile) { Text(stringResource(R.string.send_song_file)) }
                    TextButton(onClick = onEdit) { Text(stringResource(R.string.enter_edit_mode)) }
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        // Settings saved for a Track that is gone fall back to the first.
        val readTrack = settings.readTrack.takeIf { it in song.tracks.indices } ?: 0
        // Beside the notation on a wide, landscape screen; above and below it otherwise.
        AdaptivePanes(
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding).fillMaxSize(),
            header = {
                ShownViewsChoice(
                    selected = settings.shownViews,
                    onSelect = { onSettingsChange(settings.copy(shownViews = it)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            },
            panel = {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    if (song.tracks.size > 1) {
                        Dropdown(
                            label = stringResource(R.string.field_read_track),
                            options = song.tracks.indices.toList(),
                            selected = readTrack,
                            optionLabel = { song.tracks[it].name },
                            onSelect = { onSettingsChange(settings.copy(readTrack = it)) },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        )
                    }
                    PlaybackControls(
                        song = song,
                        player = player,
                        settings = settings,
                        onSettingsChange = onSettingsChange,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    TrackMix(
                        song = song,
                        settings = settings,
                        onSettingsChange = onSettingsChange,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
            },
        ) {
            NotationView(
                song = song,
                shownViews = settings.shownViews,
                modifier = Modifier.fillMaxSize(),
                player = player,
                controls = settings.playerControls(),
                track = readTrack,
            )
        }
    }
}

/** Play/stop, the tempo percentage, the click, the count-in and the Loop. */
@OptIn(ExperimentalLayoutApi::class)
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
        // Wraps onto a second line when the controls sit in a narrow column beside the notation.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), itemVerticalAlignment = Alignment.CenterVertically) {
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
            LoopChoice(song, settings.loop, onLoopChange = { onSettingsChange(settings.copy(loop = it)) })
        }
    }
}

private const val TEMPO_STEP = 5

/**
 * Chooses Tablature, Standard notation or both. Each button is as wide as its label needs (rather
 * than a third of the row each), so the longest label stays on one line and the buttons stay joined.
 */
@Composable
private fun ShownViewsChoice(selected: ShownViews, onSelect: (ShownViews) -> Unit, modifier: Modifier = Modifier) {
    val options = ShownViews.entries
    val labels = options.map { stringResource(it.label()) }
    val labelStyle = MaterialTheme.typography.labelLarge
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelWidths = remember(labels, labelStyle, density) {
        labels.map { measurer.measure(it, labelStyle, maxLines = 1).size.width.toFloat() }
    }
    val padding = with(density) { SEGMENT_PADDING.toPx() }
    SingleChoiceSegmentedButtonRow(modifier.widthIn(max = SHOWN_VIEWS_MAX_WIDTH)) {
        options.forEachIndexed { index, views ->
            SegmentedButton(
                selected = views == selected,
                onClick = { onSelect(views) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                // Shares the row out by label width; this overrides the button's own equal weight.
                modifier = Modifier.weight(labelWidths[index] + padding),
                // The filled background already marks the chosen view; a check mark would need room the labels don't have.
                icon = {},
            ) {
                Text(labels[index], maxLines = 1, overflow = TextOverflow.Ellipsis, style = labelStyle)
            }
        }
    }
}

/** Room each segment needs besides its label: the button's own padding either side and its border. */
private val SEGMENT_PADDING = 24.dp + 2.dp

/** On a tablet the choice doesn't need the whole width. */
private val SHOWN_VIEWS_MAX_WIDTH = 560.dp

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
        PracticeScreen(demoSong, PracticeSettings(), onSettingsChange = {}, onBack = {}, onEdit = {}, onSendSongFile = {})
    }
}
