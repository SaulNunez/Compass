package com.saulnunez.compass.practice

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.saulnunez.compass.R
import com.saulnunez.compass.domain.PracticeSettings
import com.saulnunez.compass.domain.ShownViews
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PracticeModeTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int) = compose.activity.getString(id)

    @Test
    fun practiceModeOffersOnlyLeavingSendingASongFilePracticeToolsAndSwitchingToEditMode() {
        compose.setContent {
            PracticeScreen(demoSong, PracticeSettings(), onSettingsChange = {}, onBack = {}, onEdit = {}, onSendSongFile = {})
        }

        // The only actions, none of which change the Song: back to the Library, sending a Song
        // file and the explicit switch to Edit mode; the choice of shown views; the Playback tools
        // (play/stop, click, count-in and the Loop; the tempo slider is not a click action); and
        // Mute and Solo for each Track. All but the first three only change the Practice settings.
        // No number pad, Duration choice, undo or delete.
        val leaveSendAndEdit = 3
        val playbackTools = 4
        val trackMix = 2 * demoSong.tracks.size
        compose.onAllNodes(hasClickAction())
            .assertCountEquals(leaveSendAndEdit + ShownViews.entries.size + playbackTools + trackMix)
        compose.onNodeWithText(string(R.string.back_to_library)).assertExists()
        compose.onNodeWithText(string(R.string.send_song_file)).assertExists()
        compose.onNodeWithText(string(R.string.enter_edit_mode)).assertExists()
        compose.onNodeWithText(string(R.string.playback_loop)).assertHasClickAction()
        compose.onAllNodesWithText(string(R.string.track_mute)).assertCountEquals(demoSong.tracks.size)
        compose.onAllNodesWithText(string(R.string.track_solo)).assertCountEquals(demoSong.tracks.size)
        compose.onAllNodesWithText(string(R.string.undo)).assertCountEquals(0)
        compose.onAllNodesWithText(string(R.string.rest)).assertCountEquals(0)
    }

    @Test
    fun editModeIsEnteredOnlyThroughTheEditAction() {
        var editing by mutableStateOf(false)
        compose.setContent {
            if (editing) {
                Text("editing")
            } else {
                PracticeScreen(demoSong, PracticeSettings(), onSettingsChange = {}, onBack = {}, onEdit = { editing = true }, onSendSongFile = {})
            }
        }

        compose.onAllNodesWithText("editing").assertCountEquals(0)
        compose.onNodeWithText(string(R.string.enter_edit_mode)).performClick()
        compose.onNodeWithText("editing").assertExists()
    }
}
