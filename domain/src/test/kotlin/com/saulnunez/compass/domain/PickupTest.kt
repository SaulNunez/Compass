package com.saulnunez.compass.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PickupTest {

    private val fourFour = Measure(TimeSignature(4, 4), KeySignature.C_MAJOR, Tempo(120))

    private val song = newSong(
        id = SongId("pickup"),
        title = "Pickup",
        artist = "",
        startingMeasure = fourFour,
        trackName = "Guitar",
        instrument = TuningPreset.GUITAR_STANDARD.instrument(),
    )

    private val oneBeat = Pickup(sixteenths = 4)

    @Test
    fun `a one-beat Pickup is full with a single quarter Note`() {
        val session = EditSession.start(song).typeDigit(3).makePickup(oneBeat)

        assertEquals(oneBeat, session.song.pickup)
        assertEquals(MeasureFill.FULL, session.song.measureFill(track = 0, measure = 0))
        assertEquals(MeasureFill.UNWRITTEN, session.song.measureFill(track = 0, measure = 1))
    }

    @Test
    fun `removing the Pickup makes the first Measure full length again, keeping its Notes`() {
        val session = EditSession.start(song).typeDigit(3).makePickup(oneBeat).removePickup()

        assertEquals(null, session.song.pickup)
        assertEquals(listOf(Note(1, 3, Duration.QUARTER)), session.song.tracks[0].voice.measures[0])
        assertEquals(MeasureFill.INCOMPLETE, session.song.measureFill(track = 0, measure = 0))
    }

    @Test
    fun `a Pickup too long for the first Measure is refused, and Notes past a shorter one are kept as overfull`() {
        val written = EditSession.start(song).typeDigit(3).moveToNextBeat().typeDigit(5)

        assertEquals(false, written.canMakePickup(Pickup(sixteenths = 16)))
        assertEquals(written.song, written.makePickup(Pickup(sixteenths = 16)).song)

        val pickup = written.makePickup(oneBeat).song
        assertEquals(2, pickup.tracks[0].voice.measures[0].size)
        assertEquals(MeasureFill.OVERFULL, pickup.measureFill(track = 0, measure = 0))
    }

    @Test
    fun `no Measure goes in before the Pickup, but one can go straight after it`() {
        val pickup = EditSession.start(song).typeDigit(3).makePickup(oneBeat)

        assertEquals(false, pickup.canInsertMeasureBefore)
        assertEquals(pickup.song, pickup.insertMeasureBefore().song)

        val after = pickup.insertMeasureAfter().song
        assertEquals(5, after.measures.size)
        assertEquals(oneBeat, after.pickup)
        assertEquals(listOf(Note(1, 3, Duration.QUARTER)), after.tracks[0].voice.measures[0])
        assertEquals(emptyList<VoiceElement>(), after.tracks[0].voice.measures[1])
    }

    @Test
    fun `copied Measures are not pasted before the Pickup`() {
        val copied = EditSession.start(song).typeDigit(3).makePickup(oneBeat)
            .moveToMeasure(1).addSection("Verse", 1, 2).copySection()
            .moveToMeasure(0)

        assertEquals(false, copied.canPaste)
        assertEquals(copied.song, copied.pasteMeasures().song)
        assertEquals(true, copied.moveToMeasure(1).canPaste)
    }

    @Test
    fun `the Pickup's time signature can't change to one the Pickup would fill`() {
        val threeBeats = EditSession.start(song).makePickup(Pickup(sixteenths = 12))
        val threeFour = fourFour.copy(timeSignature = TimeSignature(3, 4))

        assertEquals(false, threeBeats.canChangeMeasure(threeFour))
        assertEquals(threeBeats.song, threeBeats.changeMeasure(threeFour).song)

        val fiveFour = fourFour.copy(timeSignature = TimeSignature(5, 4))
        assertEquals(true, threeBeats.canChangeMeasure(fiveFour))
        assertEquals(TimeSignature(5, 4), threeBeats.changeMeasure(fiveFour).song.measures[0].timeSignature)
        assertEquals(true, threeBeats.moveToMeasure(1).canChangeMeasure(threeFour))
    }

    @Test
    fun `deleting the Pickup's Measure removes the Pickup, and the next Measure is a full first Measure`() {
        val deleted = EditSession.start(song).makePickup(oneBeat).deleteMeasure().song

        assertEquals(3, deleted.measures.size)
        assertEquals(null, deleted.pickup)
    }
}
