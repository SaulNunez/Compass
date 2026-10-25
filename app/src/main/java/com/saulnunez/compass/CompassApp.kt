package com.saulnunez.compass

import android.content.Context
import android.content.res.Resources
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalResources
import com.saulnunez.compass.domain.EditSession
import com.saulnunez.compass.domain.Library
import com.saulnunez.compass.domain.PracticeSettings
import com.saulnunez.compass.domain.Song
import com.saulnunez.compass.domain.SongId
import com.saulnunez.compass.edit.EditScreen
import com.saulnunez.compass.library.LibraryScreen
import com.saulnunez.compass.library.NewSongScreen
import com.saulnunez.compass.practice.PracticeScreen
import com.saulnunez.compass.songfile.SONG_FILE_OPEN_MIME_TYPES
import com.saulnunez.compass.songfile.sendSongFile
import com.saulnunez.compass.storage.AddResult
import com.saulnunez.compass.storage.NotABackupException
import com.saulnunez.compass.storage.NotASongFileException
import com.saulnunez.compass.storage.RestoreReport
import com.saulnunez.compass.storage.SongStore
import com.saulnunez.compass.storage.UnsupportedSongFormatException
import com.saulnunez.compass.storage.importSongFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate

/** Where the app is: the Library, creating a Song, or a Song open in Practice mode or Edit mode. */
private sealed interface Destination {
    data object Library : Destination
    data object NewSong : Destination
    data class Practice(val songId: SongId) : Destination
    data class Edit(val songId: SongId) : Destination
}

@Composable
fun CompassApp() {
    val context = LocalContext.current
    val resources = LocalResources.current
    val store = remember { SongStore(File(context.filesDir, "library")) }
    // Everything that touches the store takes turns, in order, so nothing reads a Song while an edit is still being saved.
    val storeLock = remember { Mutex() }
    val scope = rememberCoroutineScope()

    // Saved as a plain string so the current screen survives rotation and process death.
    var route by rememberSaveable { mutableStateOf(LIBRARY_ROUTE) }
    val destination = route.toDestination()
    val goToLibrary = { route = LIBRARY_ROUTE }

    var reloads by remember { mutableIntStateOf(0) }
    val showMessage = { message: String -> Toast.makeText(context, message, Toast.LENGTH_LONG).show() }

    val backUp = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BACKUP_MIME_TYPE)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val written = storeLock.withLock {
                withContext(Dispatchers.IO) {
                    runCatching { context.contentResolver.openOutputStream(uri)!!.use(store::backUp) }
                }
            }
            showMessage(resources.getString(if (written.isSuccess) R.string.backup_done else R.string.backup_failed))
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val report = storeLock.withLock {
                withContext(Dispatchers.IO) {
                    runCatching { store.restore(context.contentResolver.openInputStream(uri)!!) }
                }
            }
            reloads++
            showMessage(
                report.fold(
                    onSuccess = { it.summary(resources) },
                    onFailure = {
                        resources.getString(if (it is NotABackupException) R.string.restore_not_a_backup else R.string.restore_failed)
                    },
                ),
            )
        }
    }

    val importSongFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val imported = storeLock.withLock {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val result = store.importSongFile(context.contentResolver.openInputStream(uri)!!)
                        result to store.load(result.id).title
                    }
                }
            }
            reloads++
            showMessage(
                imported.fold(
                    onSuccess = { (result, title) -> resources.getString(result.importMessage(), title) },
                    onFailure = {
                        resources.getString(
                            when (it) {
                                is NotASongFileException -> R.string.song_file_not_a_song_file
                                is UnsupportedSongFormatException -> R.string.song_file_too_new
                                else -> R.string.song_file_import_failed
                            },
                        )
                    },
                ),
            )
        }
    }
    val sendSong: (SongId) -> Unit = { id ->
        scope.launch {
            // Waits for any edit still being saved, so the Song file has the Song as it is now.
            val sent = storeLock.withLock { sendSongFile(context, store, id) }
            if (!sent) showMessage(resources.getString(R.string.send_song_file_failed))
        }
    }

    when (destination) {
        Destination.Library -> {
            var library by remember { mutableStateOf<Library?>(null) }
            LaunchedEffect(reloads) { library = storeLock.withLock { withContext(Dispatchers.IO) { store.library() } } }

            LibraryScreen(
                library = library,
                onOpenSong = { route = it.toPracticeRoute() },
                onNewSong = { route = NEW_SONG_ROUTE },
                onBackUp = { backUp.launch(resources.getString(R.string.backup_file_name, LocalDate.now().toString())) },
                onRestore = { restore.launch(BACKUP_OPEN_MIME_TYPES) },
                onImportSong = { importSongFile.launch(SONG_FILE_OPEN_MIME_TYPES) },
                onSendSongFile = sendSong,
                onDeleteSong = { id ->
                    scope.launch {
                        storeLock.withLock { withContext(Dispatchers.IO) { store.delete(id) } }
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
                        storeLock.withLock { withContext(Dispatchers.IO) { store.save(song) } }
                        // A new Song opens in Practice mode, like every Song.
                        route = song.id.toPracticeRoute()
                    }
                },
                onBack = goToLibrary,
            )
        }

        is Destination.Practice -> {
            BackHandler(onBack = goToLibrary)
            val id = destination.songId
            val song = loadSong(store, storeLock, id, onMissing = goToLibrary)
            val loadedSettings by produceState<PracticeSettings?>(null, id) {
                value = storeLock.withLock { withContext(Dispatchers.IO) { store.practiceSettings(id) } }
            }
            val initialSettings = loadedSettings
            if (song != null && initialSettings != null) {
                var settings by remember(id, initialSettings) { mutableStateOf(initialSettings) }
                PracticeScreen(
                    song = song,
                    settings = settings,
                    onSettingsChange = { next ->
                        settings = next
                        // Saved beside the Song, never into it; in the app's scope so leaving can't cancel it.
                        scope.launch {
                            storeLock.withLock { withContext(Dispatchers.IO) { runCatching { store.savePracticeSettings(id, next) } } }
                        }
                    },
                    onPlaybackStarted = {
                        scope.launch {
                            storeLock.withLock { withContext(Dispatchers.IO) { runCatching { store.markPracticed(id, Instant.now()) } } }
                        }
                    },
                    onBack = goToLibrary,
                    onEdit = { route = id.toEditRoute() },
                    onSendSongFile = { sendSong(id) },
                )
            }
        }

        is Destination.Edit -> {
            val backToPractice = { route = destination.songId.toPracticeRoute() }
            BackHandler(onBack = backToPractice)
            val id = destination.songId
            val song = loadSong(store, storeLock, id, onMissing = goToLibrary)
            // The Practice settings as they were when editing started, so they can follow the edits.
            val settingsBefore by produceState<PracticeSettings?>(null, id) {
                value = storeLock.withLock { withContext(Dispatchers.IO) { store.practiceSettings(id) } }
            }
            val initialSettings = settingsBefore
            if (song != null && initialSettings != null) {
                var session by remember(song) { mutableStateOf(EditSession.start(song)) }
                var savedSettings by remember(song, initialSettings) { mutableStateOf(initialSettings) }
                EditScreen(
                    session = session,
                    onSessionChange = { next ->
                        val edited = next.song != session.song
                        session = next
                        val reconciled = next.reconcile(initialSettings)
                        val settingsChanged = reconciled != savedSettings
                        savedSettings = reconciled
                        // Every edit is saved straight away, in the app's scope so leaving Edit mode can't cancel it.
                        if (edited) {
                            scope.launch {
                                save(context, store, storeLock, next.song)
                                if (settingsChanged) {
                                    storeLock.withLock { withContext(Dispatchers.IO) { runCatching { store.savePracticeSettings(id, reconciled) } } }
                                }
                            }
                        }
                    },
                    onDone = backToPractice,
                )
            }
        }
    }
}

/** Loads a Song, or says it can't be opened and calls [onMissing]. Null while loading. */
@Composable
private fun loadSong(store: SongStore, storeLock: Mutex, id: SongId, onMissing: () -> Unit): Song? {
    val context = LocalContext.current
    val song by produceState<Song?>(null, id) {
        value = storeLock.withLock { withContext(Dispatchers.IO) { runCatching { store.load(id) }.getOrNull() } }
        if (value == null) {
            // Deleted, damaged, or saved by a newer Compass: say so and go back to the Library.
            Toast.makeText(context, R.string.song_cannot_open, Toast.LENGTH_LONG).show()
            onMissing()
        }
    }
    return song
}

private suspend fun save(context: Context, store: SongStore, storeLock: Mutex, song: Song) {
    val saved = storeLock.withLock { withContext(Dispatchers.IO) { runCatching { store.save(song) }.isSuccess } }
    if (!saved) Toast.makeText(context, R.string.song_cannot_save, Toast.LENGTH_LONG).show()
}

private fun RestoreReport.summary(resources: Resources): String {
    val restored = resources.getString(R.string.restore_done, added, addedAsCopies, alreadyPresent)
    return if (unreadable == 0) restored else restored + "\n" + resources.getString(R.string.restore_unreadable, unreadable)
}

private fun AddResult.importMessage(): Int = when (this) {
    is AddResult.Added -> R.string.song_file_imported
    is AddResult.AddedAsCopy -> R.string.song_file_imported_as_copy
    is AddResult.AlreadyPresent -> R.string.song_file_already_present
}

private const val BACKUP_MIME_TYPE = "application/zip"

/** Some file providers label zips differently, so accept the common variants. */
private val BACKUP_OPEN_MIME_TYPES = arrayOf(BACKUP_MIME_TYPE, "application/x-zip-compressed", "application/octet-stream")

private const val LIBRARY_ROUTE = "library"
private const val NEW_SONG_ROUTE = "new-song"
private const val PRACTICE_ROUTE_PREFIX = "practice/"
private const val EDIT_ROUTE_PREFIX = "edit/"

private fun SongId.toPracticeRoute() = PRACTICE_ROUTE_PREFIX + value
private fun SongId.toEditRoute() = EDIT_ROUTE_PREFIX + value

private fun String.toDestination(): Destination = when {
    this == NEW_SONG_ROUTE -> Destination.NewSong
    startsWith(PRACTICE_ROUTE_PREFIX) -> Destination.Practice(SongId(removePrefix(PRACTICE_ROUTE_PREFIX)))
    startsWith(EDIT_ROUTE_PREFIX) -> Destination.Edit(SongId(removePrefix(EDIT_ROUTE_PREFIX)))
    else -> Destination.Library
}
