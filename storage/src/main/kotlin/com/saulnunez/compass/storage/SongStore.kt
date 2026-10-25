package com.saulnunez.compass.storage

import com.saulnunez.compass.domain.Library
import com.saulnunez.compass.domain.LibraryEntry
import com.saulnunez.compass.domain.Song
import com.saulnunez.compass.domain.SongId
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.time.Instant

/**
 * Keeps each Song as one versioned JSON document under [root], plus a Library index
 * so the Library can be listed without loading every Song (ADR-0002).
 * Blocking file IO: call it off the main thread.
 */
class SongStore(private val root: File) {

    fun save(song: Song) {
        val folder = songFolder(song.id).apply { mkdirs() }
        File(folder, SONG_DOCUMENT).writeAtomically(SongFormat.encode(song))

        val entries = indexEntries()
        val lastPracticed = entries.find { it.id == song.id.value }?.lastPracticedEpochMillis
        writeIndex(entries.without(song.id) + IndexEntry(song.id.value, song.title, song.artist, lastPracticed))
    }

    fun library(): Library = Library(indexEntries().map { it.toLibraryEntry() })

    fun load(id: SongId): Song {
        val file = File(songFolder(id), SONG_DOCUMENT)
        if (!file.exists()) throw SongNotFoundException(id)
        return SongFormat.decode(file.readText())
    }

    /** Removes the Song and everything kept alongside it, such as its Practice settings. */
    fun delete(id: SongId) {
        writeIndex(indexEntries().without(id))
        songFolder(id).deleteRecursively()
    }

    /** One folder per Song holds its document and its Practice settings, so deleting it removes both. */
    private fun songFolder(id: SongId) = File(songsFolder, id.value)

    private val songsFolder get() = File(root, SONGS_FOLDER)

    private val indexFile get() = File(root, INDEX_FILE)

    /**
     * The index, rebuilt from the Song documents when it is missing, damaged, or out of step with
     * the Songs on disk (e.g. the app died between writing a Song and the index).
     */
    private fun indexEntries(): List<IndexEntry> {
        val stored = runCatching { indexJson.decodeFromString(indexSerializer, indexFile.readText()) }.getOrNull()
        val songIds = songsFolder.listFiles { file -> file.isDirectory }.orEmpty().map { it.name }.toSet()
        if (stored != null && stored.map { it.id }.toSet() == songIds) return stored

        val rebuilt = songIds.mapNotNull { id ->
            // A Song that can't be read is left out rather than breaking the whole Library.
            val song = runCatching { load(SongId(id)) }.getOrNull() ?: return@mapNotNull null
            IndexEntry(id, song.title, song.artist, stored?.find { it.id == id }?.lastPracticedEpochMillis)
        }
        writeIndex(rebuilt)
        return rebuilt
    }

    private fun writeIndex(entries: List<IndexEntry>) {
        root.mkdirs()
        indexFile.writeAtomically(indexJson.encodeToString(indexSerializer, entries))
    }
}

class SongNotFoundException(id: SongId) : NoSuchElementException("No Song with id ${id.value}")

@Serializable
private data class IndexEntry(val id: String, val title: String, val artist: String, val lastPracticedEpochMillis: Long?) {
    fun toLibraryEntry() = LibraryEntry(SongId(id), title, artist, lastPracticedEpochMillis?.let(Instant::ofEpochMilli))
}

private fun List<IndexEntry>.without(id: SongId) = filterNot { it.id == id.value }

private val indexJson = Json
private val indexSerializer = ListSerializer(IndexEntry.serializer())

/** Writes to a temporary file first so a crash never leaves a half-written file behind. */
internal fun File.writeAtomically(text: String) {
    val temporary = File(parentFile, "$name.tmp")
    temporary.writeText(text)
    check(temporary.renameTo(this)) { "Could not replace $this" }
}

private const val SONGS_FOLDER = "songs"
private const val SONG_DOCUMENT = "song.json"
private const val INDEX_FILE = "library.json"
