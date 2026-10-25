package com.saulnunez.compass.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.saulnunez.compass.R
import com.saulnunez.compass.domain.KeySignature
import com.saulnunez.compass.domain.Song
import com.saulnunez.compass.domain.SongId
import com.saulnunez.compass.domain.Tempo
import com.saulnunez.compass.domain.TimeSignature
import com.saulnunez.compass.domain.newSong
import com.saulnunez.compass.ui.theme.CompassTheme
import java.util.UUID

/**
 * Asks for what a new Song needs and hands the created Song to [onCreate].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSongScreen(onCreate: (Song) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var title by rememberSaveable { mutableStateOf("") }
    var artist by rememberSaveable { mutableStateOf("") }
    var tempoText by rememberSaveable { mutableStateOf("120") }
    var beatsText by rememberSaveable { mutableStateOf("4") }
    var beatUnit by rememberSaveable { mutableIntStateOf(4) }
    var keyIndex by rememberSaveable { mutableIntStateOf(allKeySignatures.indexOf(KeySignature.C_MAJOR)) }
    var instrument by rememberSaveable { mutableStateOf(InstrumentChoice.GUITAR_6) }
    var tuning by rememberSaveable { mutableStateOf(instrument.tuningPresets.first()) }

    val tempo = tempoText.toIntOrNull()?.takeIf { it in TEMPO_RANGE }
    val beats = beatsText.toIntOrNull()?.takeIf { it in BEATS_RANGE }
    val trackName = stringResource(instrument.trackName)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.new_song_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.back_to_library)) } },
            )
        },
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.field_title)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = artist,
                onValueChange = { artist = it },
                label = { Text(stringResource(R.string.field_artist)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            NumberField(R.string.field_tempo, tempoText, isError = tempo == null) { tempoText = it }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                options = allKeySignatures,
                selected = allKeySignatures[keyIndex],
                optionLabel = { keySignatureLabel(it) },
                onSelect = { keyIndex = allKeySignatures.indexOf(it) },
            )
            Dropdown(
                label = stringResource(R.string.field_instrument),
                options = InstrumentChoice.entries,
                selected = instrument,
                optionLabel = { stringResource(it.label) },
                onSelect = {
                    instrument = it
                    tuning = it.tuningPresets.first()
                },
            )
            Dropdown(
                label = stringResource(R.string.field_tuning),
                options = instrument.tuningPresets,
                selected = tuning,
                optionLabel = { stringResource(it.label()) },
                onSelect = { tuning = it },
            )
            Button(
                enabled = title.isNotBlank() && tempo != null && beats != null,
                onClick = {
                    onCreate(
                        newSong(
                            id = SongId(UUID.randomUUID().toString()),
                            title = title.trim(),
                            artist = artist.trim(),
                            tempo = Tempo(tempo!!),
                            timeSignature = TimeSignature(beats!!, beatUnit),
                            keySignature = allKeySignatures[keyIndex],
                            trackName = trackName,
                            instrument = tuning.instrument(),
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.create)) }
        }
    }
}

@Composable
private fun NumberField(
    label: Int,
    value: String,
    isError: Boolean,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter(Char::isDigit).take(3)) },
        label = { Text(stringResource(label)) },
        isError = isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Dropdown(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

private val TEMPO_RANGE = 20..300
private val BEATS_RANGE = 1..32
private val BEAT_UNITS = listOf(2, 4, 8, 16)

@Preview(showBackground = true)
@Composable
private fun NewSongScreenPreview() {
    CompassTheme {
        NewSongScreen(onCreate = {}, onBack = {})
    }
}
