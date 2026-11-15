# A Pickup's length is stored on the Song, not taken from what is written

A Song's first Measure can be a Pickup, shorter than its time signature. We store the Pickup's length explicitly on the Song (a multiple of a 16th note, shorter than the first Measure's time signature). We do not work it out from the Notes written in the Pickup, which is how alphaTab and Guitar Pro handle an anacrusis. The Pickup is shared by every Track, so its length can't depend on what any one Track has written. With an explicit length, an empty Song or a half-written Track can't change the Pickup, and the first Measure's incomplete/overfull check has something fixed to compare against.

## Considered Options

- **Length taken from the longest Track's first Measure (alphaTab's `isAnacrusis` approach)**: needs no new field. But the Pickup's length would change while someone is still writing it, an empty Song couldn't have a Pickup, and the incomplete/overfull check couldn't work for the first Measure.
- **A lead-in before Measure 1 that has no Measure of its own**: would break the rule that every Track has every Measure, and would need special cases in Loops, Sections and Restructure.

## Consequences

- The saved Song format gets a version bump and a migration. Existing Songs have no Pickup.
- When translating a Song for alphaTab, the first Measure is marked `isAnacrusis`, and a Track that writes less than the Pickup gets Rests before its Notes, up to the Pickup's length, because alphaTab plays an anacrusis bar as long as its longest Track. Playback does the same, so a short Track's Notes fall at the end of the Pickup. A Track that overfills the Pickup still makes alphaTab's Pickup longer than Compass's until it is fixed; Compass flags it as overfull.
- alphaTab numbers bars by position and can't renumber them, so it would call the Pickup bar 1. Songs with a Pickup show no alphaTab bar numbers; Compass's own labels number from the first full Measure.
- alphaTab's count-in is always one full Measure, and Compass can only switch it on or off. So before a Pickup, Playback counts in one full Measure and the Pickup comes straight after, not on its own beats.
- Importing from Guitar Pro or MusicXML later has to choose a Pickup length from what the Pickup contains.
