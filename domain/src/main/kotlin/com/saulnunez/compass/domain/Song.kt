package com.saulnunez.compass.domain

data class TimeSignature(val beats: Int, val beatUnit: Int)

/** Sharps (positive) or flats (negative) in the key, and whether it is a minor key. */
data class KeySignature(val fifths: Int, val isMinor: Boolean = false) {
    companion object {
        val C_MAJOR = KeySignature(0)
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

/** A Song's stable identity, kept across edits, Backups and Song files. */
@JvmInline
value class SongId(val value: String)

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
    tempo: Tempo,
    timeSignature: TimeSignature,
    keySignature: KeySignature,
    trackName: String,
    instrument: Instrument,
): Song = Song(
    id = id,
    title = title,
    artist = artist,
    measures = List(NEW_SONG_MEASURE_COUNT) { Measure(timeSignature, keySignature, tempo) },
    tracks = listOf(Track(trackName, instrument, Voice(List(NEW_SONG_MEASURE_COUNT) { emptyList() }))),
)
