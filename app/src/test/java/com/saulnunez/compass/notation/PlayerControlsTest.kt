package com.saulnunez.compass.notation

import com.saulnunez.compass.domain.PracticeSettings
import com.saulnunez.compass.domain.TempoPercentage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerControlsTest {

    @Test
    fun `the tempo percentage becomes alphaTab's playback speed, which scales every Tempo`() {
        assertEquals(0.25, PracticeSettings(TempoPercentage(25)).playerControls().playbackSpeed, 0.0)
        assertEquals(1.0, PracticeSettings(TempoPercentage(100)).playerControls().playbackSpeed, 0.0)
        assertEquals(1.5, PracticeSettings(TempoPercentage(150)).playerControls().playbackSpeed, 0.0)
    }

    @Test
    fun `the click and the count-in sound only when switched on`() {
        val off = PracticeSettings().playerControls()
        assertEquals(0.0, off.metronomeVolume, 0.0)
        assertEquals(0.0, off.countInVolume, 0.0)

        val on = PracticeSettings(click = true, countIn = true).playerControls()
        assertTrue(on.metronomeVolume > 0.0)
        assertTrue(on.countInVolume > 0.0)
    }
}
