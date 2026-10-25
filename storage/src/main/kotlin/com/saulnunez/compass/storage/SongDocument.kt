package com.saulnunez.compass.storage

import com.saulnunez.compass.domain.Duration
import com.saulnunez.compass.domain.Instrument
import com.saulnunez.compass.domain.InstrumentKind
import com.saulnunez.compass.domain.KeySignature
import com.saulnunez.compass.domain.Measure
import com.saulnunez.compass.domain.Note
import com.saulnunez.compass.domain.NoteValue
import com.saulnunez.compass.domain.Pitch
import com.saulnunez.compass.domain.Rest
import com.saulnunez.compass.domain.Song
import com.saulnunez.compass.domain.SongId
import com.saulnunez.compass.domain.Sound
import com.saulnunez.compass.domain.Tempo
import com.saulnunez.compass.domain.TimeSignature
import com.saulnunez.compass.domain.Track
import com.saulnunez.compass.domain.Tuning
import com.saulnunez.compass.domain.Voice
import com.saulnunez.compass.domain.VoiceElement
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * The on-disk shape of a Song (ADR-0002). Kept separate from the domain types, down to the
 * names of enum values, so the domain can change freely; any change to these classes or
 * their serial names needs a new format version.
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
    val instrument: InstrumentDocument,
    val openStrings: List<Int>,
    val measures: List<List<VoiceElementDocument>>,
    /** Since format version 3. */
    val sound: SoundDocument,
)

/** Since format version 3. */
@Serializable
internal enum class SoundDocument {
    @SerialName("clean-electric-guitar") CLEAN_ELECTRIC_GUITAR,
    @SerialName("overdriven-guitar") OVERDRIVEN_GUITAR,
    @SerialName("distorted-electric-guitar") DISTORTED_ELECTRIC_GUITAR,
    @SerialName("steel-acoustic-guitar") STEEL_ACOUSTIC_GUITAR,
    @SerialName("nylon-acoustic-guitar") NYLON_ACOUSTIC_GUITAR,
    @SerialName("fingered-bass") FINGERED_BASS,
    @SerialName("picked-bass") PICKED_BASS,
    @SerialName("slap-bass") SLAP_BASS,
    @SerialName("fretless-bass") FRETLESS_BASS,
}

@Serializable
internal enum class InstrumentDocument {
    @SerialName("guitar") GUITAR,
    @SerialName("bass") BASS,
}

/** Since format version 2: a note value plus its dot and triplet. Version 1 stored only the note value. */
@Serializable
internal data class DurationDocument(val value: NoteValueDocument, val dotted: Boolean, val triplet: Boolean)

@Serializable
internal enum class NoteValueDocument {
    @SerialName("whole") WHOLE,
    @SerialName("half") HALF,
    @SerialName("quarter") QUARTER,
    @SerialName("eighth") EIGHTH,
    @SerialName("sixteenth") SIXTEENTH,
    @SerialName("thirty-second") THIRTY_SECOND,
}

@Serializable
internal sealed interface VoiceElementDocument {
    val duration: DurationDocument
}

@Serializable
@SerialName("note")
internal data class NoteDocument(val string: Int, val fret: Int, override val duration: DurationDocument) : VoiceElementDocument

@Serializable
@SerialName("rest")
internal data class RestDocument(override val duration: DurationDocument) : VoiceElementDocument

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
            instrument = track.instrument.kind.toDocument(),
            openStrings = track.instrument.tuning.openStrings.map { it.midi },
            measures = track.voice.measures.map { elements -> elements.map { it.toDocument() } },
            sound = track.sound.toDocument(),
        )
    },
)

private fun VoiceElement.toDocument(): VoiceElementDocument = when (this) {
    is Note -> NoteDocument(string, fret, duration.toDocument())
    is Rest -> RestDocument(duration.toDocument())
}

private fun InstrumentKind.toDocument() = when (this) {
    InstrumentKind.GUITAR -> InstrumentDocument.GUITAR
    InstrumentKind.BASS -> InstrumentDocument.BASS
}

private fun Sound.toDocument() = when (this) {
    Sound.CLEAN_ELECTRIC_GUITAR -> SoundDocument.CLEAN_ELECTRIC_GUITAR
    Sound.OVERDRIVEN_GUITAR -> SoundDocument.OVERDRIVEN_GUITAR
    Sound.DISTORTED_ELECTRIC_GUITAR -> SoundDocument.DISTORTED_ELECTRIC_GUITAR
    Sound.STEEL_ACOUSTIC_GUITAR -> SoundDocument.STEEL_ACOUSTIC_GUITAR
    Sound.NYLON_ACOUSTIC_GUITAR -> SoundDocument.NYLON_ACOUSTIC_GUITAR
    Sound.FINGERED_BASS -> SoundDocument.FINGERED_BASS
    Sound.PICKED_BASS -> SoundDocument.PICKED_BASS
    Sound.SLAP_BASS -> SoundDocument.SLAP_BASS
    Sound.FRETLESS_BASS -> SoundDocument.FRETLESS_BASS
}

private fun Duration.toDocument() = DurationDocument(value.toDocument(), dotted = isDotted, triplet = isTriplet)

private fun NoteValue.toDocument() = when (this) {
    NoteValue.WHOLE -> NoteValueDocument.WHOLE
    NoteValue.HALF -> NoteValueDocument.HALF
    NoteValue.QUARTER -> NoteValueDocument.QUARTER
    NoteValue.EIGHTH -> NoteValueDocument.EIGHTH
    NoteValue.SIXTEENTH -> NoteValueDocument.SIXTEENTH
    NoteValue.THIRTY_SECOND -> NoteValueDocument.THIRTY_SECOND
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
            instrument = Instrument(track.instrument.toInstrumentKind(), Tuning(track.openStrings.map(::Pitch))),
            voice = Voice(track.measures.map { elements -> elements.map { it.toVoiceElement() } }),
            sound = track.sound.toSound(),
        )
    },
)

private fun VoiceElementDocument.toVoiceElement(): VoiceElement = when (this) {
    is NoteDocument -> Note(string, fret, duration.toDuration())
    is RestDocument -> Rest(duration.toDuration())
}

private fun InstrumentDocument.toInstrumentKind() = when (this) {
    InstrumentDocument.GUITAR -> InstrumentKind.GUITAR
    InstrumentDocument.BASS -> InstrumentKind.BASS
}

private fun SoundDocument.toSound() = when (this) {
    SoundDocument.CLEAN_ELECTRIC_GUITAR -> Sound.CLEAN_ELECTRIC_GUITAR
    SoundDocument.OVERDRIVEN_GUITAR -> Sound.OVERDRIVEN_GUITAR
    SoundDocument.DISTORTED_ELECTRIC_GUITAR -> Sound.DISTORTED_ELECTRIC_GUITAR
    SoundDocument.STEEL_ACOUSTIC_GUITAR -> Sound.STEEL_ACOUSTIC_GUITAR
    SoundDocument.NYLON_ACOUSTIC_GUITAR -> Sound.NYLON_ACOUSTIC_GUITAR
    SoundDocument.FINGERED_BASS -> Sound.FINGERED_BASS
    SoundDocument.PICKED_BASS -> Sound.PICKED_BASS
    SoundDocument.SLAP_BASS -> Sound.SLAP_BASS
    SoundDocument.FRETLESS_BASS -> Sound.FRETLESS_BASS
}

private fun DurationDocument.toDuration() = Duration(value.toNoteValue(), isDotted = dotted, isTriplet = triplet)

private fun NoteValueDocument.toNoteValue() = when (this) {
    NoteValueDocument.WHOLE -> NoteValue.WHOLE
    NoteValueDocument.HALF -> NoteValue.HALF
    NoteValueDocument.QUARTER -> NoteValue.QUARTER
    NoteValueDocument.EIGHTH -> NoteValue.EIGHTH
    NoteValueDocument.SIXTEENTH -> NoteValue.SIXTEENTH
    NoteValueDocument.THIRTY_SECOND -> NoteValue.THIRTY_SECOND
}

internal const val CURRENT_FORMAT_VERSION = 3
