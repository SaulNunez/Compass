package com.saulnunez.compass.storage

import com.saulnunez.compass.domain.KeySignature
import com.saulnunez.compass.domain.Loop
import com.saulnunez.compass.domain.Measure
import com.saulnunez.compass.domain.PracticeSettings
import com.saulnunez.compass.domain.ShownViews
import com.saulnunez.compass.domain.SongId
import com.saulnunez.compass.domain.TempoPercentage
import com.saulnunez.compass.domain.Tempo
import com.saulnunez.compass.domain.TimeSignature
import com.saulnunez.compass.domain.TuningPreset
import com.saulnunez.compass.domain.newSong
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant

class PracticeSettingsStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val song = newSong(
        id = SongId("riff"),
        title = "Riff",
        artist = "Band",
        startingMeasure = Measure(TimeSignature(4, 4), KeySignature.C_MAJOR, Tempo(120)),
        trackName = "Guitar",
        instrument = TuningPreset.GUITAR_STANDARD.instrument(),
    )

    private val practiced = PracticeSettings(
        tempoPercentage = TempoPercentage(65),
        shownViews = ShownViews.BOTH,
        click = true,
        countIn = true,
    )

    @Test
    fun `saved Practice settings load back when the Song is opened again`() {
        SongStore(folder.root).apply {
            save(song)
            savePracticeSettings(song.id, practiced)
        }

        assertEquals(practiced, SongStore(folder.root).practiceSettings(song.id))
    }

    @Test
    fun `a Song never practiced has the default Practice settings`() {
        val store = SongStore(folder.root).apply { save(song) }

        assertEquals(PracticeSettings(), store.practiceSettings(song.id))
    }

    @Test
    fun `deleting a Song removes its Practice settings`() {
        val store = SongStore(folder.root)
        store.save(song)
        store.savePracticeSettings(song.id, practiced)

        store.delete(song.id)
        // Even a Song added again under the same id starts from the defaults.
        store.save(song)

        assertEquals(PracticeSettings(), store.practiceSettings(song.id))
        assertEquals(listOf("song.json"), folder.root.walk().filter { it.isFile && it.parentFile.name == "riff" }.map { it.name }.toList())
    }

    @Test
    fun `saving Practice settings never writes the Song document`() {
        val store = SongStore(folder.root).apply { save(song) }
        val document = folder.root.walk().single { it.name == "song.json" }
        val before = document.readBytes()
        document.setLastModified(0)

        store.savePracticeSettings(song.id, practiced)
        store.markPracticed(song.id, Instant.ofEpochMilli(1_000))

        assertArrayEquals(before, document.readBytes())
        assertEquals(0L, document.lastModified())
        assertEquals(song, store.load(song.id))
    }

    @Test
    fun `Practice settings can't be saved for a Song that isn't in the Library`() {
        val store = SongStore(folder.root)

        assertThrows(SongNotFoundException::class.java) { store.savePracticeSettings(song.id, practiced) }
        assertEquals(emptyList<Any>(), store.library().entries)
    }

    @Test
    fun `damaged Practice settings fall back to the defaults`() {
        val store = SongStore(folder.root).apply { save(song) }
        store.savePracticeSettings(song.id, practiced)
        folder.root.walk().single { it.name.startsWith("practice") }.writeText("{ not json")

        assertEquals(PracticeSettings(), store.practiceSettings(song.id))
    }

    @Test
    fun `practicing a Song sets its last-practiced time in the Library, and saving the Song keeps it`() {
        val store = SongStore(folder.root)
        store.save(song)
        store.save(song.copy(id = SongId("other"), title = "Other"))

        store.markPracticed(song.id, Instant.ofEpochMilli(5_000))
        store.save(song.copy(title = "Riff (edited)"))

        val lastPracticed = SongStore(folder.root).library().entries.associate { it.id.value to it.lastPracticed }
        assertEquals(mapOf("riff" to Instant.ofEpochMilli(5_000), "other" to null), lastPracticed)
    }

    @Test
    fun `practicing a Song that is no longer in the Library changes nothing`() {
        val store = SongStore(folder.root)

        store.markPracticed(song.id, Instant.ofEpochMilli(5_000))

        assertEquals(emptyList<Any>(), store.library().entries)
        assertEquals(emptyList<File>(), folder.root.listFiles { file -> file.isDirectory }.orEmpty().flatMap { it.listFiles().orEmpty().toList() })
    }

    @Test
    fun `a Backup carries each Song's Practice settings`() {
        val original = SongStore(folder.newFolder()).apply {
            save(song)
            savePracticeSettings(song.id, practiced)
        }
        val backup = ByteArrayOutputStream().also(original::backUp).toByteArray()

        val restored = SongStore(folder.newFolder())
        restored.restore(ByteArrayInputStream(backup))

        assertEquals(practiced, restored.practiceSettings(song.id))
    }

    @Test
    fun `a saved Loop is restored when the Song is opened again`() {
        val sectionLoop = practiced.copy(loop = Loop.OfSection(section = 2))
        val measureLoop = practiced.copy(loop = Loop.OfMeasures(first = 16, last = 23))
        val store = SongStore(folder.root).apply { save(song) }

        store.savePracticeSettings(song.id, sectionLoop)
        assertEquals(sectionLoop, SongStore(folder.root).practiceSettings(song.id))
        store.savePracticeSettings(song.id, measureLoop)
        assertEquals(measureLoop, SongStore(folder.root).practiceSettings(song.id))
    }

    @Test
    fun `Practice settings saved before Loops existed open with no Loop`() {
        val store = SongStore(folder.root).apply { save(song) }
        store.savePracticeSettings(song.id, practiced)
        folder.root.walk().single { it.name.startsWith("practice") }.writeText(
            """{"formatVersion":1,"tempoPercentage":65,"shownViews":"both","click":true,"countIn":true}""",
        )

        assertEquals(practiced, store.practiceSettings(song.id))
    }
}
