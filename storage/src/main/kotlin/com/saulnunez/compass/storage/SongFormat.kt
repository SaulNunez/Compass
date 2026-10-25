package com.saulnunez.compass.storage

import com.saulnunez.compass.domain.Song
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** Compass's own versioned document format for a Song (ADR-0002). */
object SongFormat {

    fun encode(song: Song): String = songJson.encodeToString(SongDocument.serializer(), song.toDocument())

    fun decode(text: String): Song {
        var document = songJson.parseToJsonElement(text).jsonObject
        val formatVersion = document.getValue("formatVersion").jsonPrimitive.int
        if (formatVersion !in 1..CURRENT_FORMAT_VERSION) throw UnsupportedSongFormatException(formatVersion)
        // Upgrade older versions one step at a time before decoding.
        if (formatVersion < 2) document = document.upgradeFromVersion1()
        if (formatVersion < 3) document = document.upgradeFromVersion2()
        return songJson.decodeFromJsonElement(SongDocument.serializer(), document).toSong()
    }
}

/** Version 3 gave Notes a tie and Techniques (and added Chords): a version 2 Note has neither. */
private fun JsonObject.upgradeFromVersion2(): JsonObject = withEachElement { element ->
    if (element["type"]?.jsonPrimitive?.content != "note") return@withEachElement element
    JsonObject(element + mapOf("tied" to JsonPrimitive(false), "techniques" to JsonArray(emptyList())))
}

class UnsupportedSongFormatException(val formatVersion: Int) :
    IllegalArgumentException("Song format version $formatVersion is not supported by this version of Compass")

/** Version 2 gave Durations a dot and a triplet: a version 1 duration is the bare note value. */
private fun JsonObject.upgradeFromVersion1(): JsonObject = withEachElement { it.withVersion2Duration() }

/** This document with [change] applied to every Note and Rest of every Track. */
private fun JsonObject.withEachElement(change: (JsonObject) -> JsonObject): JsonObject {
    val tracks = getValue("tracks").jsonArray.map { track ->
        val measures = track.jsonObject.getValue("measures").jsonArray.map { elements ->
            JsonArray(elements.jsonArray.map { change(it.jsonObject) })
        }
        JsonObject(track.jsonObject + ("measures" to JsonArray(measures)))
    }
    return JsonObject(this + ("tracks" to JsonArray(tracks)))
}

private fun JsonObject.withVersion2Duration(): JsonObject {
    val duration: JsonElement = JsonObject(
        mapOf("value" to getValue("duration"), "dotted" to JsonPrimitive(false), "triplet" to JsonPrimitive(false)),
    )
    return JsonObject(this + ("duration" to duration))
}

private val songJson = Json
