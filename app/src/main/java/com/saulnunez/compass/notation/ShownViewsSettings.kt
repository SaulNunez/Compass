package com.saulnunez.compass.notation

import alphaTab.Settings
import alphaTab.StaveProfile
import alphaTab.TabRhythmMode
import com.saulnunez.compass.domain.ShownViews

/**
 * Sets which staves alphaTab draws for [views]. The Score is left alone, so switching views
 * only needs the settings re-applied to the view, not a new translation.
 */
fun Settings.show(views: ShownViews) {
    display.staveProfile = when (views) {
        ShownViews.TABLATURE -> StaveProfile.Tab
        ShownViews.STANDARD_NOTATION -> StaveProfile.Score
        ShownViews.BOTH -> StaveProfile.ScoreTab
    }
    // alphaTab hides the tablature's rhythm whenever a staff could show Standard notation,
    // even if the profile leaves it out; with Tablature alone the rhythm must be forced on.
    notation.rhythmMode = if (views == ShownViews.TABLATURE) TabRhythmMode.ShowWithBars else TabRhythmMode.Automatic
}
