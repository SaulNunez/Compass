package com.saulnunez.compass.notation

import alphaTab.Settings
import com.saulnunez.compass.domain.KeySignature
import com.saulnunez.compass.domain.Measure
import com.saulnunez.compass.domain.Section
import com.saulnunez.compass.domain.SongId
import com.saulnunez.compass.domain.Tempo
import com.saulnunez.compass.domain.TimeSignature
import com.saulnunez.compass.domain.TuningPreset
import com.saulnunez.compass.domain.newSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Section names reach the alphaTab Score, which writes them above the first Measure of each Section in both views. */
class SectionTranslationTest {

    private val song = newSong(
        id = SongId("sections"),
        title = "Sections",
        artist = "",
        startingMeasure = Measure(TimeSignature(4, 4), KeySignature.C_MAJOR, Tempo(120)),
        trackName = "Guitar",
        instrument = TuningPreset.GUITAR_STANDARD.instrument(),
    ).copy(sections = listOf(Section("Intro", 0, 0), Section("Verse", 2, 3)))

    @Test
    fun `each Section's name marks its first Measure`() {
        val masterBars = song.toAlphaTabScore(Settings()).masterBars

        assertEquals("Intro", masterBars[0].section!!.text)
        assertFalse(masterBars[1].isSectionStart)
        assertEquals("Verse", masterBars[2].section!!.text)
        assertFalse(masterBars[3].isSectionStart)
    }
}
