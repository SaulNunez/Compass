package com.saulnunez.compass

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.saulnunez.compass.domain.Library
import com.saulnunez.compass.domain.Song
import com.saulnunez.compass.domain.SongId
import com.saulnunez.compass.library.LibraryScreen
import com.saulnunez.compass.library.NewSongScreen
import com.saulnunez.compass.practice.PracticeScreen
import com.saulnunez.compass.storage.SongStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Where the app is: the Library, creating a Song, or a Song open in Practice mode. */
private sealed interface Destination {
    data object Library : Destination
    data object NewSong : Destination
    data class Practice(val songId: SongId) : Destination
}

@Composable
fun CompassApp() {
    val context = LocalContext.current
    val store = remember { SongStore(File(context.filesDir, "library")) }
    val scope = rememberCoroutineScope()

    // Saved as a plain string so the current screen survives rotation and process death.
    var route by rememberSaveable { mutableStateOf(LIBRARY_ROUTE) }
    val destination = route.toDestination()
    val goToLibrary = { route = LIBRARY_ROUTE }

    when (destination) {
        Destination.Library -> {
            var reloads by remember { mutableIntStateOf(0) }
            var library by remember { mutableStateOf<Library?>(null) }
            LaunchedEffect(reloads) { library = withContext(Dispatchers.IO) { store.library() } }

            LibraryScreen(
                library = library,
                onOpenSong = { route = it.toPracticeRoute() },
                onNewSong = { route = NEW_SONG_ROUTE },
                onDeleteSong = { id ->
                    scope.launch {
                        withContext(Dispatchers.IO) { store.delete(id) }
                        reloads++
                    }
                },
            )
        }

        Destination.NewSong -> {
            BackHandler(onBack = goToLibrary)
            NewSongScreen(
                onCreate = { song ->
                    scope.launch {
                        withContext(Dispatchers.IO) { store.save(song) }
                        // A new Song opens in Practice mode, like every Song.
                        route = song.id.toPracticeRoute()
                    }
                },
                onBack = goToLibrary,
            )
        }

        is Destination.Practice -> {
            BackHandler(onBack = goToLibrary)
            val song by produceState<Song?>(null, destination.songId) {
                value = withContext(Dispatchers.IO) { runCatching { store.load(destination.songId) }.getOrNull() }
                if (value == null) {
                    // Deleted, damaged, or saved by a newer Compass: say so and go back to the Library.
                    Toast.makeText(context, R.string.song_cannot_open, Toast.LENGTH_LONG).show()
                    goToLibrary()
                }
            }
            song?.let { PracticeScreen(it, onBack = goToLibrary) }
        }
    }
}

private const val LIBRARY_ROUTE = "library"
private const val NEW_SONG_ROUTE = "new-song"
private const val PRACTICE_ROUTE_PREFIX = "practice/"

private fun SongId.toPracticeRoute() = PRACTICE_ROUTE_PREFIX + value

private fun String.toDestination(): Destination = when {
    this == NEW_SONG_ROUTE -> Destination.NewSong
    startsWith(PRACTICE_ROUTE_PREFIX) -> Destination.Practice(SongId(removePrefix(PRACTICE_ROUTE_PREFIX)))
    else -> Destination.Library
}
