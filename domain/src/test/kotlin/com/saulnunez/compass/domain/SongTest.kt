package com.saulnunez.compass.domain

import org.junit.Assert.assertThrows
import org.junit.Test

class SongTest {

    private val measure = Measure(
        timeSignature = TimeSignature(4, 4),
        keySignature = KeySignature.C_MAJOR,
        tempo = Tempo(120),
    )

    private val guitar = Instrument(
        kind = InstrumentKind.GUITAR,
        tuning = Tuning(listOf(64, 59, 55, 50, 45, 40).map(::Pitch)),
    )

    @Test
    fun `a Track that does not cover every Measure of the Song is rejected`() {
        val oneMeasureTrack = Track(
            name = "Guitar",
            instrument = guitar,
            voice = Voice(listOf(listOf(Rest(Duration.WHOLE)))),
        )

        assertThrows(IllegalArgumentException::class.java) {
            Song(
                id = SongId("two-measures"),
                title = "Two measures",
                artist = "Nobody",
                measures = listOf(measure, measure),
                tracks = listOf(oneMeasureTrack),
            )
        }
    }
}
