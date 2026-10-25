package com.saulnunez.compass.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
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
import com.saulnunez.compass.domain.Tempo
import com.saulnunez.compass.domain.TimeSignature
import com.saulnunez.compass.library.keySignatureLabel
import com.saulnunez.compass.ui.BEATS_RANGE
import com.saulnunez.compass.ui.BEAT_UNITS
import com.saulnunez.compass.ui.Dropdown
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
import com.saulnunez.compass.notation.TablatureView
import com.saulnunez.compass.practice.demoSong
import com.saulnunez.compass.ui.theme.CompassTheme

/**
 * Edit mode: writes Notes, Chords and Rests, ties and Techniques into the Tablature of a Song's first Track through
 * [session]. Every action hands the next session to [onSessionChange]; [onDone] switches back
 * to Practice mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditScreen(
    session: EditSession,
    onSessionChange: (EditSession) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val song = session.song
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
                navigationIcon = { TextButton(onClick = onDone) { Text(stringResource(R.string.leave_edit_mode)) } },
                actions = {
                    TextButton(onClick = { onSessionChange(session.undo()) }, enabled = session.canUndo) {
                        Text(stringResource(R.string.undo))
                    }
                    TextButton(onClick = { onSessionChange(session.redo()) }, enabled = session.canRedo) {
                        Text(stringResource(R.string.redo))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            TablatureView(song, Modifier.weight(1f).fillMaxWidth())
            HorizontalDivider()
            MeasureStrip(session, onSessionChange)
            MeasureTools(session, onSessionChange)
            MeasureEditor(session)
            DurationBar(session, onSessionChange)
            TechniqueBar(session, onSessionChange)
            CursorPad(session, onSessionChange)
            NumberPad(onDigit = { onSessionChange(session.typeDigit(it)) })
        }
    }
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
            val fill = session.song.measureFill(track = 0, measure = index)
            val flagged = fill == MeasureFill.INCOMPLETE || fill == MeasureFill.OVERFULL
            val description = measureDescription(index + 1, fill)
            FilterChip(
                selected = index == session.cursor.measure,
                onClick = { onSessionChange(session.moveToMeasure(index)) },
                label = {
                    Text(
                        if (flagged) "${index + 1} !" else "${index + 1}",
                        color = if (flagged) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                },
                modifier = Modifier.semantics { contentDescription = description },
            )
        }
    }
}

@Composable
private fun measureDescription(number: Int, fill: MeasureFill): String = when (fill) {
    MeasureFill.INCOMPLETE -> stringResource(R.string.measure_incomplete, number)
    MeasureFill.OVERFULL -> stringResource(R.string.measure_overfull, number)
    MeasureFill.UNWRITTEN, MeasureFill.FULL -> stringResource(R.string.measure_number, number)
}

/**
 * Inserts and deletes Measures (in every Track) and changes the time signature, key signature
 * and Tempo from the cursor's Measure on.
 */
@Composable
private fun MeasureTools(session: EditSession, onSessionChange: (EditSession) -> Unit) {
    var editingSettings by rememberSaveable { mutableStateOf(false) }
    val measure = session.measureAtCursor
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
    ) {
        OutlinedButton(onClick = { onSessionChange(session.insertMeasureBefore()) }) {
            Text(stringResource(R.string.insert_measure_before))
        }
        OutlinedButton(onClick = { onSessionChange(session.insertMeasureAfter()) }) {
            Text(stringResource(R.string.insert_measure_after))
        }
        OutlinedButton(onClick = { onSessionChange(session.deleteMeasure()) }, enabled = session.canDeleteMeasure) {
            Text(stringResource(R.string.delete_measure))
        }
        val summary = stringResource(
            R.string.measure_settings_summary,
            measure.timeSignature.beats,
            measure.timeSignature.beatUnit,
            keySignatureLabel(measure.keySignature),
            measure.tempo.beatsPerMinute,
        )
        val description = stringResource(R.string.change_measure_settings, summary)
        OutlinedButton(
            onClick = { editingSettings = true },
            modifier = Modifier.semantics { contentDescription = description },
        ) { Text(summary) }
    }
    if (editingSettings) {
        MeasureSettingsDialog(
            measureNumber = session.cursor.measure + 1,
            current = measure,
            onApply = {
                editingSettings = false
                onSessionChange(session.changeMeasure(it))
            },
            onDismiss = { editingSettings = false },
        )
    }
}

/** Asks for a Measure's time signature, key signature and Tempo, starting from [current]. */
@Composable
private fun MeasureSettingsDialog(measureNumber: Int, current: Measure, onApply: (Measure) -> Unit, onDismiss: () -> Unit) {
    var tempoText by rememberSaveable { mutableStateOf(current.tempo.beatsPerMinute.toString()) }
    var beatsText by rememberSaveable { mutableStateOf(current.timeSignature.beats.toString()) }
    var beatUnit by rememberSaveable { mutableIntStateOf(current.timeSignature.beatUnit) }
    var keyIndex by rememberSaveable { mutableIntStateOf(KeySignature.ALL.indexOf(current.keySignature)) }

    val tempo = tempoText.toIntOrNull()?.takeIf { it in TEMPO_RANGE }
    val beats = beatsText.toIntOrNull()?.takeIf { it in BEATS_RANGE }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.measure_settings_title, measureNumber)) },
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
            }
        },
        confirmButton = {
            TextButton(
                enabled = tempo != null && beats != null,
                onClick = { onApply(Measure(TimeSignature(beats!!, beatUnit), KeySignature.ALL[keyIndex], Tempo(tempo!!))) },
            ) { Text(stringResource(R.string.apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** The cursor's Measure as a grid of strings by beats, the cursor's cell highlighted. */
@Composable
private fun MeasureEditor(session: EditSession) {
    val track = session.song.tracks[0]
    val measure = session.cursor.measure
    val elements = track.voice.measures[measure]
    val fill = session.song.measureFill(track = 0, measure = measure)
    val hasFreeBeat = fill == MeasureFill.UNWRITTEN || fill == MeasureFill.INCOMPLETE
    val columns = elements.size + if (hasFreeBeat) 1 else 0

    Column(Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        val flagColor = if (fill == MeasureFill.INCOMPLETE || fill == MeasureFill.OVERFULL) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
        Text(measureDescription(measure + 1, fill), color = flagColor, style = MaterialTheme.typography.labelMedium)
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
            OutlinedButton(onClick = { onSessionChange(session.delete()) }, enabled = session.elementAtCursor != null) {
                Text(stringResource(R.string.delete))
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
        PadButton("«", stringResource(R.string.cursor_previous_measure)) { onSessionChange(session.moveToPreviousMeasure()) }
        PadButton("‹", stringResource(R.string.cursor_previous_beat)) { onSessionChange(session.moveToPreviousBeat()) }
        PadButton("↑", stringResource(R.string.cursor_string_above)) { onSessionChange(session.moveToStringAbove()) }
        PadButton("↓", stringResource(R.string.cursor_string_below)) { onSessionChange(session.moveToStringBelow()) }
        PadButton("›", stringResource(R.string.cursor_next_beat)) { onSessionChange(session.moveToNextBeat()) }
        PadButton("»", stringResource(R.string.cursor_next_measure)) { onSessionChange(session.moveToNextMeasure()) }
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

@Composable
private fun RowScope.PadButton(symbol: String, description: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.weight(1f).semantics { contentDescription = description },
    ) { Text(symbol, textAlign = TextAlign.Center) }
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

private fun Technique.label(): Int = when (this) {
    Technique.HAMMER_ON_PULL_OFF -> R.string.technique_hammer_on_pull_off
    Technique.SLIDE -> R.string.technique_slide
    Technique.BEND -> R.string.technique_bend
    Technique.BEND_AND_RELEASE -> R.string.technique_bend_and_release
    Technique.VIBRATO -> R.string.technique_vibrato
    Technique.PALM_MUTE -> R.string.technique_palm_mute
    Technique.DEAD_NOTE -> R.string.technique_dead_note
    Technique.SLAP -> R.string.technique_slap
    Technique.POP -> R.string.technique_pop
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
        EditScreen(EditSession.start(demoSong).moveToNextBeat(), onSessionChange = {}, onDone = {})
    }
}
