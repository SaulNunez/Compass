package com.saulnunez.compass.practice

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.saulnunez.compass.R
import com.saulnunez.compass.domain.ShownViews
import com.saulnunez.compass.domain.Song
import com.saulnunez.compass.notation.NotationView
import com.saulnunez.compass.ui.BackToLibraryButton
import com.saulnunez.compass.ui.theme.CompassTheme

/**
 * Practice mode: reads a Song without any way to change it. The only way to change the Song
 * is the explicit [onEdit] action, which switches to Edit mode.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(song: Song, onBack: () -> Unit, onEdit: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(song.title)
                        Text(
                            if (song.artist.isBlank()) {
                                stringResource(R.string.practice_mode)
                            } else {
                                stringResource(R.string.practice_mode_subtitle, song.artist)
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                navigationIcon = { BackToLibraryButton(onBack) },
                actions = { TextButton(onClick = onEdit) { Text(stringResource(R.string.enter_edit_mode)) } },
            )
        },
    ) { innerPadding ->
        // In-session for now; it becomes part of the Song's Practice settings later (#6).
        var shownViews by rememberSaveable { mutableStateOf(ShownViews.TABLATURE) }
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            ShownViewsChoice(
                selected = shownViews,
                onSelect = { shownViews = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            NotationView(song, shownViews, Modifier.weight(1f).fillMaxWidth())
        }
    }
}

@Composable
private fun ShownViewsChoice(selected: ShownViews, onSelect: (ShownViews) -> Unit, modifier: Modifier = Modifier) {
    val options = ShownViews.entries
    SingleChoiceSegmentedButtonRow(modifier) {
        options.forEachIndexed { index, views ->
            SegmentedButton(
                selected = views == selected,
                onClick = { onSelect(views) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(stringResource(views.label()))
            }
        }
    }
}

@StringRes
private fun ShownViews.label(): Int = when (this) {
    ShownViews.TABLATURE -> R.string.view_tablature
    ShownViews.STANDARD_NOTATION -> R.string.view_standard_notation
    ShownViews.BOTH -> R.string.view_both
}

@Preview(showBackground = true)
@Composable
private fun PracticeScreenPreview() {
    CompassTheme {
        PracticeScreen(demoSong, onBack = {}, onEdit = {})
    }
}
