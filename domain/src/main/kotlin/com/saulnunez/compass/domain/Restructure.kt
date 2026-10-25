package com.saulnunez.compass.domain

/**
 * Where the Measures and Sections of one Song went in an edited version of it: for each Measure
 * and each Section (by index) of the Song before, its index after, or null when it was deleted.
 * It lets Practice settings saved for the Song before follow it through the edits.
 */
internal data class Restructure(val measures: List<Int?>, val sections: List<Int?>) {

    /** This, followed by [next] (a Restructure of the Song this one leads to). */
    fun then(next: Restructure) = Restructure(
        measures = measures.map { it?.let(next.measures::get) },
        sections = sections.map { it?.let(next.sections::get) },
    )

    /** [loop], saved for the Song before, as it applies to the Song after; null when nothing of it remains. */
    fun reconcile(loop: Loop): Loop? = when (loop) {
        is Loop.OfSection -> sections.getOrNull(loop.section)?.let { Loop.OfSection(it) }
        // Shifts with what moves before it, takes in Measures inserted inside it, and shrinks as its Measures go.
        is Loop.OfMeasures -> (loop.first..loop.last).mapNotNull { measures.getOrNull(it) }
            .takeIf { it.isNotEmpty() }
            ?.let { Loop.OfMeasures(it.min(), it.max()) }
    }

    companion object {
        /** Nothing in [song] moves. */
        fun none(song: Song) = Restructure(song.measures.indices.toList(), song.sections.indices.toList())
    }
}

/** Where this edit moves the Measures and Sections of [before], the Song it is applied to. */
internal fun Edit.restructure(before: Song): Restructure = when (this) {
    is InsertMeasure -> before.measuresInserted(at, count = 1)
    is PasteMeasures -> before.measuresInserted(at, count = copied.measures.size)
    is DeleteMeasure -> {
        // As in DeleteMeasure: a Section whose only Measure goes is removed with it.
        val removed = before.sections.map { it.first == measure && it.last == measure }
        Restructure(
            measures = before.measures.indices.map { if (it < measure) it else if (it == measure) null else it - 1 },
            sections = before.sections.indices.map { index -> if (removed[index]) null else index - removed.take(index).count { it } },
        )
    }
    // Sections stay in Measure order, so the new one goes in before every Section after it.
    is AddSection -> Restructure.none(before).copy(
        sections = before.sections.mapIndexed { index, it -> if (it.first < section.first) index else index + 1 },
    )
    is RemoveSection -> Restructure.none(before).copy(
        sections = before.sections.indices.map { if (it < section) it else if (it == section) null else it - 1 },
    )
    // No two Sections share a Measure, so resizing one never changes their order.
    is ResizeSection, is ChangeSection, is RenameSection,
    is WriteElement, is ChangeDuration, is DeleteElement,
    is ChangeTimeSignature, is ChangeKeySignature, is ChangeTempo, is ChangeMeasure,
    -> Restructure.none(before)
}

private fun Song.measuresInserted(at: Int, count: Int) = Restructure.none(this).copy(
    measures = measures.indices.map { if (it < at) it else it + count },
)
