package com.saulnunez.compass.domain

data class TimeSignature(val beats: Int, val beatUnit: Int)

/** Sharps (positive) or flats (negative) in the key, and whether it is a minor key. */
data class KeySignature(val fifths: Int, val isMinor: Boolean = false) {
    companion object {
        val C_MAJOR = KeySignature(0)

        /** Every key signature from 7 flats to 7 sharps, major keys first. */
        val ALL: List<KeySignature> = listOf(false, true).flatMap { isMinor -> (-7..7).map { KeySignature(it, isMinor) } }
    }
}

@JvmInline
value class Tempo(val beatsPerMinute: Int)

/** A span of a Song shared by every Track. */
data class Measure(
    val timeSignature: TimeSignature,
    val keySignature: KeySignature,
    val tempo: Tempo,
)

/**
 * A Song's stable identity, kept across edits, Backups and Song files.
 * Limited to letters, digits, '-' and '_' because it is also used as a file name.
 */
@JvmInline
value class SongId(val value: String) {
    init {
        require(value.matches(SAFE_ID)) { "Not a valid SongId: '$value'" }
    }
}

private val SAFE_ID = Regex("[A-Za-z0-9_-]{1,64}")

data class Song(
    val id: SongId,
    val title: String,
    val artist: String,
    val measures: List<Measure>,
    val tracks: List<Track>,
) {
    init {
        tracks.forEach { track ->
            require(track.voice.measures.size == measures.size) {
                "Track '${track.name}' has ${track.voice.measures.size} Measures but the Song has ${measures.size}"
            }
        }
    }
}

/** Measures a new Song starts with, so there is room to write straight away. */
const val NEW_SONG_MEASURE_COUNT = 4

fun newSong(
    id: SongId,
    title: String,
    artist: String,
    startingMeasure: Measure,
    trackName: String,
    instrument: Instrument,
): Song = Song(
    id = id,
    title = title,
    artist = artist,
    measures = List(NEW_SONG_MEASURE_COUNT) { startingMeasure },
    tracks = listOf(Track(trackName, instrument, Voice(List(NEW_SONG_MEASURE_COUNT) { emptyList() }))),
)
