package com.saulnunez.compass.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class InstrumentTest {

    // Standard guitar tuning, string 1 (high E4) to string 6 (low E2), as MIDI note numbers.
    private val standardGuitar = Instrument(
        kind = InstrumentKind.GUITAR,
        tuning = Tuning(listOf(64, 59, 55, 50, 45, 40).map(::Pitch)),
    )

    @Test
    fun `open high E string sounds E4`() {
        assertEquals(Pitch(64), standardGuitar.pitchOf(Note(string = 1, fret = 0, duration = Duration.QUARTER)))
    }

    @Test
    fun `fifth fret on the low E string sounds A2`() {
        assertEquals(Pitch(45), standardGuitar.pitchOf(Note(string = 6, fret = 5, duration = Duration.QUARTER)))
    }
}
