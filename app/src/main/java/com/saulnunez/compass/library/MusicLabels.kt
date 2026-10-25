package com.saulnunez.compass.library

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringArrayResource
import com.saulnunez.compass.R
import com.saulnunez.compass.domain.InstrumentKind
import com.saulnunez.compass.domain.KeySignature
import com.saulnunez.compass.domain.TuningPreset

/** The Instruments a new Song's first Track can use in v1. */
enum class InstrumentChoice(
    val kind: InstrumentKind,
    val stringCount: Int,
    @param:StringRes val label: Int,
    @param:StringRes val trackName: Int,
) {
    GUITAR_6(InstrumentKind.GUITAR, 6, R.string.instrument_guitar_6, R.string.track_name_guitar),
    BASS_4(InstrumentKind.BASS, 4, R.string.instrument_bass_4, R.string.track_name_bass),
    BASS_5(InstrumentKind.BASS, 5, R.string.instrument_bass_5, R.string.track_name_bass);

    val tuningPresets: List<TuningPreset> get() = TuningPreset.forInstrument(kind, stringCount)
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

/** Every key signature from 7 flats to 7 sharps, major keys first. */
val allKeySignatures: List<KeySignature> =
    listOf(false, true).flatMap { isMinor -> (-7..7).map { KeySignature(it, isMinor) } }

@Composable
fun keySignatureLabel(key: KeySignature): String {
    val names = stringArrayResource(if (key.isMinor) R.array.minor_keys else R.array.major_keys)
    return names[key.fifths + 7]
}
