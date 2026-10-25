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
        if (formatVersion < 4) document = document.upgradeFromVersion3()
        if (formatVersion < 5) document = document.upgradeFromVersion4()
        return songJson.decodeFromJsonElement(SongDocument.serializer(), document).toSong()
    }
}

/** Version 4 gave Notes a tie and Techniques (and added Chords): a version 3 Note has neither. */
private fun JsonObject.upgradeFromVersion3(): JsonObject = withEachElement { element ->
    if (element["type"]?.jsonPrimitive?.content != "note") return@withEachElement element
    JsonObject(element + mapOf("tied" to JsonPrimitive(false), "techniques" to JsonArray(emptyList())))
}.withFormatVersion(4)

/** Version 5 gave each Track a Sound: a version 4 Track plays with its Instrument's default Sound. */
private fun JsonObject.upgradeFromVersion4(): JsonObject {
    val tracks = getValue("tracks").jsonArray.map { track ->
        val sound = when (track.jsonObject.getValue("instrument").jsonPrimitive.content) {
            "bass" -> "fingered-bass"
            else -> "clean-electric-guitar"
        }
        JsonObject(track.jsonObject + ("sound" to JsonPrimitive(sound)))
    }
    return JsonObject(this + ("tracks" to JsonArray(tracks))).withFormatVersion(5)
}

class UnsupportedSongFormatException(val formatVersion: Int) :
    IllegalArgumentException("Song format version $formatVersion is not supported by this version of Compass")

/** Version 2 gave Durations a dot and a triplet: a version 1 duration is the bare note value. */
private fun JsonObject.upgradeFromVersion1(): JsonObject = withEachElement { it.withVersion2Duration() }.withFormatVersion(2)

private fun JsonObject.withFormatVersion(version: Int): JsonObject = JsonObject(this + ("formatVersion" to JsonPrimitive(version)))

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

/** Version 3 added Sections: a version 2 Song has none. */
private fun JsonObject.upgradeFromVersion2(): JsonObject =
    JsonObject(this + mapOf("formatVersion" to JsonPrimitive(3), "sections" to JsonArray(emptyList())))

private val songJson = Json
