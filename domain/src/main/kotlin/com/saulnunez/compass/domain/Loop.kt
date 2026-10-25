package com.saulnunez.compass.domain

/**
 * A range of Measures that Playback repeats until stopped (see GLOSSARY.md). Part of the Practice
 * settings, never of the Song.
 */
sealed interface Loop {

    /** Loops the Section at index [section] of the Song (in Measure order), whatever Measures it spans. */
    data class OfSection(val section: Int) : Loop {
        init {
            require(section >= 0) { "No Section $section" }
        }
    }

    /** Loops Measures [first] to [last] (0-based, both included). */
    data class OfMeasures(val first: Int, val last: Int) : Loop {
        init {
            require(first in 0..last) { "A Loop must start at or before its last Measure" }
        }
    }
}

/** The Measures (0-based) this Loop repeats in [song], or null when the Song has no such Section or Measures. */
fun Loop.measuresIn(song: Song): IntRange? = when (this) {
    is Loop.OfSection -> song.sections.getOrNull(section)?.let { it.first..it.last }
    is Loop.OfMeasures -> (first..last).takeIf { last < song.measures.size }
}
