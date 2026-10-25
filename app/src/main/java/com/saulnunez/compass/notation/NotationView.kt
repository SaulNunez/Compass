package com.saulnunez.compass.notation

import alphaTab.AlphaTabView
import alphaTab.PlayerMode
import alphaTab.ScrollMode
import alphaTab.Settings
import alphaTab.model.Score
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import com.saulnunez.compass.domain.ShownViews
import com.saulnunez.compass.domain.Song

/** What the alphaTab view last drew, so recomposition only re-renders what changed. */
private data class Drawn(
    val score: Score,
    val track: Int,
    val views: ShownViews,
    val controls: PlayerControls?,
    val theme: NotationTheme,
)

/**
 * Everything the alphaTab view needs to match the app's light or dark theme: the notation itself,
 * the background behind it (alphaTab paints none of its own), and the Playback cursors.
 */
private data class NotationTheme(val colors: NotationColors, val background: Int, val barCursor: Int, val beatCursor: Int)

private fun ColorScheme.notationTheme() = NotationTheme(
    colors = NotationColors(ink = onSurface.toArgb(), lines = outline.toArgb(), accent = primary.toArgb()),
    background = surface.toArgb(),
    barCursor = primary.copy(alpha = BAR_CURSOR_ALPHA).toArgb(),
    beatCursor = primary.copy(alpha = BEAT_CURSOR_ALPHA).toArgb(),
)

/** How strongly the cursors show over the notation: the Measure faintly, the beat clearly. */
private const val BAR_CURSOR_ALPHA = 0.18f
private const val BEAT_CURSOR_ALPHA = 0.75f

/**
 * Draws the Track at index [track] of [song] in the [shownViews] with alphaTab, as wrapped lines
 * that scroll vertically; redraws whenever any of them changes.
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
    track: Int = 0,
) {
    // alphaTab's native view can't run in the Compose preview.
    if (LocalInspectionMode.current) {
        Box(modifier.background(MaterialTheme.colorScheme.surface))
        return
    }

    val playable = player != null
    val settings = remember(playable) {
        Settings().apply {
            // Even with a player, alphaTab's player starts only once the Song is first drawn (see below).
            this.player.playerMode = PlayerMode.Disabled
            if (playable) {
                this.player.enableCursor = true
                this.player.scrollMode = ScrollMode.Continuous
                // Compass handles taps itself (start at the tapped Measure); alphaTab's own handling
                // would also select ranges and clear Loops.
                this.player.enableUserInteraction = false
            }
        }
    }
    val score = remember(song) { song.toAlphaTabScore(settings) }
    // Follows the system's light or dark theme, which can change while the screen is open.
    val theme = MaterialTheme.colorScheme.notationTheme()
    AndroidView(
        modifier = modifier,
        factory = { context ->
            AlphaTabView(context, null).apply {
                // Listen before the player is created, so its first ready event isn't missed.
                player?.attach(api)
                if (player != null) startPlayerAfterFirstRender(settings)
                // The settings are applied once, by the first update, with the views and colours.
            }
        },
        onRelease = { player?.detach() },
        update = { view ->
            // update runs on every recomposition; only re-render what changed.
            val drawn = view.tag as Drawn?
            if (drawn?.views != shownViews || drawn.theme != theme) {
                settings.show(shownViews)
                settings.use(theme.colors)
                view.setBackgroundColor(theme.background)
                // Read again whenever the settings are re-applied, so the cursors change colour too.
                view.barCursorFillColor = theme.barCursor
                view.beatCursorFillColor = theme.beatCursor
                // Re-applying the settings re-renders whatever Score the view holds.
                view.settings = settings
            }
            if (drawn?.score !== score || drawn.track != track) {
                // Setting tracks (rather than calling renderScore) lets alphaTab wait until the
                // view is visible and measured; rendering earlier is skipped at width 0.
                view.tracks = listOf(score.tracks[track])
            }
            if (controls != null && drawn?.controls != controls) view.api.apply(controls)
            view.tag = Drawn(score, track, shownViews, controls, theme)
        },
    )
}

/**
 * Creates alphaTab's player (its synthesizer and audio output, which load the soundfont) once the
 * Song has first been drawn. Created with the view, its start-up (reading and copying the 1.3 MB
 * soundfont on the main thread, opening the audio output, and parsing the soundfont on a worker
 * thread) competed with the first render and held up the Song appearing.
 */
private fun AlphaTabView.startPlayerAfterFirstRender(settings: Settings) {
    var unsubscribe: () -> Unit = {}
    unsubscribe = api.postRenderFinished.on {
        if (api.score == null) return@on
        unsubscribe()
        settings.player.playerMode = PlayerMode.EnabledSynthesizer
        // Creates the player and loads the MIDI for the Score shown, without drawing it again.
        api.updateSettings()
    }
}
