package com.saulnunez.compass.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.saulnunez.compass.R
import com.saulnunez.compass.domain.Loop
import com.saulnunez.compass.domain.Song
import com.saulnunez.compass.domain.measuresIn
import com.saulnunez.compass.ui.NumberField

/** Shows the Loop in use, if any, and lets the person pick another: a Section, any Measures, or none. */
@Composable
fun LoopChoice(song: Song, loop: Loop?, onLoopChange: (Loop?) -> Unit, modifier: Modifier = Modifier) {
    var choosing by rememberSaveable { mutableStateOf(false) }
    // A Loop whose Measures the Song no longer has is treated as none.
    val measures = loop?.measuresIn(song)
    FilterChip(
        selected = measures != null,
        onClick = { choosing = true },
        label = {
            Text(
                when {
                    measures == null -> stringResource(R.string.playback_loop)
                    loop is Loop.OfSection -> stringResource(R.string.loop_of_section, song.sections[loop.section].name)
                    else -> stringResource(R.string.loop_of_measures, measures.first + 1, measures.last + 1)
                },
            )
        },
        modifier = modifier,
    )
    if (choosing) {
        LoopDialog(
            song = song,
            loop = loop.takeIf { measures != null },
            onChoose = {
                choosing = false
                onLoopChange(it)
            },
            onDismiss = { choosing = false },
        )
    }
}

/** What the Loop dialog has selected: no Loop, the Section at an index, or a Measure range. */
private const val NO_LOOP = -1
private const val MEASURE_RANGE = -2

/** Asks for a Loop; Measures are numbered from 1 here and 0-based in the [Loop] given to [onChoose]. */
@Composable
private fun LoopDialog(song: Song, loop: Loop?, onChoose: (Loop?) -> Unit, onDismiss: () -> Unit) {
    var selected by rememberSaveable {
        mutableStateOf(
            when (loop) {
                null -> NO_LOOP
                is Loop.OfSection -> loop.section
                is Loop.OfMeasures -> MEASURE_RANGE
            },
        )
    }
    val initial = (loop as? Loop.OfMeasures)?.let { it.first + 1 to it.last + 1 } ?: (1 to song.measures.size)
    var firstText by rememberSaveable { mutableStateOf(initial.first.toString()) }
    var lastText by rememberSaveable { mutableStateOf(initial.second.toString()) }
    val measureCount = song.measures.size
    val first = firstText.toIntOrNull()?.takeIf { it in 1..measureCount }
    val last = lastText.toIntOrNull()?.takeIf { it in 1..measureCount && (first == null || it >= first) }

    val chosen: Loop? = when (selected) {
        NO_LOOP -> null
        MEASURE_RANGE -> if (first != null && last != null) Loop.OfMeasures(first - 1, last - 1) else null
        else -> Loop.OfSection(selected)
    }
    val canApply = selected != MEASURE_RANGE || chosen != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.loop_title)) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                LoopOption(stringResource(R.string.loop_none), selected == NO_LOOP) { selected = NO_LOOP }
                song.sections.forEachIndexed { index, section ->
                    LoopOption(
                        stringResource(R.string.loop_section_option, section.name, section.first + 1, section.last + 1),
                        selected == index,
                    ) { selected = index }
                }
                LoopOption(stringResource(R.string.loop_measures_option), selected == MEASURE_RANGE) { selected = MEASURE_RANGE }
                if (selected == MEASURE_RANGE) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumberField(R.string.field_first_measure, firstText, isError = first == null, Modifier.weight(1f)) {
                            firstText = it
                        }
                        NumberField(R.string.field_last_measure, lastText, isError = last == null, Modifier.weight(1f)) {
                            lastText = it
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = canApply, onClick = { onChoose(chosen) }) { Text(stringResource(R.string.apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun LoopOption(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(label)
    }
}
