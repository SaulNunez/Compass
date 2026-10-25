# Compass

An app for writing, playing back and practicing guitar and bass music, shown as tablature and standard notation.

## Music

**Song**:
A single piece of music made of one or more Tracks that play together.
_Avoid_: Tab, score, sheet, file

**Track**:
One instrument's part within a Song, played on exactly one Instrument.
_Avoid_: Part, voice, layer

**Instrument**:
A guitar or bass, defined by its kind, its number of strings and its Tuning.
_Avoid_: Axe

**Tuning**:
The pitch each open string of an Instrument is set to, e.g. standard, Drop D, half-step down.

**Note**:
A sound played on one string at one fret for a duration; its pitch follows from the string's Tuning, not the other way round.
_Avoid_: Fret, tab number

**Chord**:
Several Notes in the same Track that start together on different strings.
_Avoid_: Beat, stack

**Technique**:
A way a Note is played beyond its string, fret and duration, e.g. hammer-on, slide, bend, palm mute, slap.
_Avoid_: Effect, articulation, ornament

**Measure**:
A span of a Song with one time signature, key signature and Tempo, shared by every Track; Songs are written out Measure by Measure in full.
_Avoid_: Bar

**Tempo**:
The written speed of a Song in beats per minute, set at the first Measure and changeable at any later Measure. Part of the Song, unlike the practice tempo percentage.
_Avoid_: Speed, BPM (as a noun for the setting)

**Voice**:
A single rhythmic line within a Track; every Track has exactly one.

**Section**:
A named, contiguous range of Measures in a Song, e.g. Intro, Verse, Chorus.
_Avoid_: Part, segment, marker

**Sound**:
The synthesized timbre a Track uses during Playback, e.g. distorted electric guitar or slap bass. Affects only how the Track sounds, not what is written.
_Avoid_: Patch, preset, program, instrument (Instrument is the guitar or bass itself)

## Views

**Tablature**:
A view of a Track that draws one line per string and shows each note as the fret to press.
_Avoid_: Tabs, TAB (as a noun for the whole Song)

**Standard notation**:
A view of a Track that draws notes on a five-line staff by pitch and duration.
_Avoid_: Partiture, partitura, score, sheet music

## Modes

**Practice mode**:
The read-only way of opening a Song for reading and playing along; the Song cannot be changed in it. Songs always open in Practice mode.
_Avoid_: Read mode, locked, view mode

**Edit mode**:
The way of opening a Song in which its Tracks can be changed, entered only by a deliberate switch from Practice mode.
_Avoid_: Write mode, unlocked

**Practice settings**:
A person's saved setup for practicing one Song: tempo percentage, Loop, muted and soloed Tracks, the Track being read and which views are shown. Kept apart from the Song, never part of it.
_Avoid_: Song settings, preferences

**Library**:
The list of all Songs on the device.
_Avoid_: Collection, songbook

**Backup**:
A single file holding the whole Library, restorable on any device without overwriting existing Songs.
_Avoid_: Export (reserved for a single Song)

**Song file**:
A single Song exported as a file so it can be sent to someone else and imported into their Library.
_Avoid_: Backup, share

**Playback**:
The app sounding a Song with synthesized instruments while a cursor follows along in the views.
_Avoid_: Preview, audio, play-along

**Loop**:
A range of Measures that Playback repeats until stopped, anchored either to a Section (and following it) or to a Measure range (and adjusting as Measures are inserted or deleted).
_Avoid_: Repeat (reserved for written repeat signs, which Compass does not have)
