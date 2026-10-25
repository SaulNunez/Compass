package com.saulnunez.compass.practice

import com.saulnunez.compass.domain.Duration
import com.saulnunez.compass.domain.Instrument
import com.saulnunez.compass.domain.InstrumentKind
import com.saulnunez.compass.domain.KeySignature
import com.saulnunez.compass.domain.Measure
import com.saulnunez.compass.domain.Note
import com.saulnunez.compass.domain.Pitch
import com.saulnunez.compass.domain.Rest
import com.saulnunez.compass.domain.Song
import com.saulnunez.compass.domain.SongId
import com.saulnunez.compass.domain.Tempo
import com.saulnunez.compass.domain.TimeSignature
import com.saulnunez.compass.domain.Track
import com.saulnunez.compass.domain.Tuning
import com.saulnunez.compass.domain.Voice

/** A sample Song for previews: an E minor pentatonic run on guitar. */
internal val demoSong: Song = run {
    fun eighth(string: Int, fret: Int) = Note(string, fret, Duration.EIGHTH)
    val measure = Measure(TimeSignature(4, 4), KeySignature(fifths = 1, isMinor = true), Tempo(100))

    Song(
        id = SongId("demo-e-minor-pentatonic"),
        title = "E minor pentatonic",
        artist = "Compass",
        measures = List(3) { measure },
        tracks = listOf(
            Track(
                name = "Guitar",
                instrument = Instrument(
                    kind = InstrumentKind.GUITAR,
                    tuning = Tuning(listOf(64, 59, 55, 50, 45, 40).map(::Pitch)),
                ),
                voice = Voice(
                    listOf(
                        listOf(
                            eighth(6, 0), eighth(6, 3), eighth(5, 0), eighth(5, 2),
                            eighth(4, 0), eighth(4, 2), eighth(3, 0), eighth(3, 2),
                        ),
                        listOf(eighth(2, 0), eighth(2, 3), eighth(1, 0), eighth(1, 3), Rest(Duration.HALF)),
                        listOf(Note(string = 6, fret = 0, duration = Duration.WHOLE)),
                    ),
                ),
            ),
        ),
    )
}
