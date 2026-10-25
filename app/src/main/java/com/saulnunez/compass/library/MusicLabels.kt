package com.saulnunez.compass.library

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import com.saulnunez.compass.R
import com.saulnunez.compass.domain.KeySignature
import com.saulnunez.compass.domain.SupportedInstrument
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
