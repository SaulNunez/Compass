package com.saulnunez.compass.domain

/** A way a Note is played beyond its string, fret and duration. */
enum class Technique(
    /** Techniques sharing a group cannot be played on the same Note; null when it goes with any. */
    private val group: Group? = null,
    /** The Instrument kinds it is offered on. */
    private val kinds: Set<InstrumentKind> = InstrumentKind.entries.toSet(),
    /**
     * Played by the whole Chord at once, so every Note of a Chord has it or none does (alphaTab,
     * too, marks it on the beat rather than on each Note).
     */
    val isPerChord: Boolean = false,
) {
    /** Into the next Note on the same string without picking it: a hammer-on going up, a pull-off going down. */
    HAMMER_ON_PULL_OFF(Group.LEGATO),

    /** Sliding into the next Note on the same string. */
    SLIDE(Group.LEGATO),

    /** Bent up a whole step and held. */
    BEND(Group.BEND),

    /** Bent up a whole step and let back down. */
    BEND_AND_RELEASE(Group.BEND),
    VIBRATO,
    PALM_MUTE,

    /** Muted by the fretting hand: a percussive click with no pitch. */
    DEAD_NOTE,
    SLAP(Group.THUMB_OR_FINGER, setOf(InstrumentKind.BASS), isPerChord = true),
    POP(Group.THUMB_OR_FINGER, setOf(InstrumentKind.BASS), isPerChord = true),
    ;

    private enum class Group { LEGATO, BEND, THUMB_OR_FINGER }

    /** Whether this Technique cannot be played on the same Note as [other]. */
    fun excludes(other: Technique): Boolean = other != this && group != null && group == other.group

    fun isOfferedOn(kind: InstrumentKind): Boolean = kind in kinds

    companion object {
        /** The Techniques offered on [kind], in toolbar order. */
        fun offeredOn(kind: InstrumentKind): List<Technique> = entries.filter { it.isOfferedOn(kind) }
    }
}
