package com.saulnunez.compass.domain

import org.junit.Assert.assertThrows
import org.junit.Test

class TrackTest {

    private val fourStringBass = Instrument(
        kind = InstrumentKind.BASS,
        tuning = Tuning(listOf(43, 38, 33, 28).map(::Pitch)),
    )

    @Test
    fun `a Note on a string the Instrument does not have is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            Track(
                name = "Bass",
                instrument = fourStringBass,
                voice = Voice(listOf(listOf(Note(string = 5, fret = 0, duration = Duration.WHOLE)))),
            )
        }
    }

    @Test
    fun `a negative fret is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            Track(
                name = "Bass",
                instrument = fourStringBass,
                voice = Voice(listOf(listOf(Note(string = 1, fret = -1, duration = Duration.WHOLE)))),
            )
        }
    }
}
