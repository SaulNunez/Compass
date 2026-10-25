package com.saulnunez.compass.storage

import com.saulnunez.compass.domain.Loop
import com.saulnunez.compass.domain.PracticeSettings
import com.saulnunez.compass.domain.ShownViews
import com.saulnunez.compass.domain.TempoPercentage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/*
 * The on-disk shape of a Song's Practice settings, kept in the Song's folder beside (never inside)
 * its document (ADR-0002). Separate from the domain types for the same reason as SongDocument.
 */

@Serializable
private data class PracticeSettingsDocument(
    val formatVersion: Int = PRACTICE_SETTINGS_FORMAT_VERSION,
    val tempoPercentage: Int,
    val shownViews: ShownViewsDocument,
    val click: Boolean,
    val countIn: Boolean,
    /** Added in version 2; version 1 has no Loop. */
    val loop: LoopDocument? = null,
    /**
     * Recorded from version 4. Versions 1-3 may or may not carry it (it was first written without a
     * version step); when they don't, the first Track is read.
     */
    val readTrack: Int? = null,
    /** Added in version 3, as Track indices; earlier versions have every Track sounding. */
    val mutedTracks: List<Int> = emptyList(),
    val soloedTracks: List<Int> = emptyList(),
)

/** A Section Loop has [section]; a Measure-range Loop has [first] and [last] (all 0-based). */
@Serializable
private data class LoopDocument(val section: Int? = null, val first: Int? = null, val last: Int? = null)

@Serializable
private enum class ShownViewsDocument {
    @SerialName("tablature") TABLATURE,
    @SerialName("standard-notation") STANDARD_NOTATION,
    @SerialName("both") BOTH,
}

internal object PracticeSettingsFormat {

    fun encode(settings: PracticeSettings): String = json.encodeToString(
        PracticeSettingsDocument.serializer(),
        PracticeSettingsDocument(
            tempoPercentage = settings.tempoPercentage.percent,
            shownViews = when (settings.shownViews) {
                ShownViews.TABLATURE -> ShownViewsDocument.TABLATURE
                ShownViews.STANDARD_NOTATION -> ShownViewsDocument.STANDARD_NOTATION
                ShownViews.BOTH -> ShownViewsDocument.BOTH
            },
            click = settings.click,
            countIn = settings.countIn,
            loop = when (val loop = settings.loop) {
                null -> null
                is Loop.OfSection -> LoopDocument(section = loop.section)
                is Loop.OfMeasures -> LoopDocument(first = loop.first, last = loop.last)
            },
            readTrack = settings.readTrack,
            mutedTracks = settings.mutedTracks.sorted(),
            soloedTracks = settings.soloedTracks.sorted(),
        ),
    )

    /**
     * Practice settings are only a convenience, so anything unreadable (damaged, or from a newer
     * Compass) falls back to the defaults rather than stopping the Song from opening.
     */
    fun decodeOrDefault(text: String): PracticeSettings = runCatching {
        val document = json.decodeFromString(PracticeSettingsDocument.serializer(), text)
        check(document.formatVersion in 1..PRACTICE_SETTINGS_FORMAT_VERSION)
        check(document.formatVersion < READ_TRACK_VERSION || document.readTrack != null)
        PracticeSettings(
            tempoPercentage = TempoPercentage(document.tempoPercentage.coerceIn(TempoPercentage.RANGE)),
            shownViews = when (document.shownViews) {
                ShownViewsDocument.TABLATURE -> ShownViews.TABLATURE
                ShownViewsDocument.STANDARD_NOTATION -> ShownViews.STANDARD_NOTATION
                ShownViewsDocument.BOTH -> ShownViews.BOTH
            },
            click = document.click,
            countIn = document.countIn,
            loop = document.loop?.toLoop(),
            readTrack = (document.readTrack ?: 0).coerceAtLeast(0),
            mutedTracks = document.mutedTracks.filter { it >= 0 }.toSet(),
            soloedTracks = document.soloedTracks.filter { it >= 0 }.toSet(),
        )
    }.getOrDefault(PracticeSettings())
}

/** A Loop that can't be read is dropped, keeping the rest of the Practice settings. */
private fun LoopDocument.toLoop(): Loop? = runCatching {
    when {
        section != null -> Loop.OfSection(section)
        first != null && last != null -> Loop.OfMeasures(first, last)
        else -> null
    }
}.getOrNull()

/**
 * Version 2 adds the Loop; version 3 the muted and soloed Tracks; version 4 always records the Track
 * being read.
 */
private const val PRACTICE_SETTINGS_FORMAT_VERSION = 4

private const val READ_TRACK_VERSION = 4

private val json = Json { encodeDefaults = true }
