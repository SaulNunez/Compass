package com.saulnunez.compass.storage

import com.saulnunez.compass.domain.KeySignature
import com.saulnunez.compass.domain.Measure
import com.saulnunez.compass.domain.Song
import com.saulnunez.compass.domain.SongId
import com.saulnunez.compass.domain.Tempo
import com.saulnunez.compass.domain.TimeSignature
import com.saulnunez.compass.domain.TuningPreset
import com.saulnunez.compass.domain.newSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class BackupTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun song(id: String, title: String = id) = newSong(
        id = SongId(id),
        title = title,
        artist = "Band",
        startingMeasure = Measure(TimeSignature(4, 4), KeySignature.C_MAJOR, Tempo(120)),
        trackName = "Guitar",
        instrument = TuningPreset.GUITAR_STANDARD.instrument(),
    )

    private fun newStore() = SongStore(folder.newFolder())

    private fun SongStore.backupBytes(): ByteArray = ByteArrayOutputStream().also { backUp(it) }.toByteArray()

    private fun SongStore.songs(): List<Song> = library().entries.map { load(it.id) }.sortedBy { it.title }

    @Test
    fun `restoring into an empty Library brings back every Song`() {
        val original = newStore()
        original.save(song("a", "Alpha"))
        original.save(song("b", "Beta"))

        val restored = newStore()
        restored.restore(original.backupBytes().inputStream())

        assertEquals(listOf(song("a", "Alpha"), song("b", "Beta")), restored.songs())
    }

    @Test
    fun `restore skips Songs that are already in the Library unchanged`() {
        val store = newStore()
        store.save(song("a", "Alpha"))
        val backup = store.backupBytes()

        val report = store.restore(backup.inputStream())

        assertEquals(listOf(song("a", "Alpha")), store.songs())
        assertEquals(listOf(AddResult.AlreadyPresent(SongId("a"))), report.results)
    }

    @Test
    fun `a Song changed since the Backup comes back as a copy and the local version is kept`() {
        val store = newStore()
        store.save(song("a", "Alpha"))
        val backup = store.backupBytes()
        val edited = song("a", "Alpha (edited)")
        store.save(edited)

        val report = store.restore(backup.inputStream())

        val copy = report.results.single()
        assertTrue(copy is AddResult.AddedAsCopy)
        assertNotEquals(SongId("a"), copy.id)
        assertEquals(edited, store.load(SongId("a")))
        assertEquals(song("a", "Alpha").copy(id = copy.id), store.load(copy.id))
        assertEquals(2, store.library().entries.size)
    }

    @Test
    fun `restoring the same Backup again adds no further copies`() {
        val store = newStore()
        store.save(song("a", "Alpha"))
        val backup = store.backupBytes()
        store.save(song("a", "Alpha (edited)"))
        store.restore(backup.inputStream())

        val report = store.restore(backup.inputStream())

        assertTrue(report.results.single() is AddResult.AlreadyPresent)
        assertEquals(2, store.library().entries.size)
    }

    // Practice settings don't exist yet; a Backup carries whatever is kept in a Song's folder.
    private fun File.practiceFile(id: String) = File(this, "songs/$id/practice.json")

    @Test
    fun `files kept beside a Song, such as its Practice settings, are restored with it`() {
        val originalRoot = folder.newFolder()
        val original = SongStore(originalRoot)
        original.save(song("a"))
        originalRoot.practiceFile("a").writeText("""{"tempoPercentage":75}""")
        val restoredRoot = folder.newFolder()

        SongStore(restoredRoot).restore(original.backupBytes().inputStream())

        assertEquals("""{"tempoPercentage":75}""", restoredRoot.practiceFile("a").readText())
    }

    @Test
    fun `restore never overwrites what is kept beside a Song already in the Library`() {
        val root = folder.newFolder()
        val store = SongStore(root)
        store.save(song("a"))
        root.practiceFile("a").writeText("backed up")
        val backup = store.backupBytes()
        root.practiceFile("a").writeText("changed since")

        store.restore(backup.inputStream())

        assertEquals("changed since", root.practiceFile("a").readText())
    }

    @Test
    fun `a copy brings the backed-up files kept beside its Song`() {
        val root = folder.newFolder()
        val store = SongStore(root)
        store.save(song("a"))
        root.practiceFile("a").writeText("backed up")
        val backup = store.backupBytes()
        store.save(song("a", "Edited"))

        val copy = store.restore(backup.inputStream()).results.single()

        assertEquals("backed up", root.practiceFile(copy.id.value).readText())
    }

    /** A Backup written by hand, as an older or newer Compass might have written it. */
    private fun handWrittenBackup(vararg entries: Pair<String, String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            (listOf("compass-backup.json" to """{"formatVersion":1}""") + entries).forEach { (name, text) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(text.toByteArray())
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private val versionOneDocument = """
        {
          "formatVersion": 1,
          "id": "riff",
          "title": "Riff",
          "artist": "Band",
          "measures": [{ "beats": 3, "beatUnit": 4, "keyFifths": 0, "keyIsMinor": false, "tempo": 90 }],
          "tracks": [{
            "name": "Bass",
            "instrument": "bass",
            "openStrings": [43, 38, 33, 28],
            "measures": [[{ "type": "note", "string": 4, "fret": 3, "duration": "half" }]]
          }]
        }
    """.trimIndent()

    @Test
    fun `a Song saved by an older Compass is brought up to date on restore`() {
        val store = newStore()

        val report = store.restore(handWrittenBackup("songs/riff/song.json" to versionOneDocument).inputStream())

        assertEquals(listOf(AddResult.Added(SongId("riff"))), report.results)
        assertEquals(SongFormat.decode(versionOneDocument), store.load(SongId("riff")))
    }

    @Test
    fun `Songs that can't be read are counted and the rest are still restored`() {
        val store = newStore()
        val fromTheFuture = versionOneDocument.replace("\"formatVersion\": 1", "\"formatVersion\": 99").replace("riff", "future")

        val report = store.restore(
            handWrittenBackup(
                "songs/riff/song.json" to versionOneDocument,
                "songs/future/song.json" to fromTheFuture,
                "songs/broken/song.json" to "not json",
            ).inputStream(),
        )

        assertEquals(2, report.unreadable)
        assertEquals(listOf(SongId("riff")), store.library().entries.map { it.id })
    }

    @Test
    fun `a file that is not a Backup is refused and changes nothing`() {
        val store = newStore()
        store.save(song("a"))

        assertThrows(NotABackupException::class.java) { store.restore("not a zip".byteInputStream()) }
        assertEquals(listOf(song("a")), store.songs())
    }

    @Test
    fun `a damaged Backup is refused`() {
        val original = newStore()
        original.save(song("a"))
        val backup = original.backupBytes()

        assertThrows(NotABackupException::class.java) {
            newStore().restore(backup.copyOf(backup.size / 2).inputStream())
        }
    }

    @Test
    fun `zip entries that try to escape the Library are ignored`() {
        val root = folder.newFolder()
        val store = SongStore(root)

        store.restore(
            handWrittenBackup(
                "songs/riff/song.json" to versionOneDocument,
                "songs/riff/../../escaped.json" to "x",
                "../escaped.json" to "x",
            ).inputStream(),
        )

        assertEquals(listOf(SongId("riff")), store.library().entries.map { it.id })
        assertFalse(File(root, "escaped.json").exists())
        assertFalse(File(root.parentFile, "escaped.json").exists())
    }
}
