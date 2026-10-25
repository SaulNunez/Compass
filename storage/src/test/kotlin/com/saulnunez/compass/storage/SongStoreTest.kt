package com.saulnunez.compass.storage

import com.saulnunez.compass.domain.Duration
import com.saulnunez.compass.domain.KeySignature
import com.saulnunez.compass.domain.Measure
import com.saulnunez.compass.domain.Note
import com.saulnunez.compass.domain.Rest
import com.saulnunez.compass.domain.Song
import com.saulnunez.compass.domain.SongId
import com.saulnunez.compass.domain.Tempo
import com.saulnunez.compass.domain.TimeSignature
import com.saulnunez.compass.domain.Track
import com.saulnunez.compass.domain.TuningPreset
import com.saulnunez.compass.domain.Voice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SongStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val song = Song(
        id = SongId("riff"),
        title = "Riff",
        artist = "Band",
        measures = listOf(
            Measure(TimeSignature(4, 4), KeySignature(fifths = 1, isMinor = true), Tempo(120)),
            Measure(TimeSignature(7, 8), KeySignature(fifths = -3), Tempo(96)),
        ),
        tracks = listOf(
            Track(
                name = "Guitar",
                instrument = TuningPreset.GUITAR_DROP_D.instrument(),
                voice = Voice(
                    listOf(
                        listOf(Note(6, 0, Duration.QUARTER), Rest(Duration.QUARTER), Note(1, 12, Duration.HALF)),
                        emptyList(),
                    ),
                ),
            ),
            Track(
                name = "Bass",
                instrument = TuningPreset.BASS_FIVE_STRING_STANDARD.instrument(),
                voice = Voice(listOf(listOf(Note(5, 3, Duration.WHOLE)), listOf(Rest(Duration.THIRTY_SECOND)))),
            ),
        ),
    )

    @Test
    fun `a saved Song loads back unchanged`() {
        val store = SongStore(folder.root)
        store.save(song)

        assertEquals(song, SongStore(folder.root).load(SongId("riff")))
    }

    @Test
    fun `the Library lists each saved Song once, with its latest title and artist`() {
        val store = SongStore(folder.root)
        store.save(song)
        store.save(song.copy(id = SongId("other"), title = "Other", artist = "Someone"))
        store.save(song.copy(title = "Riff (live)"))

        val entries = SongStore(folder.root).library().entries.sortedBy { it.id.value }
        assertEquals(listOf("other", "riff"), entries.map { it.id.value })
        assertEquals(listOf("Other", "Riff (live)"), entries.map { it.title })
        assertEquals(listOf("Someone", "Band"), entries.map { it.artist })
        assertEquals(listOf(null, null), entries.map { it.lastPracticed })
    }

    @Test
    fun `a deleted Song leaves the Library and can no longer be loaded`() {
        val store = SongStore(folder.root)
        store.save(song)
        store.save(song.copy(id = SongId("keep")))

        store.delete(SongId("riff"))

        assertEquals(listOf(SongId("keep")), store.library().entries.map { it.id })
        assertThrows(SongNotFoundException::class.java) { store.load(SongId("riff")) }
    }

    // The next tests damage files on disk on purpose, so they know the store's file layout.
    private val indexFile get() = File(folder.root, "library.json")

    @Test
    fun `a damaged Library index is rebuilt from the saved Songs`() {
        val store = SongStore(folder.root)
        store.save(song)
        store.save(song.copy(id = SongId("other"), title = "Other"))
        indexFile.writeText("not json")

        assertEquals(listOf("Other", "Riff"), store.library().entries.map { it.title }.sorted())
    }

    @Test
    fun `a Song saved without reaching the index is still listed`() {
        val store = SongStore(folder.root)
        store.save(song)
        val indexBeforeSecondSave = indexFile.readText()
        store.save(song.copy(id = SongId("other"), title = "Other"))
        indexFile.writeText(indexBeforeSecondSave) // as if the app died between writing the Song and the index

        assertEquals(listOf("Other", "Riff"), store.library().entries.map { it.title }.sorted())
    }

    @Test
    fun `a Song document that can't be read is left out instead of breaking the Library`() {
        val store = SongStore(folder.root)
        store.save(song)
        store.save(song.copy(id = SongId("other"), title = "Other"))
        File(folder.root, "songs/other/song.json").writeText("not json")
        indexFile.delete()

        assertEquals(listOf("Riff"), store.library().entries.map { it.title })
    }

    @Test
    fun `an empty store has an empty Library`() {
        assertEquals(emptyList<Any>(), SongStore(folder.root).library().entries)
    }
}
