package com.saulnunez.compass.domain

/** A ready-made Tuning offered when choosing an Instrument, open strings listed from string 1 (highest). */
enum class TuningPreset(val kind: InstrumentKind, openStrings: List<Int>) {
    GUITAR_STANDARD(InstrumentKind.GUITAR, listOf(64, 59, 55, 50, 45, 40)),
    GUITAR_DROP_D(InstrumentKind.GUITAR, listOf(64, 59, 55, 50, 45, 38)),
    GUITAR_E_FLAT(InstrumentKind.GUITAR, listOf(63, 58, 54, 49, 44, 39)),
    GUITAR_D_STANDARD(InstrumentKind.GUITAR, listOf(62, 57, 53, 48, 43, 38)),
    GUITAR_DROP_C(InstrumentKind.GUITAR, listOf(62, 57, 53, 48, 43, 36)),
    GUITAR_DADGAD(InstrumentKind.GUITAR, listOf(62, 57, 55, 50, 45, 38)),
    BASS_STANDARD(InstrumentKind.BASS, listOf(43, 38, 33, 28)),
    BASS_DROP_D(InstrumentKind.BASS, listOf(43, 38, 33, 26)),
    BASS_E_FLAT(InstrumentKind.BASS, listOf(42, 37, 32, 27)),
    BASS_FIVE_STRING_STANDARD(InstrumentKind.BASS, listOf(43, 38, 33, 28, 23));

    val tuning: Tuning = Tuning(openStrings.map(::Pitch))

    fun instrument(): Instrument = Instrument(kind, tuning)

    companion object {
        /** The presets for one Instrument, in the order they are offered. */
        fun forInstrument(kind: InstrumentKind, stringCount: Int): List<TuningPreset> =
            entries.filter { it.kind == kind && it.tuning.stringCount == stringCount }
    }
}
