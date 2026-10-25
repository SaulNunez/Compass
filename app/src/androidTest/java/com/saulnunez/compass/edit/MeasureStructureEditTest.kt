package com.saulnunez.compass.edit

import alphaTab.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.saulnunez.compass.R
import com.saulnunez.compass.domain.EditSession
import com.saulnunez.compass.domain.KeySignature
import com.saulnunez.compass.domain.Tempo
import com.saulnunez.compass.domain.TimeSignature
import com.saulnunez.compass.notation.toAlphaTabScore
import com.saulnunez.compass.practice.demoSong
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import alphaTab.model.KeySignature as AlphaTabKeySignature

/** Measure inserts, deletes and changes made in Edit mode reach the alphaTab model on the device. */
@RunWith(AndroidJUnit4::class)
class MeasureStructureEditTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int) = compose.activity.getString(id)

    @Test
    fun insertingAndDeletingMeasuresInEditModeReachesTheAlphaTabScore() {
        var session by mutableStateOf(EditSession.start(demoSong))
        compose.setContent { EditScreen(session, onSessionChange = { session = it }, onDone = {}) }
        val measures = demoSong.measures.size

        compose.onNodeWithText(string(R.string.insert_measure_after)).performClick()
        compose.waitForIdle()
        assertEquals(measures + 1, session.song.toAlphaTabScore(Settings()).masterBars.length.toInt())

        compose.onNodeWithText(string(R.string.delete_measure)).performClick()
        compose.onNodeWithText(string(R.string.delete_measure)).performClick()
        compose.waitForIdle()
        val score = session.song.toAlphaTabScore(Settings())
        assertEquals(measures - 1, score.masterBars.length.toInt())
        score.tracks.forEach { track -> assertEquals(measures - 1, track.staves[0].bars.length.toInt()) }
    }

    @Test
    fun timeSignatureKeySignatureAndTempoChangesReachTheAlphaTabScore() {
        val edited = EditSession.start(demoSong).moveToMeasure(1)
            .changeTimeSignature(TimeSignature(3, 4))
            .changeKeySignature(KeySignature(fifths = -1))
            .changeTempo(Tempo(70))
            .song
        val score = edited.toAlphaTabScore(Settings())

        assertEquals(3, score.masterBars[1].timeSignatureNumerator.toInt())
        assertEquals(AlphaTabKeySignature.F, score.tracks[0].staves[0].bars[1].keySignature)
        assertEquals(70, score.masterBars[1].tempoAutomation!!.value.toInt())
    }
}
