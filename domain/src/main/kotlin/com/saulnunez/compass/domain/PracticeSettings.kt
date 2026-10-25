package com.saulnunez.compass.domain

/**
 * How fast Playback goes compared with the Song's written Tempos, from 25% to 150%.
 * Part of the Practice settings, never of the Song.
 */
@JvmInline
value class TempoPercentage(val percent: Int) {
    init {
        require(percent in RANGE) { "Tempo percentage $percent% is outside $RANGE" }
    }

    /** The beats per minute Playback uses for a written [tempo]. */
    fun scale(tempo: Tempo): Double = tempo.beatsPerMinute * percent / 100.0

    companion object {
        val RANGE = 25..150
        val FULL = TempoPercentage(100)
    }
}

/**
 * A person's saved setup for practicing one Song (see GLOSSARY.md). Kept apart from the Song:
 * changing it never changes the Song.
 */
data class PracticeSettings(
    val tempoPercentage: TempoPercentage = TempoPercentage.FULL,
    val shownViews: ShownViews = ShownViews.TABLATURE,
    /** Whether a click sounds on every beat during Playback. */
    val click: Boolean = false,
    /** Whether one Measure of clicks plays before Playback starts. */
    val countIn: Boolean = false,
    /** The Measures Playback repeats, or null to play to the end of the Song. */
    val loop: Loop? = null,
    /** The index of the Track being read: the one the views show. */
    val readTrack: Int = 0,
)
