package com.saulnunez.compass.notation

import alphaTab.Settings
import alphaTab.model.Color

/**
 * The colours alphaTab draws the Tablature and Standard notation in, as ARGB ints, so they can follow
 * the app's light or dark theme: [ink] for Notes, rests, clefs and the title, [lines] for staff,
 * tablature and Measure lines, and [accent] for Measure numbers.
 */
data class NotationColors(val ink: Int, val lines: Int, val accent: Int)

/** Draws with [colors]. Like [show], it only changes the settings; re-apply them to the view to redraw. */
fun Settings.use(colors: NotationColors) {
    display.resources.apply {
        mainGlyphColor = colors.ink.toAlphaTab()
        scoreInfoColor = colors.ink.toAlphaTab()
        secondaryGlyphColor = colors.ink.toAlphaTab(alpha = SECONDARY_VOICE_ALPHA)
        staffLineColor = colors.lines.toAlphaTab()
        barSeparatorColor = colors.lines.toAlphaTab()
        barNumberColor = colors.accent.toAlphaTab()
    }
}

private fun Int.toAlphaTab(alpha: Int = (this ushr 24) and 0xFF): Color = Color(
    r = ((this shr 16) and 0xFF).toDouble(),
    g = ((this shr 8) and 0xFF).toDouble(),
    b = (this and 0xFF).toDouble(),
    a = alpha.toDouble(),
)

/** How opaque other voices are drawn, as alphaTab does by default (out of 255). */
private const val SECONDARY_VOICE_ALPHA = 100
