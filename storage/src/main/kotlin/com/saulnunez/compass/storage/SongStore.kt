package com.saulnunez.compass.storage

import com.saulnunez.compass.domain.Library
import com.saulnunez.compass.domain.LibraryEntry
import com.saulnunez.compass.domain.Song
import com.saulnunez.compass.domain.SongId
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
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
        File(folder, SONG_FILE).writeAtomically(SongFormat.encode(song))

        val entries = readIndex()
        val lastPracticed = entries.find { it.id == song.id.value }?.lastPracticedEpochMillis
        writeIndex(entries.filterNot { it.id == song.id.value } + IndexEntry(song.id.value, song.title, song.artist, lastPracticed))
    }

    fun library(): Library = Library(readIndex().map { it.toLibraryEntry() })

    fun load(id: SongId): Song {
        val file = File(songFolder(id), SONG_FILE)
        if (!file.exists()) throw SongNotFoundException(id)
        return SongFormat.decode(file.readText())
    }

    /** Removes the Song and everything kept alongside it, such as its Practice settings. */
    fun delete(id: SongId) {
        writeIndex(readIndex().filterNot { it.id == id.value })
        songFolder(id).deleteRecursively()
    }

    /** One folder per Song holds its document and its Practice settings, so deleting it removes both. */
    private fun songFolder(id: SongId) = File(File(root, SONGS_FOLDER), id.value)

    private val indexFile get() = File(root, INDEX_FILE)

    private fun readIndex(): List<IndexEntry> =
        if (indexFile.exists()) json.decodeFromString(indexSerializer, indexFile.readText()) else emptyList()

    private fun writeIndex(entries: List<IndexEntry>) {
        root.mkdirs()
        indexFile.writeAtomically(json.encodeToString(indexSerializer, entries))
    }
}

class SongNotFoundException(id: SongId) : NoSuchElementException("No Song with id ${id.value}")

@Serializable
private data class IndexEntry(val id: String, val title: String, val artist: String, val lastPracticedEpochMillis: Long?) {
    fun toLibraryEntry() = LibraryEntry(SongId(id), title, artist, lastPracticedEpochMillis?.let(Instant::ofEpochMilli))
}

private val indexSerializer = ListSerializer(IndexEntry.serializer())

/** Writes to a temporary file first so a crash never leaves a half-written file behind. */
internal fun File.writeAtomically(text: String) {
    val temporary = File(parentFile, "$name.tmp")
    temporary.writeText(text)
    check(temporary.renameTo(this)) { "Could not replace $this" }
}

private const val SONGS_FOLDER = "songs"
private const val SONG_FILE = "song.json"
private const val INDEX_FILE = "library.json"
