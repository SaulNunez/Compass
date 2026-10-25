package com.saulnunez.compass.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.saulnunez.compass.R
import com.saulnunez.compass.domain.Library
import com.saulnunez.compass.domain.LibraryEntry
import com.saulnunez.compass.domain.LibraryOrder
import com.saulnunez.compass.domain.SongId
import com.saulnunez.compass.ui.theme.CompassTheme

/**
 * The list of all Songs. [library] is null while it is still loading.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    library: Library?,
    onOpenSong: (SongId) -> Unit,
    onNewSong: () -> Unit,
    onBackUp: () -> Unit,
    onRestore: () -> Unit,
    onDeleteSong: (SongId) -> Unit,
    modifier: Modifier = Modifier,
) {
    var search by rememberSaveable { mutableStateOf("") }
    var order by rememberSaveable { mutableStateOf(LibraryOrder.TITLE) }
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.library_title)) },
                actions = { BackupMenu(onBackUp = onBackUp, onRestore = onRestore) },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNewSong) { Text(stringResource(R.string.new_song)) }
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text(stringResource(R.string.library_search)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                LibraryOrder.entries.forEach { option ->
                    FilterChip(
                        selected = order == option,
                        onClick = { order = option },
                        label = { Text(stringResource(option.label())) },
                    )
                }
            }

            val entries = library?.browse(search, order)
            when {
                entries == null -> Unit
                entries.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    val message = if (library.entries.isEmpty()) R.string.library_empty else R.string.library_no_matches
                    Text(stringResource(message), style = MaterialTheme.typography.bodyLarge)
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(entries, key = { it.id.value }) { entry ->
                        SongRow(entry, onOpen = { onOpenSong(entry.id) }, onDelete = { pendingDelete = entry.id.value })
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    val toDelete = pendingDelete?.let { id -> library?.entries?.find { it.id.value == id } }
    if (toDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_song_title, toDelete.title)) },
            text = { Text(stringResource(R.string.delete_song_message)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    onDeleteSong(toDelete.id)
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun BackupMenu(onBackUp: () -> Unit, onRestore: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }) { Text(stringResource(R.string.backup_menu)) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.backup_create)) },
                onClick = {
                    expanded = false
                    onBackUp()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.backup_restore)) },
                onClick = {
                    expanded = false
                    onRestore()
                },
            )
        }
    }
}

@Composable
private fun SongRow(entry: LibraryEntry, onOpen: () -> Unit, onDelete: () -> Unit) {
    ListItem(
        headlineContent = { Text(entry.title) },
        supportingContent = entry.artist.takeIf { it.isNotBlank() }?.let { artist -> { Text(artist) } },
        trailingContent = { TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) } },
        modifier = Modifier.clickable(onClick = onOpen),
    )
}

private fun LibraryOrder.label(): Int = when (this) {
    LibraryOrder.TITLE -> R.string.library_sort_title
    LibraryOrder.ARTIST -> R.string.library_sort_artist
    LibraryOrder.LAST_PRACTICED -> R.string.library_sort_last_practiced
}

@Preview(showBackground = true)
@Composable
private fun LibraryScreenPreview() {
    CompassTheme {
        LibraryScreen(
            library = Library(
                listOf(
                    LibraryEntry(SongId("1"), "Blackbird", "The Beatles", lastPracticed = null),
                    LibraryEntry(SongId("2"), "Canción del mariachi", "Los Lobos", lastPracticed = null),
                ),
            ),
            onOpenSong = {},
            onNewSong = {},
            onBackUp = {},
            onRestore = {},
            onDeleteSong = {},
        )
    }
}
