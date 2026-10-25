package com.saulnunez.compass.domain

/** The synthesized timbre a Track plays with; each suits one kind of Instrument. */
enum class Sound(val kind: InstrumentKind) {
    CLEAN_ELECTRIC_GUITAR(InstrumentKind.GUITAR),
    OVERDRIVEN_GUITAR(InstrumentKind.GUITAR),
    DISTORTED_ELECTRIC_GUITAR(InstrumentKind.GUITAR),
    STEEL_ACOUSTIC_GUITAR(InstrumentKind.GUITAR),
    NYLON_ACOUSTIC_GUITAR(InstrumentKind.GUITAR),
    FINGERED_BASS(InstrumentKind.BASS),
    PICKED_BASS(InstrumentKind.BASS),
    SLAP_BASS(InstrumentKind.BASS),
    FRETLESS_BASS(InstrumentKind.BASS);

    companion object {
        /** The Sounds a Track on an Instrument of [kind] can use, the default first. */
        fun choicesFor(kind: InstrumentKind): List<Sound> = entries.filter { it.kind == kind }

        /** The Sound a Track gets unless another is chosen: clean electric guitar or fingered bass. */
        fun defaultFor(kind: InstrumentKind): Sound = choicesFor(kind).first()
    }
}
