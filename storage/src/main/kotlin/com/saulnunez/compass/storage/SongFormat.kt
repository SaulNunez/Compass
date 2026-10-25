package com.saulnunez.compass.storage

import com.saulnunez.compass.domain.Song
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Compass's own document format for a Song: used for the Library, Song files and Backups. */
object SongFormat {

    fun encode(song: Song): String = json.encodeToString(SongDocument.serializer(), song.toDocument())

    fun decode(text: String): Song {
        val document = json.parseToJsonElement(text).jsonObject
        val formatVersion = document.getValue("formatVersion").jsonPrimitive.int
        // When the format changes, upgrade older versions here before decoding.
        if (formatVersion != CURRENT_FORMAT_VERSION) throw UnsupportedSongFormatException(formatVersion)
        return json.decodeFromJsonElement(SongDocument.serializer(), document).toSong()
    }
}

class UnsupportedSongFormatException(val formatVersion: Int) :
    IllegalArgumentException("Song format version $formatVersion is not supported by this version of Compass")

internal val json = Json
