package com.saulnunez.compass.storage

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
)

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
        ),
    )

    /**
     * Practice settings are only a convenience, so anything unreadable (damaged, or from a newer
     * Compass) falls back to the defaults rather than stopping the Song from opening.
     */
    fun decodeOrDefault(text: String): PracticeSettings = runCatching {
        val document = json.decodeFromString(PracticeSettingsDocument.serializer(), text)
        check(document.formatVersion == PRACTICE_SETTINGS_FORMAT_VERSION)
        PracticeSettings(
            tempoPercentage = TempoPercentage(document.tempoPercentage.coerceIn(TempoPercentage.RANGE)),
            shownViews = when (document.shownViews) {
                ShownViewsDocument.TABLATURE -> ShownViews.TABLATURE
                ShownViewsDocument.STANDARD_NOTATION -> ShownViews.STANDARD_NOTATION
                ShownViewsDocument.BOTH -> ShownViews.BOTH
            },
            click = document.click,
            countIn = document.countIn,
        )
    }.getOrDefault(PracticeSettings())
}

private const val PRACTICE_SETTINGS_FORMAT_VERSION = 1

private val json = Json { encodeDefaults = true }
