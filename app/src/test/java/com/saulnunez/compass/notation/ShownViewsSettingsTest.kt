package com.saulnunez.compass.notation

import alphaTab.Settings
import alphaTab.StaveProfile
import alphaTab.TabRhythmMode
import com.saulnunez.compass.domain.ShownViews
import org.junit.Assert.assertEquals
import org.junit.Test

class ShownViewsSettingsTest {

    @Test
    fun `Tablature alone draws only the tablature staff, with the rhythm shown on it`() {
        val settings = Settings().apply { show(ShownViews.TABLATURE) }
        assertEquals(StaveProfile.Tab, settings.display.staveProfile)
        // Without Standard notation above it, the tablature has to show durations itself.
        assertEquals(TabRhythmMode.ShowWithBars, settings.notation.rhythmMode)
    }

    @Test
    fun `Standard notation alone draws only the five-line staff`() {
        val settings = Settings().apply { show(ShownViews.STANDARD_NOTATION) }
        assertEquals(StaveProfile.Score, settings.display.staveProfile)
    }

    @Test
    fun `both views stack Standard notation over Tablature`() {
        val settings = Settings().apply { show(ShownViews.TABLATURE) }.apply { show(ShownViews.BOTH) }
        assertEquals(StaveProfile.ScoreTab, settings.display.staveProfile)
        // Standard notation carries the rhythm, so the tablature below it leaves it out.
        assertEquals(TabRhythmMode.Automatic, settings.notation.rhythmMode)
    }
}
