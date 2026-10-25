package com.saulnunez.compass.songfile

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.saulnunez.compass.R
import com.saulnunez.compass.domain.SongId
import com.saulnunez.compass.storage.SongStore
import com.saulnunez.compass.storage.exportSongFile
import com.saulnunez.compass.storage.songFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** The type a Song file is sent and picked as. */
const val SONG_FILE_MIME_TYPE = "application/json"

/** Some apps label a received Song file differently, so the file picker accepts these too. */
val SONG_FILE_OPEN_MIME_TYPES = arrayOf(SONG_FILE_MIME_TYPE, "text/plain", "application/octet-stream")

/**
 * Writes the Song with [id] as a Song file and opens the share sheet to send it.
 * @return false if the Song file could not be written.
 */
suspend fun shareSongFile(context: Context, store: SongStore, id: SongId): Boolean {
    val (songFile, title) = withContext(Dispatchers.IO) {
        runCatching {
            val title = store.load(id).title
            // Only the Song file being shared is kept, so earlier ones don't pile up in the cache.
            val folder = File(context.cacheDir, SHARED_FOLDER).apply {
                deleteRecursively()
                mkdirs()
            }
            File(folder, songFileName(title, context.getString(R.string.song_file_untitled))).apply {
                outputStream().use { store.exportSongFile(id, it) }
            } to title
        }.getOrNull()
    } ?: return false

    val uri = FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, songFile)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = SONG_FILE_MIME_TYPE
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, title)
        // ClipData lets the share sheet preview the file and carries the read grant to the chosen app.
        clipData = ClipData.newRawUri(title, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, context.getString(R.string.song_share_chooser, title)))
    return true
}

/** Must match the provider's authorities in AndroidManifest.xml. */
private const val AUTHORITY_SUFFIX = ".songfiles"

/** Must match the cache-path in res/xml/song_file_paths.xml. */
private const val SHARED_FOLDER = "song-files"
