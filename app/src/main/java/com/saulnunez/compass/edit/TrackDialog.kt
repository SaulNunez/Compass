package com.saulnunez.compass.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.saulnunez.compass.R
import com.saulnunez.compass.domain.Instrument
import com.saulnunez.compass.domain.Pitch
import com.saulnunez.compass.domain.Sound
import com.saulnunez.compass.domain.SupportedInstrument
import com.saulnunez.compass.domain.Track
import com.saulnunez.compass.domain.Tuning
import com.saulnunez.compass.domain.TuningPreset
import com.saulnunez.compass.library.defaultTrackName
import com.saulnunez.compass.library.label
import com.saulnunez.compass.library.pitchLabel
import com.saulnunez.compass.ui.Dropdown

/** Asks for a new Track's name, Instrument, Tuning and Sound, and hands them to [onAdd]. */
@Composable
fun NewTrackDialog(onAdd: (name: String, instrument: Instrument, sound: Sound) -> Unit, onDismiss: () -> Unit) {
    var instrument by rememberSaveable { mutableStateOf(SupportedInstrument.GUITAR_6) }
    val defaultName = stringResource(instrument.defaultTrackName())
    TrackFields(
        title = stringResource(R.string.new_track_title),
        initialName = defaultName,
        initialTuning = instrument.tuningPresets.first().tuning,
        initialSound = Sound.defaultFor(instrument.kind),
        instrument = instrument,
        // Picking another Instrument starts its Tuning and Sound afresh.
        onInstrumentChange = { instrument = it },
        onApply = { name, tuning, sound -> onAdd(name, Instrument(instrument.kind, tuning), sound) },
        onDismiss = onDismiss,
    )
}

/**
 * Asks for a new name, Tuning (ready-made or string by string) and Sound for [track], handing them
 * to [onApply]; [onRemove], when given, removes the Track.
 */
@Composable
fun TrackSettingsDialog(
    track: Track,
    onApply: (name: String, tuning: Tuning, sound: Sound) -> Unit,
    onRemove: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val instrument = SupportedInstrument.entries.first {
        it.kind == track.instrument.kind && it.stringCount == track.instrument.stringCount
    }
    TrackFields(
        title = stringResource(R.string.track_settings),
        initialName = track.name,
        initialTuning = track.instrument.tuning,
        initialSound = track.sound,
        instrument = instrument,
        onInstrumentChange = null,
        onApply = onApply,
        onDismiss = onDismiss,
        onRemove = onRemove,
    )
}

/**
 * The fields shared by both dialogs. [onInstrumentChange] is null when the Instrument is fixed;
 * the other fields restart whenever [instrument] changes.
 */
@Composable
private fun TrackFields(
    title: String,
    initialName: String,
    initialTuning: Tuning,
    initialSound: Sound,
    instrument: SupportedInstrument,
    onInstrumentChange: ((SupportedInstrument) -> Unit)?,
    onApply: (name: String, tuning: Tuning, sound: Sound) -> Unit,
    onDismiss: () -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    var name by rememberSaveable(instrument) { mutableStateOf(initialName) }
    var openStrings by rememberSaveable(instrument) { mutableStateOf(initialTuning.openStrings.map { it.midi }.toIntArray()) }
    var sound by rememberSaveable(instrument) { mutableStateOf(initialSound) }
    val tuning = Tuning(openStrings.map(::Pitch))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.field_track_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (onInstrumentChange != null) {
                    Dropdown(
                        label = stringResource(R.string.field_instrument),
                        options = SupportedInstrument.entries,
                        selected = instrument,
                        optionLabel = { stringResource(it.label()) },
                        onSelect = onInstrumentChange,
                    )
                }
                Dropdown(
                    label = stringResource(R.string.field_tuning),
                    // null stands for a custom Tuning.
                    options = instrument.tuningPresets + null,
                    selected = instrument.tuningPresets.firstOrNull { it.tuning == tuning },
                    optionLabel = { preset: TuningPreset? -> stringResource(preset?.label() ?: R.string.tuning_custom) },
                    onSelect = { preset -> if (preset != null) openStrings = preset.tuning.openStrings.map { it.midi }.toIntArray() },
                )
                openStrings.forEachIndexed { index, midi ->
                    StringTuning(
                        string = index + 1,
                        pitch = Pitch(midi),
                        onChange = { pitch -> openStrings = openStrings.copyOf().apply { set(index, pitch.midi) } },
                    )
                }
                if (onInstrumentChange == null) {
                    Text(stringResource(R.string.tuning_keeps_frets), style = MaterialTheme.typography.bodySmall)
                }
                Dropdown(
                    label = stringResource(R.string.field_sound),
                    options = Sound.choicesFor(instrument.kind),
                    selected = sound,
                    optionLabel = { stringResource(it.label()) },
                    onSelect = { sound = it },
                )
                if (onRemove != null) {
                    TextButton(onClick = onRemove) {
                        Text(stringResource(R.string.remove_track), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onApply(name.trim(), tuning, sound) }) {
                Text(stringResource(if (onInstrumentChange != null) R.string.create else R.string.apply))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** One string of a custom Tuning: its open pitch, a semitone lower or higher at a time. */
@Composable
private fun StringTuning(string: Int, pitch: Pitch, onChange: (Pitch) -> Unit) {
    val lower = stringResource(R.string.tuning_string_lower, string)
    val higher = stringResource(R.string.tuning_string_higher, string)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.tuning_string, string), Modifier.weight(1f))
        OutlinedButton(
            onClick = { onChange(Pitch(pitch.midi - 1)) },
            enabled = pitch.midi > LOWEST_OPEN_STRING,
            modifier = Modifier.semantics { contentDescription = lower },
        ) { Text("−") }
        Text(pitchLabel(pitch), style = MaterialTheme.typography.bodyLarge)
        OutlinedButton(
            onClick = { onChange(Pitch(pitch.midi + 1)) },
            enabled = pitch.midi < HIGHEST_OPEN_STRING,
            modifier = Modifier.semantics { contentDescription = higher },
        ) { Text("+") }
    }
}

/** The open-string pitches a custom Tuning can reach: A0 to E5 (MIDI note numbers). */
private const val LOWEST_OPEN_STRING = 21
private const val HIGHEST_OPEN_STRING = 76
