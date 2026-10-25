package com.saulnunez.compass.domain

/**
 * Where the Measures and Sections of one Song went in an edited version of it: for each Measure
 * and each Section (by index) of the Song before, its index after, or null when it was deleted.
 * It lets Practice settings saved for the Song before follow it through the edits. Only the
 * domain reads it (see [Edit.restructure]).
 */
class Restructure internal constructor(internal val measures: List<Int?>, internal val sections: List<Int?>) {

    internal fun copy(measures: List<Int?> = this.measures, sections: List<Int?> = this.sections) = Restructure(measures, sections)

    /** This, followed by [next] (a Restructure of the Song this one leads to). */
    internal fun then(next: Restructure) = Restructure(
        measures = measures.map { it?.let(next.measures::get) },
        sections = sections.map { it?.let(next.sections::get) },
    )

    /** [loop], saved for the Song before, as it applies to the Song after; null when nothing of it remains. */
    internal fun reconcile(loop: Loop): Loop? = when (loop) {
        is Loop.OfSection -> sections.getOrNull(loop.section)?.let { Loop.OfSection(it) }
        // Shifts with what moves before it, takes in Measures inserted inside it, and shrinks as its Measures go.
        is Loop.OfMeasures -> (loop.first..loop.last).mapNotNull { measures.getOrNull(it) }
            .takeIf { it.isNotEmpty() }
            ?.let { Loop.OfMeasures(it.min(), it.max()) }
    }

    companion object {
        /** Nothing in [song] moves. */
        internal fun none(song: Song) = Restructure(song.measures.indices.toList(), song.sections.indices.toList())
    }
}

/** [count] Measures inserted into [song] before Measure [at]. */
internal fun measuresInserted(song: Song, at: Int, count: Int) = Restructure.none(song).copy(
    measures = song.measures.indices.map { if (it < at) it else it + count },
)

/** Measure [measure] deleted from [song], with any Section that was only that Measure. */
internal fun measureDeleted(song: Song, measure: Int): Restructure {
    val removed = song.sections.map { it.first == measure && it.last == measure }
    return Restructure(
        measures = removedAt(song.measures.size, measure),
        sections = song.sections.indices.map { index -> if (removed[index]) null else index - removed.take(index).count { it } },
    )
}

/** [section] added to [song]: Sections stay in Measure order, so it goes in before every Section after it. */
internal fun sectionAdded(song: Song, section: Section) = Restructure.none(song).copy(
    sections = song.sections.mapIndexed { index, it -> if (it.first < section.first) index else index + 1 },
)

/** The Section at index [section] removed from [song]. */
internal fun sectionRemoved(song: Song, section: Int) = Restructure.none(song).copy(
    sections = removedAt(song.sections.size, section),
)

/** Where each of [size] indices goes when the one at [removed] is taken out: those after it move up one. */
private fun removedAt(size: Int, removed: Int): List<Int?> =
    List(size) { if (it < removed) it else if (it == removed) null else it - 1 }
