package com.saulnunez.compass.storage

import com.saulnunez.compass.domain.Song
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Compass's own versioned document format for a Song (ADR-0002). */
object SongFormat {

    fun encode(song: Song): String = songJson.encodeToString(SongDocument.serializer(), song.toDocument())

    fun decode(text: String): Song {
        val document = songJson.parseToJsonElement(text).jsonObject
        val formatVersion = document.getValue("formatVersion").jsonPrimitive.int
        // When the format changes, upgrade older versions here before decoding.
        if (formatVersion != CURRENT_FORMAT_VERSION) throw UnsupportedSongFormatException(formatVersion)
        return songJson.decodeFromJsonElement(SongDocument.serializer(), document).toSong()
    }
}

class UnsupportedSongFormatException(val formatVersion: Int) :
    IllegalArgumentException("Song format version $formatVersion is not supported by this version of Compass")

private val songJson = Json
