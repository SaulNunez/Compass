package com.saulnunez.compass.practice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.saulnunez.compass.R
import com.saulnunez.compass.domain.Song
import com.saulnunez.compass.notation.TablatureView
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
        TablatureView(song, Modifier.padding(innerPadding).fillMaxSize())
    }
}

@Preview(showBackground = true)
@Composable
private fun PracticeScreenPreview() {
    CompassTheme {
        PracticeScreen(demoSong, onBack = {}, onEdit = {})
    }
}
