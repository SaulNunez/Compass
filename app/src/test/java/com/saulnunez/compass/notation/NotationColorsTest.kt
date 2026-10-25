package com.saulnunez.compass.notation

import alphaTab.Settings
import alphaTab.model.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class NotationColorsTest {

    private val dark = NotationColors(
        ink = 0xFFE6E1E5.toInt(),
        lines = 0xFF938F99.toInt(),
        accent = 0xFFF2B8B5.toInt(),
    )

    @Test
    fun `Notes, clefs and the title are drawn in the ink colour`() {
        val resources = Settings().apply { use(dark) }.display.resources
        assertColor(Color(0xE6.toDouble(), 0xE1.toDouble(), 0xE5.toDouble()), resources.mainGlyphColor)
        assertColor(Color(0xE6.toDouble(), 0xE1.toDouble(), 0xE5.toDouble()), resources.scoreInfoColor)
    }

    @Test
    fun `other voices are drawn in a fainter ink`() {
        val resources = Settings().apply { use(dark) }.display.resources
        // alphaTab's own default draws other voices at alpha 100 of 255.
        assertColor(Color(0xE6.toDouble(), 0xE1.toDouble(), 0xE5.toDouble(), 100.0), resources.secondaryGlyphColor)
    }

    @Test
    fun `staff and tablature lines and Measure lines use the line colour`() {
        val resources = Settings().apply { use(dark) }.display.resources
        assertColor(Color(0x93.toDouble(), 0x8F.toDouble(), 0x99.toDouble()), resources.staffLineColor)
        assertColor(Color(0x93.toDouble(), 0x8F.toDouble(), 0x99.toDouble()), resources.barSeparatorColor)
    }

    @Test
    fun `Measure numbers use the accent colour`() {
        val resources = Settings().apply { use(dark) }.display.resources
        assertColor(Color(0xF2.toDouble(), 0xB8.toDouble(), 0xB5.toDouble()), resources.barNumberColor)
    }

    private fun assertColor(expected: Color, actual: Color) {
        assertEquals(listOf(expected.r, expected.g, expected.b, expected.a), listOf(actual.r, actual.g, actual.b, actual.a))
    }
}
