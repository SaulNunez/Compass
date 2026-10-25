package com.saulnunez.compass.notation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.saulnunez.compass.R
import com.saulnunez.compass.domain.ShownViews
import com.saulnunez.compass.practice.demoSong
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotationLoadingTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun aLoadingIndicatorShowsUntilTheSongIsFirstDrawn() {
        val loading = compose.activity.getString(R.string.notation_loading)
        compose.mainClock.autoAdvance = false
        compose.setContent { NotationView(demoSong, ShownViews.TABLATURE, Modifier.fillMaxSize()) }

        // Before alphaTab has drawn anything, the empty notation area says it is still preparing the Song.
        compose.onNodeWithContentDescription(loading).assertExists()

        compose.mainClock.autoAdvance = true
        compose.waitUntilDoesNotExist(hasContentDescription(loading), timeoutMillis = 15_000)
    }
}
