package com.saulnunez.compass.library

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import com.saulnunez.compass.R
import com.saulnunez.compass.domain.KeySignature
import com.saulnunez.compass.domain.Pitch
import com.saulnunez.compass.domain.Sound
import com.saulnunez.compass.domain.SupportedInstrument
import com.saulnunez.compass.domain.Technique
import com.saulnunez.compass.domain.TuningPreset

@StringRes
fun SupportedInstrument.label(): Int = when (this) {
    SupportedInstrument.GUITAR_6 -> R.string.instrument_guitar_6
    SupportedInstrument.BASS_4 -> R.string.instrument_bass_4
    SupportedInstrument.BASS_5 -> R.string.instrument_bass_5
}

/** The name a new Song's first Track gets on this Instrument. */
@StringRes
fun SupportedInstrument.defaultTrackName(): Int = when (this) {
    SupportedInstrument.GUITAR_6 -> R.string.track_name_guitar
    SupportedInstrument.BASS_4, SupportedInstrument.BASS_5 -> R.string.track_name_bass
}

@StringRes
fun TuningPreset.label(): Int = when (this) {
    TuningPreset.GUITAR_STANDARD -> R.string.tuning_guitar_standard
    TuningPreset.GUITAR_DROP_D -> R.string.tuning_guitar_drop_d
    TuningPreset.GUITAR_E_FLAT -> R.string.tuning_guitar_e_flat
    TuningPreset.GUITAR_D_STANDARD -> R.string.tuning_guitar_d_standard
    TuningPreset.GUITAR_DROP_C -> R.string.tuning_guitar_drop_c
    TuningPreset.GUITAR_DADGAD -> R.string.tuning_guitar_dadgad
    TuningPreset.BASS_STANDARD -> R.string.tuning_bass_standard
    TuningPreset.BASS_DROP_D -> R.string.tuning_bass_drop_d
    TuningPreset.BASS_E_FLAT -> R.string.tuning_bass_e_flat
    TuningPreset.BASS_FIVE_STRING_STANDARD -> R.string.tuning_bass_five_string_standard
}

@Composable
fun keySignatureLabel(key: KeySignature): String {
    val names = stringArrayResource(if (key.isMinor) R.array.minor_keys else R.array.major_keys)
    return names[key.fifths + 7]
}

@StringRes
fun Sound.label(): Int = when (this) {
    Sound.CLEAN_ELECTRIC_GUITAR -> R.string.sound_clean_electric_guitar
    Sound.OVERDRIVEN_GUITAR -> R.string.sound_overdriven_guitar
    Sound.DISTORTED_ELECTRIC_GUITAR -> R.string.sound_distorted_electric_guitar
    Sound.STEEL_ACOUSTIC_GUITAR -> R.string.sound_steel_acoustic_guitar
    Sound.NYLON_ACOUSTIC_GUITAR -> R.string.sound_nylon_acoustic_guitar
    Sound.FINGERED_BASS -> R.string.sound_fingered_bass
    Sound.PICKED_BASS -> R.string.sound_picked_bass
    Sound.SLAP_BASS -> R.string.sound_slap_bass
    Sound.FRETLESS_BASS -> R.string.sound_fretless_bass
}

@StringRes
fun Technique.label(): Int = when (this) {
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

/** A pitch's note name and octave, e.g. E2 for the low string of a standard-tuned guitar. */
@Composable
fun pitchLabel(pitch: Pitch): String {
    val names = stringArrayResource(R.array.pitch_classes)
    return stringResource(R.string.pitch_name, names[pitch.midi % 12], pitch.midi / 12 - 1)
}
