package com.saulnunez.compass.storage

import com.saulnunez.compass.domain.SongId
import java.io.InputStream
import java.io.OutputStream

/*
 * A Song file is a single Song's document in Compass's versioned format (SongFormat), for sending
 * to someone else. Practice settings stay behind: they belong to the person, not to the Song.
 */

/** Writes the Song with [id] to [output] as a Song file. Does not close [output]. */
fun SongStore.exportSongFile(id: SongId, output: OutputStream) {
    output.write(SongFormat.encode(load(id)).toByteArray())
    output.flush()
}

/**
 * Adds the Song in the Song file read from [input] to the Library by the rules of [SongStore.add]
 * (the same as restoring a Backup), then closes [input].
 * @throws NotASongFileException if [input] is not a Compass Song file, or is damaged.
 * @throws UnsupportedSongFormatException if the Song file was made by a newer Compass.
 */
fun SongStore.importSongFile(input: InputStream): AddResult {
    val text = input.use { it.readBytes().decodeToString() }
    val song = try {
        SongFormat.decode(text)
    } catch (e: UnsupportedSongFormatException) {
        throw e
    } catch (e: RuntimeException) {
        // Malformed JSON, missing fields and impossible values all mean the same thing to the person importing.
        throw NotASongFileException(e)
    }
    return add(song)
}

/**
 * A file name for a Song file of the Song titled [title], safe on any file system; [untitled] is
 * used when the title has nothing usable in it.
 */
fun songFileName(title: String, untitled: String = "Song"): String {
    val safe = title.replace(UNSAFE_FILE_NAME_CHARACTERS, "_").trim().take(MAX_FILE_NAME_TITLE_LENGTH).trim()
    return (safe.ifEmpty { untitled }) + SONG_FILE_EXTENSION
}

const val SONG_FILE_EXTENSION = ".compass.json"

private val UNSAFE_FILE_NAME_CHARACTERS = Regex("""[\\/:*?"<>|\p{Cntrl}]""")
private const val MAX_FILE_NAME_TITLE_LENGTH = 80

/** The file chosen to import is not a Compass Song file, or is damaged. */
class NotASongFileException(cause: Throwable? = null) : IllegalArgumentException("Not a Compass Song file", cause)
