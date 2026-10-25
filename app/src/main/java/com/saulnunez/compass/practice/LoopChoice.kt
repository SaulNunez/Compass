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
import androidx.compose.runtime.saveable.Saver
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
private sealed interface LoopSelection {
    data object None : LoopSelection
    data class OfSection(val section: Int) : LoopSelection
    data object OfMeasures : LoopSelection
}

/** Keeps the selection across rotation as a number: a Section's index, or below 0 for the others. */
private val LoopSelectionSaver = Saver<LoopSelection, Int>(
    save = {
        when (it) {
            LoopSelection.None -> SAVED_NONE
            LoopSelection.OfMeasures -> SAVED_MEASURES
            is LoopSelection.OfSection -> it.section
        }
    },
    restore = {
        when (it) {
            SAVED_NONE -> LoopSelection.None
            SAVED_MEASURES -> LoopSelection.OfMeasures
            else -> LoopSelection.OfSection(it)
        }
    },
)
private const val SAVED_NONE = -1
private const val SAVED_MEASURES = -2

/** Asks for a Loop; Measures are numbered from 1 here and 0-based in the [Loop] given to [onChoose]. */
@Composable
private fun LoopDialog(song: Song, loop: Loop?, onChoose: (Loop?) -> Unit, onDismiss: () -> Unit) {
    var selected by rememberSaveable(stateSaver = LoopSelectionSaver) {
        mutableStateOf(
            when (loop) {
                null -> LoopSelection.None
                is Loop.OfSection -> LoopSelection.OfSection(loop.section)
                is Loop.OfMeasures -> LoopSelection.OfMeasures
            },
        )
    }
    val initial = (loop as? Loop.OfMeasures)?.let { it.first + 1 to it.last + 1 } ?: (1 to song.measures.size)
    var firstText by rememberSaveable { mutableStateOf(initial.first.toString()) }
    var lastText by rememberSaveable { mutableStateOf(initial.second.toString()) }
    val measureCount = song.measures.size
    val first = firstText.toIntOrNull()?.takeIf { it in 1..measureCount }
    val last = lastText.toIntOrNull()?.takeIf { it in 1..measureCount && (first == null || it >= first) }

    val chosen: Loop? = when (val selection = selected) {
        LoopSelection.None -> null
        LoopSelection.OfMeasures -> if (first != null && last != null) Loop.OfMeasures(first - 1, last - 1) else null
        is LoopSelection.OfSection -> Loop.OfSection(selection.section)
    }
    val canApply = selected != LoopSelection.OfMeasures || chosen != null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.loop_title)) },
        text = {
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState())) {
                LoopOption(stringResource(R.string.loop_none), selected == LoopSelection.None) { selected = LoopSelection.None }
                song.sections.forEachIndexed { index, section ->
                    LoopOption(
                        stringResource(R.string.loop_section_option, section.name, section.first + 1, section.last + 1),
                        selected == LoopSelection.OfSection(index),
                    ) { selected = LoopSelection.OfSection(index) }
                }
                LoopOption(stringResource(R.string.loop_measures_option), selected == LoopSelection.OfMeasures) {
                    selected = LoopSelection.OfMeasures
                }
                if (selected == LoopSelection.OfMeasures) {
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
