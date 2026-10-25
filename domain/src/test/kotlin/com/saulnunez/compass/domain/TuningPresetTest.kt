package com.saulnunez.compass.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TuningPresetTest {

    private fun openStrings(preset: TuningPreset) = preset.tuning.openStrings.map { it.midi }

    @Test
    fun `6-string guitar offers Standard, Drop D, Eb, D standard, Drop C and DADGAD`() {
        assertEquals(
            listOf(
                TuningPreset.GUITAR_STANDARD,
                TuningPreset.GUITAR_DROP_D,
                TuningPreset.GUITAR_E_FLAT,
                TuningPreset.GUITAR_D_STANDARD,
                TuningPreset.GUITAR_DROP_C,
                TuningPreset.GUITAR_DADGAD,
            ),
            SupportedInstrument.GUITAR_6.tuningPresets,
        )
    }

    @Test
    fun `4-string bass offers Standard, Drop D and Eb`() {
        assertEquals(
            listOf(TuningPreset.BASS_STANDARD, TuningPreset.BASS_DROP_D, TuningPreset.BASS_E_FLAT),
            SupportedInstrument.BASS_4.tuningPresets,
        )
    }

    @Test
    fun `5-string bass offers B-E-A-D-G`() {
        assertEquals(
            listOf(TuningPreset.BASS_FIVE_STRING_STANDARD),
            SupportedInstrument.BASS_5.tuningPresets,
        )
    }

    @Test
    fun `guitar Tunings have the expected open strings from high to low`() {
        assertEquals(listOf(64, 59, 55, 50, 45, 40), openStrings(TuningPreset.GUITAR_STANDARD)) // E4 B3 G3 D3 A2 E2
        assertEquals(listOf(64, 59, 55, 50, 45, 38), openStrings(TuningPreset.GUITAR_DROP_D)) // low string D2
        assertEquals(listOf(63, 58, 54, 49, 44, 39), openStrings(TuningPreset.GUITAR_E_FLAT)) // all half a step down
        assertEquals(listOf(62, 57, 53, 48, 43, 38), openStrings(TuningPreset.GUITAR_D_STANDARD)) // D4 A3 F3 C3 G2 D2
        assertEquals(listOf(62, 57, 53, 48, 43, 36), openStrings(TuningPreset.GUITAR_DROP_C)) // low string C2
        assertEquals(listOf(62, 57, 55, 50, 45, 38), openStrings(TuningPreset.GUITAR_DADGAD)) // D4 A3 G3 D3 A2 D2
    }

    @Test
    fun `bass Tunings have the expected open strings from high to low`() {
        assertEquals(listOf(43, 38, 33, 28), openStrings(TuningPreset.BASS_STANDARD)) // G2 D2 A1 E1
        assertEquals(listOf(43, 38, 33, 26), openStrings(TuningPreset.BASS_DROP_D)) // low string D1
        assertEquals(listOf(42, 37, 32, 27), openStrings(TuningPreset.BASS_E_FLAT))
        assertEquals(listOf(43, 38, 33, 28, 23), openStrings(TuningPreset.BASS_FIVE_STRING_STANDARD)) // low string B0
    }
}
