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

data class Song(
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
