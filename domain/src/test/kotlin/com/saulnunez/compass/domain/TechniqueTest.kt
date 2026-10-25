package com.saulnunez.compass.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TechniqueTest {

    private fun songFor(preset: TuningPreset) = newSong(
        id = SongId("techniques"),
        title = "Techniques",
        artist = "",
        startingMeasure = Measure(TimeSignature(4, 4), KeySignature.C_MAJOR, Tempo(120)),
        trackName = "Track",
        instrument = preset.instrument(),
    )

    private val guitar = EditSession.start(songFor(TuningPreset.GUITAR_STANDARD))
    private val bass = EditSession.start(songFor(TuningPreset.BASS_STANDARD))

    private fun EditSession.measure(index: Int) = song.tracks[0].voice.measures[index]

    private val quarter = Duration.QUARTER

    @Test
    fun `each guitar Technique can be added to a Note and removed again`() {
        val guitarTechniques = listOf(
            Technique.HAMMER_ON_PULL_OFF, Technique.SLIDE, Technique.BEND, Technique.BEND_AND_RELEASE,
            Technique.VIBRATO, Technique.PALM_MUTE, Technique.DEAD_NOTE,
        )
        assertEquals(guitarTechniques, guitar.availableTechniques)

        guitarTechniques.forEach { technique ->
            val added = guitar.typeDigit(5).toggleTechnique(technique)
            assertEquals(listOf(Note(1, 5, quarter, techniques = setOf(technique))), added.measure(0))

            assertEquals(listOf(Note(1, 5, quarter)), added.toggleTechnique(technique).measure(0))
        }
    }

    @Test
    fun `slap and pop are offered on bass only`() {
        assertEquals(Technique.entries, bass.availableTechniques)

        val slapped = bass.typeDigit(3).toggleTechnique(Technique.SLAP)
        assertEquals(listOf(Note(1, 3, quarter, techniques = setOf(Technique.SLAP))), slapped.measure(0))

        val guitarNote = guitar.typeDigit(3)
        assertEquals(guitarNote.song, guitarNote.toggleTechnique(Technique.SLAP).song)
    }

    @Test
    fun `a Note can have several Techniques`() {
        val session = guitar.typeDigit(7).toggleTechnique(Technique.BEND).toggleTechnique(Technique.VIBRATO)

        assertEquals(setOf(Technique.BEND, Technique.VIBRATO), session.noteAtCursor!!.techniques)
    }

    @Test
    fun `a Technique replaces the one it cannot be played with`() {
        val bend = guitar.typeDigit(7).toggleTechnique(Technique.BEND).toggleTechnique(Technique.BEND_AND_RELEASE)
        assertEquals(setOf(Technique.BEND_AND_RELEASE), bend.noteAtCursor!!.techniques)

        val legato = guitar.typeDigit(7).toggleTechnique(Technique.HAMMER_ON_PULL_OFF).toggleTechnique(Technique.SLIDE)
        assertEquals(setOf(Technique.SLIDE), legato.noteAtCursor!!.techniques)

        val slapPop = bass.typeDigit(7).toggleTechnique(Technique.SLAP).toggleTechnique(Technique.POP)
        assertEquals(setOf(Technique.POP), slapPop.noteAtCursor!!.techniques)
    }

    @Test
    fun `a Technique goes on the Note of the Chord on the cursor's string only`() {
        val session = guitar.typeDigit(5).moveToStringBelow().typeDigit(5).toggleTechnique(Technique.PALM_MUTE)

        assertEquals(
            listOf(Chord(listOf(Note(1, 5, quarter), Note(2, 5, quarter, techniques = setOf(Technique.PALM_MUTE))))),
            session.measure(0),
        )
    }

    @Test
    fun `with no Note on the cursor's string a Technique changes nothing and is not an edit`() {
        val session = guitar.toggleTechnique(Technique.VIBRATO)

        assertEquals(guitar.song, session.song)
        assertFalse(session.canUndo)
    }

    @Test
    fun `adding a Technique is undone in one step`() {
        val written = guitar.typeDigit(5)

        assertEquals(written.song, written.toggleTechnique(Technique.SLIDE).undo().song)
    }
}
