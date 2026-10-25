package com.saulnunez.compass.storage

import com.saulnunez.compass.domain.Library
import com.saulnunez.compass.domain.LibraryEntry
import com.saulnunez.compass.domain.PracticeSettings
import com.saulnunez.compass.domain.Song
import com.saulnunez.compass.domain.SongId
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.util.UUID

/**
 * Keeps each Song as one versioned JSON document under [root], plus a Library index
 * so the Library can be listed without loading every Song (ADR-0002).
 * Blocking file IO: call it off the main thread.
 */
class SongStore(
    private val root: File,
    /** Makes the id for a Song added as a copy. */
    private val newSongId: () -> SongId = { SongId(UUID.randomUUID().toString()) },
) {

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

    /** The Song's saved Practice settings, or the defaults if it has none (or they can't be read). */
    fun practiceSettings(id: SongId): PracticeSettings {
        val file = File(songFolder(id), PRACTICE_SETTINGS)
        return if (file.exists()) PracticeSettingsFormat.decodeOrDefault(file.readText()) else PracticeSettings()
    }

    /**
     * Saves the Song's Practice settings in its folder, beside its document, which stays untouched.
     * @throws SongNotFoundException if the Song is not in the Library (e.g. it was just deleted).
     */
    fun savePracticeSettings(id: SongId, settings: PracticeSettings) {
        val folder = songFolder(id)
        if (!File(folder, SONG_DOCUMENT).exists()) throw SongNotFoundException(id)
        File(folder, PRACTICE_SETTINGS).writeAtomically(PracticeSettingsFormat.encode(settings))
    }

    /** Records in the Library that the Song was practiced [at]; does nothing if the Song is gone. */
    fun markPracticed(id: SongId, at: Instant) {
        val entries = indexEntries()
        if (entries.none { it.id == id.value }) return
        writeIndex(entries.map { if (it.id == id.value) it.copy(lastPracticedEpochMillis = at.toEpochMilli()) else it })
    }

    private fun loadOrNull(id: SongId): Song? = runCatching { load(id) }.getOrNull()

    /** Removes the Song and everything kept alongside it, such as its Practice settings. */
    fun delete(id: SongId) {
        writeIndex(indexEntries().without(id))
        songFolder(id).deleteRecursively()
    }

    /**
     * Adds [song] to the Library without ever overwriting a Song already there.
     * Backup restore and Song file import both go through here.
     */
    fun add(song: Song): AddResult = add(song, companions = emptyMap())

    /** Like [add], also writing [companions] (files kept beside the Song, such as Practice settings) when it is added. */
    private fun add(song: Song, companions: Map<String, ByteArray>): AddResult {
        identicalSongId(song)?.let { return AddResult.AlreadyPresent(it) }

        // Any folder already using the id, even one whose Song can't be read, is left alone.
        val isNew = !songFolder(song.id).exists()
        val id = if (isNew) song.id else generateSequence(newSongId).first { !songFolder(it).exists() }
        val folder = songFolder(id).apply { mkdirs() }
        // The document goes last: a folder without one is ignored, so a crash never adds half a Song.
        companions.forEach { (name, bytes) -> File(folder, name).writeAtomically(bytes) }
        save(song.copy(id = id))
        return if (isNew) AddResult.Added(id) else AddResult.AddedAsCopy(id)
    }

    /**
     * The Song in the Library that [song] would duplicate: the one with its id if unchanged, or a copy
     * of it added earlier (a copy keeps the title and artist, so only those Songs need loading).
     */
    private fun identicalSongId(song: Song): SongId? {
        if (loadOrNull(song.id) == song) return song.id
        return indexEntries()
            .filter { it.title == song.title && it.artist == song.artist }
            .map { SongId(it.id) }
            .firstOrNull { loadOrNull(it) == song.copy(id = it) }
    }

    /** Writes the whole Library to [output] as a Backup zip. Does not close [output]. */
    fun backUp(output: OutputStream) {
        val folders = songsFolder.listFiles { file -> file.isDirectory }.orEmpty().associate { folder ->
            folder.name to folder.listFiles { file -> file.isFile && !file.name.endsWith(".tmp") }.orEmpty()
                .associate { it.name to it.readBytes() }
        }
        writeBackup(folders, NonClosingOutputStream(output))
    }

    /**
     * Adds every Song in the Backup read from [input] to the Library by the rules of [add], then closes [input].
     * @throws NotABackupException if [input] is not a Compass Backup.
     */
    fun restore(input: InputStream): RestoreReport {
        val results = readBackup(input).map { (_, files) ->
            val document = files[SONG_DOCUMENT] ?: return@map null
            val song = runCatching { SongFormat.decode(document.decodeToString()) }.getOrNull() ?: return@map null
            add(song, files - SONG_DOCUMENT)
        }
        return RestoreReport(results.filterNotNull(), unreadable = results.count { it == null })
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

/** What [SongStore.add] did with a Song; [id] is where that Song now is in the Library. */
sealed interface AddResult {
    val id: SongId

    /** The Song was new to the Library. */
    data class Added(override val id: SongId) : AddResult

    /** An identical Song was already in the Library, so nothing changed. */
    data class AlreadyPresent(override val id: SongId) : AddResult

    /** A different version of the Song was already in the Library, so this one was added beside it under a new id. */
    data class AddedAsCopy(override val id: SongId) : AddResult
}

/** What restoring a Backup did with each Song in it. */
data class RestoreReport(val results: List<AddResult>, val unreadable: Int) {
    val added get() = results.count { it is AddResult.Added }
    val addedAsCopies get() = results.count { it is AddResult.AddedAsCopy }
    val alreadyPresent get() = results.count { it is AddResult.AlreadyPresent }
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
internal fun File.writeAtomically(text: String) = writeAtomically(text.toByteArray())

internal fun File.writeAtomically(bytes: ByteArray) {
    val temporary = File(parentFile, "$name.tmp")
    temporary.writeBytes(bytes)
    check(temporary.renameTo(this)) { "Could not replace $this" }
}

/** Lets a zip be finished without closing the caller's stream. */
private class NonClosingOutputStream(output: OutputStream) : FilterOutputStream(output) {
    override fun write(b: ByteArray, off: Int, len: Int) = out.write(b, off, len)
    override fun close() = flush()
}

private const val SONGS_FOLDER = "songs"
private const val SONG_DOCUMENT = "song.json"
private const val PRACTICE_SETTINGS = "practice-settings.json"
private const val INDEX_FILE = "library.json"
