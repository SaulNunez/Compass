package com.saulnunez.compass.edit

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.saulnunez.compass.R
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.saulnunez.compass.domain.KeySignature
import com.saulnunez.compass.domain.Measure
import com.saulnunez.compass.domain.Pickup
import com.saulnunez.compass.domain.Song
import com.saulnunez.compass.domain.isPickup
import com.saulnunez.compass.domain.measureNumber
import com.saulnunez.compass.domain.measureNumbered
import com.saulnunez.compass.domain.Tempo
import com.saulnunez.compass.domain.TimeSignature
import com.saulnunez.compass.library.keySignatureLabel
import com.saulnunez.compass.library.label
import com.saulnunez.compass.library.measureLabel
import com.saulnunez.compass.ui.AdaptivePanes
import com.saulnunez.compass.ui.BEATS_RANGE
import com.saulnunez.compass.ui.BEAT_UNITS
import com.saulnunez.compass.ui.Dropdown
import com.saulnunez.compass.ui.IconAction
import com.saulnunez.compass.ui.IconAndLabel
import com.saulnunez.compass.ui.LabeledIconAction
import com.saulnunez.compass.ui.NumberField
import com.saulnunez.compass.ui.TEMPO_RANGE
import com.saulnunez.compass.domain.Duration
import com.saulnunez.compass.domain.EditSession
import com.saulnunez.compass.domain.MeasureFill
import com.saulnunez.compass.domain.Note
import com.saulnunez.compass.domain.NoteValue
import com.saulnunez.compass.domain.Rest
import com.saulnunez.compass.domain.Technique
import com.saulnunez.compass.domain.noteOn
import com.saulnunez.compass.domain.measureFill
import com.saulnunez.compass.domain.ShownViews
import com.saulnunez.compass.notation.NotationPlayer
import com.saulnunez.compass.notation.NotationView
import com.saulnunez.compass.notation.rememberNotationPlayer
import com.saulnunez.compass.practice.demoSong
import com.saulnunez.compass.ui.theme.CompassTheme

/**
 * Edit mode: adds, removes and changes a Song's Tracks and writes Notes, Chords and Rests, ties
 * and Techniques into the Tablature of the Track being edited, through [session]. Every action
 * hands the next session to [onSessionChange]; [onDone] switches back to Practice mode. Playback
 * here only plays the Song from the cursor and stops; the tempo percentage, click and count-in
 * are Practice mode's. The Song is shown in Practice mode's [shownViews] (always with the
 * Tablature being edited), so Standard notation follows every edit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    session: EditSession,
    shownViews: ShownViews,
    onSessionChange: (EditSession) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = session.song
    // Tapping the Tablature doesn't start Playback here; only the Play action does, from the cursor.
    val player = rememberNotationPlayer(playsOnTap = false)
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(song.title)
                        Text(stringResource(R.string.edit_mode), style = MaterialTheme.typography.bodySmall)
                    }
                },
                // A check mark alone could read as "apply", so leaving Edit mode keeps its label.
                navigationIcon = { LabeledIconAction(R.drawable.ic_check, stringResource(R.string.leave_edit_mode), onDone) },
                actions = {
                    PlayFromCursorButton(player, session)
                    IconAction(R.drawable.ic_undo, stringResource(R.string.undo), { onSessionChange(session.undo()) }, enabled = session.canUndo)
                    IconAction(R.drawable.ic_redo, stringResource(R.string.redo), { onSessionChange(session.redo()) }, enabled = session.canRedo)
                },
            )
        },
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        // The tools sit beside the Tablature on a wide, landscape screen and below it otherwise.
        AdaptivePanes(
            modifier = Modifier.padding(innerPadding).consumeWindowInsets(innerPadding).fillMaxSize(),
            panel = { sideBySide -> EditTools(session, onSessionChange, sideBySide) },
        ) {
            // Rebuilt from the edited Song on every change, so Playback always plays what was just written.
            NotationView(song, shownViews.inEditMode(), Modifier.fillMaxSize(), player = player, track = session.track)
        }
    }
}

/**
 * Everything that edits the Song. The cursor and number pads stay put at the bottom, within reach;
 * the tools above them scroll when there isn't room for all of them (a phone on its side).
 */
@Composable
private fun EditTools(session: EditSession, onSessionChange: (EditSession) -> Unit, sideBySide: Boolean) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier
            .then(if (sideBySide) Modifier.fillMaxHeight() else Modifier)
            .drawBehind {
                // Separates the tools from the Tablature: a line on the side they share.
                val stroke = 1.dp.toPx()
                if (sideBySide) {
                    drawLine(dividerColor, Offset(stroke / 2, 0f), Offset(stroke / 2, size.height), stroke)
                } else {
                    drawLine(dividerColor, Offset(0f, stroke / 2), Offset(size.width, stroke / 2), stroke)
                }
            },
    ) {
        Column(
            Modifier
                // Takes all the room above the pads beside the Tablature; only what it needs below it.
                .weight(1f, fill = sideBySide)
                .verticalScroll(rememberScrollState())
                .padding(top = 4.dp),
        ) {
            TrackTools(session, onSessionChange)
            MeasureStrip(session, onSessionChange)
            MeasureTools(session, onSessionChange)
            SectionTools(session, onSessionChange)
            MeasureEditor(session)
            DurationBar(session, onSessionChange)
            TechniqueBar(session, onSessionChange)
        }
        // On a tablet the pads keep a thumb-sized width rather than stretching across the screen.
        Column(
            Modifier.widthIn(max = PADS_MAX_WIDTH).fillMaxWidth().align(Alignment.CenterHorizontally),
        ) {
            CursorPad(session, onSessionChange)
            NumberPad(onDigit = { onSessionChange(session.typeDigit(it)) })
        }
    }
}

private val PADS_MAX_WIDTH = 560.dp

/** Picks the Track being edited, adds a Track, and opens the edited Track's settings. */
@Composable
private fun TrackTools(session: EditSession, onSessionChange: (EditSession) -> Unit) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var changing by rememberSaveable { mutableStateOf(false) }
    val tracks = session.song.tracks
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Dropdown(
            label = stringResource(R.string.field_edited_track),
            options = tracks.indices.toList(),
            selected = session.track,
            optionLabel = { tracks[it].name },
            onSelect = { onSessionChange(session.editTrack(it)) },
            modifier = Modifier.weight(1f),
        )
        // Beside the Track picker, a gear and a plus say on their own what they do.
        IconAction(R.drawable.ic_settings, stringResource(R.string.track_settings), { changing = true })
        IconAction(R.drawable.ic_add, stringResource(R.string.add_track), { adding = true })
    }
    if (adding) {
        NewTrackDialog(
            onAdd = { name, instrument, sound ->
                adding = false
                onSessionChange(session.addTrack(name, instrument, sound))
            },
            onDismiss = { adding = false },
        )
    }
    if (changing) {
        TrackSettingsDialog(
            track = tracks[session.track],
            onApply = { name, tuning, sound ->
                changing = false
                onSessionChange(session.changeTrack(name, tuning, sound))
            },
            onRemove = if (session.canRemoveTrack) {
                {
                    changing = false
                    onSessionChange(session.removeTrack())
                }
            } else {
                null
            },
            onDismiss = { changing = false },
        )
    }
}

/** Plays the Song from the cursor, or stops Playback and goes back to where it started. */
@Composable
private fun PlayFromCursorButton(player: NotationPlayer, session: EditSession) {
    IconAction(
        icon = if (player.isPlaying) R.drawable.ic_stop else R.drawable.ic_play_arrow,
        label = stringResource(if (player.isPlaying) R.string.playback_stop else R.string.playback_play_from_cursor),
        onClick = { if (player.isPlaying) player.stop() else player.playFrom(session.cursor, session.track) },
        enabled = player.isReady,
    )
}

/** Every Measure by number, the cursor's highlighted; incomplete or overfull ones flagged. */
@Composable
private fun MeasureStrip(session: EditSession, onSessionChange: (EditSession) -> Unit) {
    val listState = rememberLazyListState()
    LaunchedEffect(session.cursor.measure) { listState.animateScrollToItem(session.cursor.measure) }
    LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        itemsIndexed(session.song.measures) { index, _ ->
            val fill = session.song.measureFill(track = session.track, measure = index)
            val flagged = fill == MeasureFill.INCOMPLETE || fill == MeasureFill.OVERFULL
            val description = measureDescription(session.song, index, fill)
            val shown = if (session.song.isPickup(index)) stringResource(R.string.pickup) else "${session.song.measureNumber(index)}"
            // A Section's name leads its first Measure.
            val sectionName = session.song.sections.firstOrNull { it.first == index }?.let { "${it.name} · " } ?: ""
            FilterChip(
                selected = index == session.cursor.measure,
                onClick = { onSessionChange(session.moveToMeasure(index)) },
                label = {
                    Text(
                        sectionName + if (flagged) "$shown !" else shown,
                        color = if (flagged) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                },
                modifier = Modifier.semantics { contentDescription = description },
            )
        }
    }
}

/** Measure [measure] (0-based) of [song] by name, saying when it is incomplete or overfull. */
@Composable
private fun measureDescription(song: Song, measure: Int, fill: MeasureFill): String {
    val number = song.measureNumber(measure)
    return when {
        fill == MeasureFill.INCOMPLETE -> if (song.isPickup(measure)) stringResource(R.string.pickup_incomplete) else stringResource(R.string.measure_incomplete, number)
        fill == MeasureFill.OVERFULL -> if (song.isPickup(measure)) stringResource(R.string.pickup_overfull) else stringResource(R.string.measure_overfull, number)
        else -> measureLabel(song, measure)
    }
}

/**
 * Inserts and deletes Measures (in every Track), changes the time signature, key signature and
 * Tempo from the cursor's Measure on, and on the first Measure makes, changes or removes a Pickup.
 */
@Composable
private fun MeasureTools(session: EditSession, onSessionChange: (EditSession) -> Unit) {
    var editingSettings by rememberSaveable { mutableStateOf(false) }
    var editingPickup by rememberSaveable { mutableStateOf(false) }
    val measure = session.measureAtCursor
    val pickup = session.song.pickup
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
    ) {
        ToolButton(R.drawable.ic_add_column_left, stringResource(R.string.insert_measure_before), enabled = session.canInsertMeasureBefore) {
            onSessionChange(session.insertMeasureBefore())
        }
        ToolButton(R.drawable.ic_add_column_right, stringResource(R.string.insert_measure_after)) {
            onSessionChange(session.insertMeasureAfter())
        }
        ToolButton(R.drawable.ic_delete, stringResource(R.string.delete_measure), enabled = session.canDeleteMeasure) {
            onSessionChange(session.deleteMeasure())
        }
        val summary = stringResource(
            R.string.measure_settings_summary,
            measure.timeSignature.beats,
            measure.timeSignature.beatUnit,
            keySignatureLabel(measure.keySignature),
            measure.tempo.beatsPerMinute,
        )
        val description = stringResource(R.string.change_measure_settings, summary)
        ToolButton(
            R.drawable.ic_tune,
            summary,
            modifier = Modifier.semantics { contentDescription = description },
        ) { editingSettings = true }
        if (session.cursor.measure == 0) {
            OutlinedButton(onClick = { editingPickup = true }) {
                Text(stringResource(if (pickup == null) R.string.make_pickup else R.string.change_pickup))
            }
            if (pickup != null) {
                ToolButton(R.drawable.ic_delete, stringResource(R.string.remove_pickup)) { onSessionChange(session.removePickup()) }
            }
        }
    }
    if (editingPickup) {
        PickupDialog(
            timeSignature = session.song.measures.first().timeSignature,
            current = pickup,
            canMake = session::canMakePickup,
            onApply = {
                editingPickup = false
                onSessionChange(session.makePickup(it))
            },
            onDismiss = { editingPickup = false },
        )
    }
    if (editingSettings) {
        MeasureSettingsDialog(
            title = measureLabel(session.song, session.cursor.measure),
            current = measure,
            canApply = session::canChangeMeasure,
            onApply = {
                editingSettings = false
                onSessionChange(session.changeMeasure(it))
            },
            onDismiss = { editingSettings = false },
        )
    }
}

/**
 * Asks how long the first Measure's Pickup lasts, from a 16th note up to just short of
 * [timeSignature], starting from [current].
 */
@Composable
private fun PickupDialog(timeSignature: TimeSignature, current: Pickup?, canMake: (Pickup) -> Boolean, onApply: (Pickup) -> Unit, onDismiss: () -> Unit) {
    val options = (1 until SIXTEENTHS_PER_WHOLE * timeSignature.beats / timeSignature.beatUnit).map(::Pickup).filter(canMake)
    var selected by rememberSaveable { mutableIntStateOf(current?.sixteenths ?: (SIXTEENTHS_PER_WHOLE / timeSignature.beatUnit)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pickup_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.pickup_hint), style = MaterialTheme.typography.bodySmall)
                Dropdown(
                    label = stringResource(R.string.field_pickup_length),
                    options = options,
                    selected = Pickup(selected),
                    optionLabel = { stringResource(R.string.pickup_length_option, beatsIn(it, timeSignature), timeSignature.beats) },
                    onSelect = { selected = it.sixteenths },
                )
            }
        },
        confirmButton = {
            TextButton(enabled = canMake(Pickup(selected)), onClick = { onApply(Pickup(selected)) }) { Text(stringResource(R.string.apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** How many beats of [timeSignature] [pickup] lasts, e.g. "1" or "1.5". */
private fun beatsIn(pickup: Pickup, timeSignature: TimeSignature): String {
    val beats = pickup.sixteenths * timeSignature.beatUnit / SIXTEENTHS_PER_WHOLE.toDouble()
    return if (beats % 1.0 == 0.0) beats.toInt().toString() else beats.toString().trimEnd('0')
}

private const val SIXTEENTHS_PER_WHOLE = 16

/**
 * Asks for a Measure's time signature, key signature and Tempo, starting from [current]; settings
 * [canApply] refuses (a time signature a Pickup would fill) can't be applied.
 */
@Composable
private fun MeasureSettingsDialog(title: String, current: Measure, canApply: (Measure) -> Boolean, onApply: (Measure) -> Unit, onDismiss: () -> Unit) {
    var tempoText by rememberSaveable { mutableStateOf(current.tempo.beatsPerMinute.toString()) }
    var beatsText by rememberSaveable { mutableStateOf(current.timeSignature.beats.toString()) }
    var beatUnit by rememberSaveable { mutableIntStateOf(current.timeSignature.beatUnit) }
    var keyIndex by rememberSaveable { mutableIntStateOf(KeySignature.ALL.indexOf(current.keySignature)) }

    val tempo = tempoText.toIntOrNull()?.takeIf { it in TEMPO_RANGE }
    val beats = beatsText.toIntOrNull()?.takeIf { it in BEATS_RANGE }
    val chosen = if (tempo != null && beats != null) Measure(TimeSignature(beats, beatUnit), KeySignature.ALL[keyIndex], Tempo(tempo)) else null
    val refused = chosen != null && !canApply(chosen)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.measure_settings_hint), style = MaterialTheme.typography.bodySmall)
                NumberField(R.string.field_tempo, tempoText, isError = tempo == null) { tempoText = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(R.string.field_beats, beatsText, isError = beats == null, Modifier.weight(1f)) { beatsText = it }
                    Dropdown(
                        label = stringResource(R.string.field_beat_unit),
                        options = BEAT_UNITS,
                        selected = beatUnit,
                        optionLabel = { it.toString() },
                        onSelect = { beatUnit = it },
                        modifier = Modifier.weight(1f),
                    )
                }
                Dropdown(
                    label = stringResource(R.string.field_key),
                    options = KeySignature.ALL,
                    selected = KeySignature.ALL[keyIndex],
                    optionLabel = { keySignatureLabel(it) },
                    onSelect = { keyIndex = KeySignature.ALL.indexOf(it) },
                )
                if (refused) {
                    Text(stringResource(R.string.pickup_too_long), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = chosen != null && !refused,
                onClick = { onApply(chosen!!) },
            ) { Text(stringResource(R.string.apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/**
 * Names, renames, resizes and removes the Section under the cursor, and copies its Measures to
 * paste before another Measure, in every Track.
 */
@Composable
private fun SectionTools(session: EditSession, onSessionChange: (EditSession) -> Unit) {
    var addingSection by rememberSaveable { mutableStateOf(false) }
    var editingSection by rememberSaveable { mutableStateOf(false) }
    val section = session.sectionAtCursor
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
    ) {
        Text(
            if (section != null) stringResource(R.string.section_at_cursor, section.name) else stringResource(R.string.no_section),
            style = MaterialTheme.typography.labelMedium,
        )
        if (section == null) {
            ToolButton(R.drawable.ic_add, stringResource(R.string.new_section)) { addingSection = true }
        } else {
            ToolButton(R.drawable.ic_edit, stringResource(R.string.edit_section)) { editingSection = true }
            ToolButton(R.drawable.ic_content_copy, stringResource(R.string.copy_section)) { onSessionChange(session.copySection()) }
        }
        val pasteDescription = stringResource(R.string.paste_measures_description, measureLabel(session.song, session.cursor.measure))
        ToolButton(
            R.drawable.ic_content_paste,
            stringResource(R.string.paste_measures),
            enabled = session.canPaste,
            modifier = Modifier.semantics { contentDescription = pasteDescription },
        ) { onSessionChange(session.pasteMeasures()) }
    }
    if (addingSection) {
        SectionDialog(
            title = stringResource(R.string.new_section_title),
            initialName = "",
            initialFirst = session.cursor.measure,
            initialLast = session.cursor.measure,
            song = session.song,
            canPlace = session::canAddSection,
            onApply = { name, first, last ->
                addingSection = false
                onSessionChange(session.addSection(name, first, last))
            },
            onDismiss = { addingSection = false },
        )
    }
    if (editingSection && section != null) {
        SectionDialog(
            title = stringResource(R.string.edit_section_title, section.name),
            initialName = section.name,
            initialFirst = section.first,
            initialLast = section.last,
            song = session.song,
            canPlace = session::canResizeSection,
            onApply = { name, first, last ->
                editingSection = false
                onSessionChange(session.changeSection(name, first, last))
            },
            onDismiss = { editingSection = false },
            onRemove = {
                editingSection = false
                onSessionChange(session.removeSection())
            },
        )
    }
}

/**
 * Asks for a Section's name and its first and last Measure of [song], starting from [initialFirst]
 * to [initialLast]. They are typed as the numbers Measures are shown with (0 for a Pickup); they
 * and [onApply] and [canPlace] are 0-based. [onRemove], when given, offers to remove the Section.
 */
@Composable
private fun SectionDialog(
    title: String,
    initialName: String,
    initialFirst: Int,
    initialLast: Int,
    song: Song,
    canPlace: (first: Int, last: Int) -> Boolean,
    onApply: (name: String, first: Int, last: Int) -> Unit,
    onDismiss: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var firstText by rememberSaveable { mutableStateOf(song.measureNumber(initialFirst).toString()) }
    var lastText by rememberSaveable { mutableStateOf(song.measureNumber(initialLast).toString()) }

    val first = firstText.toIntOrNull()?.let(song::measureNumbered)
    val last = lastText.toIntOrNull()?.let(song::measureNumbered)?.takeIf { first == null || it >= first }
    val overlaps = first != null && last != null && !canPlace(first, last)
    val canApply = name.isNotBlank() && first != null && last != null && !overlaps

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_section_name)) },
                    isError = name.isBlank(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberField(R.string.field_first_measure, firstText, isError = first == null || overlaps, Modifier.weight(1f)) {
                        firstText = it
                    }
                    NumberField(R.string.field_last_measure, lastText, isError = last == null || overlaps, Modifier.weight(1f)) {
                        lastText = it
                    }
                }
                if (song.pickup != null) Text(stringResource(R.string.pickup_is_zero), style = MaterialTheme.typography.bodySmall)
                if (overlaps) {
                    Text(
                        stringResource(R.string.section_overlaps),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (onRemove != null) {
                    TextButton(onClick = onRemove, contentPadding = ButtonDefaults.TextButtonWithIconContentPadding) {
                        IconAndLabel(R.drawable.ic_delete, stringResource(R.string.remove_section))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canApply, onClick = { onApply(name, first!!, last!!) }) {
                Text(stringResource(R.string.apply))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The cursor's Measure as a grid of strings by beats, the cursor's cell highlighted. */
@Composable
private fun MeasureEditor(session: EditSession) {
    val track = session.song.tracks[session.track]
    val measure = session.cursor.measure
    val elements = track.voice.measures[measure]
    val fill = session.song.measureFill(track = session.track, measure = measure)
    val hasFreeBeat = fill == MeasureFill.UNWRITTEN || fill == MeasureFill.INCOMPLETE
    val columns = elements.size + if (hasFreeBeat) 1 else 0

    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        val flagColor = if (fill == MeasureFill.INCOMPLETE || fill == MeasureFill.OVERFULL) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(measureDescription(session.song, measure, fill), color = flagColor, style = MaterialTheme.typography.labelMedium)
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            for (beat in 0 until columns) {
                val element = elements.getOrNull(beat)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    for (string in 1..track.instrument.stringCount) {
                        val isCursor = session.cursor.beat == beat && session.cursor.string == string
                        val note = element?.noteOn(string)
                        val text = when {
                            note != null -> noteSymbol(note)
                            element is Rest && string == (track.instrument.stringCount + 1) / 2 -> stringResource(R.string.rest_short)
                            else -> ""
                        }
                        GridCell(text, isCursor)
                    }
                    Text(
                        element?.duration?.let { durationSymbol(it) } ?: "",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun GridCell(text: String, isCursor: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 36.dp, height = 22.dp)
            .background(if (isCursor) colors.primaryContainer else colors.surface)
            .border(if (isCursor) 2.dp else 0.5.dp, if (isCursor) colors.primary else colors.outlineVariant),
    ) {
        Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
    }
}

/** Note values whole to 32nd, dot, triplet, Rest and delete. */
@Composable
private fun DurationBar(session: EditSession, onSessionChange: (EditSession) -> Unit) {
    val duration = session.duration
    Column(Modifier.padding(horizontal = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            NoteValue.entries.forEach { value ->
                val description = stringResource(value.label())
                FilterChip(
                    selected = duration.value == value,
                    onClick = { onSessionChange(session.chooseNoteValue(value)) },
                    label = { Text(value.symbol()) },
                    modifier = Modifier.semantics { contentDescription = description },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
            FilterChip(
                selected = duration.isDotted,
                onClick = { onSessionChange(session.toggleDot()) },
                label = { Text(stringResource(R.string.dotted)) },
            )
            FilterChip(
                selected = duration.isTriplet,
                onClick = { onSessionChange(session.toggleTriplet()) },
                label = { Text(stringResource(R.string.triplet)) },
            )
            OutlinedButton(onClick = { onSessionChange(session.writeRest()) }) { Text(stringResource(R.string.rest)) }
            ToolButton(R.drawable.ic_backspace, stringResource(R.string.delete), enabled = session.elementAtCursor != null) {
                onSessionChange(session.delete())
            }
        }
    }
}

/** Ties the Note at the cursor and adds or removes its Techniques; disabled where there is no Note. */
@Composable
private fun TechniqueBar(session: EditSession, onSessionChange: (EditSession) -> Unit) {
    val note = session.noteAtCursor
    val description = stringResource(R.string.techniques)
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp)
            .semantics { contentDescription = description },
    ) {
        FilterChip(
            selected = note?.isTied == true,
            onClick = { onSessionChange(session.toggleTie()) },
            label = { Text(stringResource(R.string.tie)) },
            enabled = session.canTie,
        )
        session.availableTechniques.forEach { technique ->
            FilterChip(
                selected = note != null && technique in note.techniques,
                onClick = { onSessionChange(session.toggleTechnique(technique)) },
                label = { Text(stringResource(technique.label())) },
                enabled = note != null,
            )
        }
    }
}

/** Moves the cursor by Measure, beat and string. */
@Composable
private fun CursorPad(session: EditSession, onSessionChange: (EditSession) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        PadButton(R.drawable.ic_keyboard_double_arrow_left, stringResource(R.string.cursor_previous_measure)) { onSessionChange(session.moveToPreviousMeasure()) }
        PadButton(R.drawable.ic_chevron_left, stringResource(R.string.cursor_previous_beat)) { onSessionChange(session.moveToPreviousBeat()) }
        PadButton(R.drawable.ic_arrow_upward, stringResource(R.string.cursor_string_above)) { onSessionChange(session.moveToStringAbove()) }
        PadButton(R.drawable.ic_arrow_downward, stringResource(R.string.cursor_string_below)) { onSessionChange(session.moveToStringBelow()) }
        PadButton(R.drawable.ic_chevron_right, stringResource(R.string.cursor_next_beat)) { onSessionChange(session.moveToNextBeat()) }
        PadButton(R.drawable.ic_keyboard_double_arrow_right, stringResource(R.string.cursor_next_measure)) { onSessionChange(session.moveToNextMeasure()) }
    }
}

/** Frets 0–9; two digits in a row make a two-digit fret. */
@Composable
private fun NumberPad(onDigit: (Int) -> Unit) {
    val description = stringResource(R.string.fret_pad)
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
            .semantics { contentDescription = description },
    ) {
        listOf(1..5, listOf(6, 7, 8, 9, 0)).forEach { digits ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                digits.forEach { digit ->
                    FilledTonalButton(
                        onClick = { onDigit(digit) },
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.weight(1f),
                    ) { Text(digit.toString()) }
                }
            }
        }
    }
}

/** A cursor move: its arrow says what it does, so [description] is only read out. */
@Composable
private fun RowScope.PadButton(@DrawableRes icon: Int, description: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.weight(1f),
    ) { Icon(painterResource(icon), contentDescription = description) }
}

/** An edit tool: [icon] alone doesn't say what it does to the Song, so [label] stays beside it. */
@Composable
private fun ToolButton(
    @DrawableRes icon: Int,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        modifier = modifier,
    ) { IconAndLabel(icon, label) }
}

private fun NoteValue.symbol(): String = when (this) {
    NoteValue.WHOLE -> "1"
    NoteValue.HALF -> "1/2"
    NoteValue.QUARTER -> "1/4"
    NoteValue.EIGHTH -> "1/8"
    NoteValue.SIXTEENTH -> "1/16"
    NoteValue.THIRTY_SECOND -> "1/32"
}

/** A short written form of a Duration, e.g. "1/4." for a dotted quarter or "1/8³" for a triplet eighth. */
private fun durationSymbol(duration: Duration): String =
    duration.value.symbol() + (if (duration.isDotted) "." else "") + (if (duration.isTriplet) "³" else "")

/** A Note's cell in the grid: its fret, in brackets when tied, or an x for a dead note. */
private fun noteSymbol(note: Note): String = when {
    Technique.DEAD_NOTE in note.techniques -> "x"
    note.isTied -> "(${note.fret})"
    else -> note.fret.toString()
}

private fun NoteValue.label(): Int = when (this) {
    NoteValue.WHOLE -> R.string.note_value_whole
    NoteValue.HALF -> R.string.note_value_half
    NoteValue.QUARTER -> R.string.note_value_quarter
    NoteValue.EIGHTH -> R.string.note_value_eighth
    NoteValue.SIXTEENTH -> R.string.note_value_sixteenth
    NoteValue.THIRTY_SECOND -> R.string.note_value_thirty_second
}

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun EditScreenPreview() {
    CompassTheme {
        EditScreen(EditSession.start(demoSong).moveToNextBeat(), ShownViews.BOTH, onSessionChange = {}, onDone = {})
    }
}
