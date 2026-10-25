package com.saulnunez.compass.storage

import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/*
 * A Backup is a zip holding a marker file plus every Song's folder as it is on disk
 * (`songs/<id>/song.json` and whatever is kept beside it, such as Practice settings).
 */

internal fun writeBackup(songFolders: Map<String, Map<String, ByteArray>>, output: OutputStream) {
    ZipOutputStream(output).use { zip ->
        zip.putNextEntry(ZipEntry(MARKER_ENTRY))
        zip.write(MARKER_CONTENT.toByteArray())
        zip.closeEntry()
        songFolders.forEach { (id, files) ->
            files.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry("$SONGS_PREFIX$id/$name"))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
    }
}

/** Every Song folder in the Backup, by Song id, as file name to contents. */
internal fun readBackup(input: InputStream): Map<String, Map<String, ByteArray>> {
    val folders = mutableMapOf<String, MutableMap<String, ByteArray>>()
    var isBackup = false
    // Entry names are only matched against a strict pattern, never used as paths.
    // Everything is read before anything is added, so a damaged Backup changes nothing.
    try {
        ZipInputStream(input).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                if (entry.name == MARKER_ENTRY) isBackup = true
                val match = SONG_FILE_ENTRY.matchEntire(entry.name) ?: return@forEach
                val (id, name) = match.destructured
                folders.getOrPut(id) { mutableMapOf() }[name] = zip.readBytes()
            }
        }
    } catch (e: IOException) {
        throw NotABackupException(e)
    }
    if (!isBackup) throw NotABackupException()
    return folders
}

/** The file chosen to restore from is not a Compass Backup, or is damaged. */
class NotABackupException(cause: Throwable? = null) : IllegalArgumentException("Not a Compass Backup", cause)

private const val MARKER_ENTRY = "compass-backup.json"
private const val MARKER_CONTENT = """{"formatVersion":1}"""
private const val SONGS_PREFIX = "songs/"
private val SONG_FILE_ENTRY = Regex("songs/([A-Za-z0-9_-]{1,64})/([A-Za-z0-9][A-Za-z0-9._-]{0,127})")
