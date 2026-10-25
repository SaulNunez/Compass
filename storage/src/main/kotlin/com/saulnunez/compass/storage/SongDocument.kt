package com.saulnunez.compass.storage

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
import com.saulnunez.compass.domain.VoiceElement
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * The on-disk shape of a Song (ADR-0002). Kept separate from the domain types so the
 * domain can change freely; any change to these classes needs a new format version.
 */

@Serializable
internal data class SongDocument(
    val formatVersion: Int,
    val id: String,
    val title: String,
    val artist: String,
    val measures: List<MeasureDocument>,
    val tracks: List<TrackDocument>,
)

@Serializable
internal data class MeasureDocument(
    val beats: Int,
    val beatUnit: Int,
    val keyFifths: Int,
    val keyIsMinor: Boolean,
    val tempo: Int,
)

@Serializable
internal data class TrackDocument(
    val name: String,
    val instrument: InstrumentKind,
    val openStrings: List<Int>,
    val measures: List<List<VoiceElementDocument>>,
)

@Serializable
internal sealed interface VoiceElementDocument {
    val duration: Duration
}

@Serializable
@SerialName("note")
internal data class NoteDocument(val string: Int, val fret: Int, override val duration: Duration) : VoiceElementDocument

@Serializable
@SerialName("rest")
internal data class RestDocument(override val duration: Duration) : VoiceElementDocument

internal fun Song.toDocument() = SongDocument(
    formatVersion = CURRENT_FORMAT_VERSION,
    id = id.value,
    title = title,
    artist = artist,
    measures = measures.map {
        MeasureDocument(
            beats = it.timeSignature.beats,
            beatUnit = it.timeSignature.beatUnit,
            keyFifths = it.keySignature.fifths,
            keyIsMinor = it.keySignature.isMinor,
            tempo = it.tempo.beatsPerMinute,
        )
    },
    tracks = tracks.map { track ->
        TrackDocument(
            name = track.name,
            instrument = track.instrument.kind,
            openStrings = track.instrument.tuning.openStrings.map { it.midi },
            measures = track.voice.measures.map { elements -> elements.map { it.toDocument() } },
        )
    },
)

private fun VoiceElement.toDocument(): VoiceElementDocument = when (this) {
    is Note -> NoteDocument(string, fret, duration)
    is Rest -> RestDocument(duration)
}

internal fun SongDocument.toSong() = Song(
    id = SongId(id),
    title = title,
    artist = artist,
    measures = measures.map {
        Measure(
            timeSignature = TimeSignature(it.beats, it.beatUnit),
            keySignature = KeySignature(it.keyFifths, it.keyIsMinor),
            tempo = Tempo(it.tempo),
        )
    },
    tracks = tracks.map { track ->
        Track(
            name = track.name,
            instrument = Instrument(track.instrument, Tuning(track.openStrings.map(::Pitch))),
            voice = Voice(track.measures.map { elements -> elements.map { it.toVoiceElement() } }),
        )
    },
)

private fun VoiceElementDocument.toVoiceElement(): VoiceElement = when (this) {
    is NoteDocument -> Note(string, fret, duration)
    is RestDocument -> Rest(duration)
}

internal const val CURRENT_FORMAT_VERSION = 1
