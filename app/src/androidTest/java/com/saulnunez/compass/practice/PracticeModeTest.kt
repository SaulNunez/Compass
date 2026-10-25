package com.saulnunez.compass.practice

import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.activity.ComponentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.saulnunez.compass.R
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
    fun practiceModeOffersOnlyLeavingSharingAndSwitchingToEditMode() {
        compose.setContent { PracticeScreen(demoSong, onBack = {}, onEdit = {}, onShare = {}) }

        // Back to the Library, sharing a Song file, the explicit switch to Edit mode and the choice
        // of shown views (none of which change the Song) are the only actions: no number pad,
        // Duration choice, undo or delete.
        compose.onAllNodes(hasClickAction()).assertCountEquals(3 + ShownViews.entries.size)
        compose.onNodeWithText(string(R.string.back_to_library)).assertExists()
        compose.onNodeWithText(string(R.string.song_share)).assertExists()
        compose.onNodeWithText(string(R.string.enter_edit_mode)).assertExists()
        compose.onAllNodesWithText(string(R.string.undo)).assertCountEquals(0)
        compose.onAllNodesWithText(string(R.string.rest)).assertCountEquals(0)
    }

    @Test
    fun editModeIsEnteredOnlyThroughTheEditAction() {
        var editing by mutableStateOf(false)
        compose.setContent {
            if (editing) Text("editing") else PracticeScreen(demoSong, onBack = {}, onEdit = { editing = true }, onShare = {})
        }

        compose.onAllNodesWithText("editing").assertCountEquals(0)
        compose.onNodeWithText(string(R.string.enter_edit_mode)).performClick()
        compose.onNodeWithText("editing").assertExists()
    }
}
