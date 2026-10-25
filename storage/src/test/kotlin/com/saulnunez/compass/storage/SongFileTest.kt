package com.saulnunez.compass.storage

import com.saulnunez.compass.domain.KeySignature
import com.saulnunez.compass.domain.Measure
import com.saulnunez.compass.domain.SongId
import com.saulnunez.compass.domain.Tempo
import com.saulnunez.compass.domain.TimeSignature
import com.saulnunez.compass.domain.TuningPreset
import com.saulnunez.compass.domain.newSong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream

class SongFileTest {

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

    private fun SongStore.songFileBytes(id: String): ByteArray =
        ByteArrayOutputStream().also { exportSongFile(SongId(id), it) }.toByteArray()

    @Test
    fun `a Song exported from one Library and imported into another arrives unchanged`() {
        val sender = newStore()
        sender.save(song("a", "Alpha"))
        val receiver = newStore()

        val result = receiver.importSongFile(sender.songFileBytes("a").inputStream())

        assertEquals(AddResult.Added(SongId("a")), result)
        assertEquals(song("a", "Alpha"), receiver.load(SongId("a")))
        assertEquals(listOf("Alpha"), receiver.library().entries.map { it.title })
    }

    @Test
    fun `importing a Song already in the Library unchanged adds nothing`() {
        val store = newStore()
        store.save(song("a", "Alpha"))

        val result = store.importSongFile(store.songFileBytes("a").inputStream())

        assertEquals(AddResult.AlreadyPresent(SongId("a")), result)
        assertEquals(1, store.library().entries.size)
    }

    @Test
    fun `importing a changed version of a Song adds it as a copy and keeps the local one`() {
        val store = newStore()
        store.save(song("a", "Alpha"))
        val songFile = store.songFileBytes("a")
        store.save(song("a", "Alpha (edited)"))

        val result = store.importSongFile(songFile.inputStream())

        assertTrue(result is AddResult.AddedAsCopy)
        assertEquals(song("a", "Alpha (edited)"), store.load(SongId("a")))
        assertEquals(song("a", "Alpha").copy(id = result.id), store.load(result.id))
    }

    @Test
    fun `a file that is not a Song file is refused and changes nothing`() {
        val store = newStore()
        store.save(song("a"))

        listOf("not json", """{"hello":"world"}""", """{"formatVersion":1,"title":"No tracks"}""").forEach { text ->
            assertThrows(NotASongFileException::class.java) { store.importSongFile(text.byteInputStream()) }
        }
        assertEquals(listOf(SongId("a")), store.library().entries.map { it.id })
    }

    @Test
    fun `a Song file from a newer Compass is refused as such`() {
        val sender = newStore()
        sender.save(song("a"))
        val fromTheFuture = sender.songFileBytes("a").decodeToString()
            .replace(Regex("\"formatVersion\":\\s*\\d+"), "\"formatVersion\":99")
        val store = newStore()

        val refusal = assertThrows(UnsupportedSongFormatException::class.java) {
            store.importSongFile(fromTheFuture.byteInputStream())
        }
        assertEquals(99, refusal.formatVersion)
        assertTrue(store.library().entries.isEmpty())
    }

    @Test
    fun `a Song file is named after its Song's title, safely`() {
        assertEquals("Blackbird.compass.json", songFileName("Blackbird"))
        assertEquals("AC_DC_ Back in Black_.compass.json", songFileName("AC/DC: Back in Black?"))
        assertEquals("Song.compass.json", songFileName("  "))
    }
}
