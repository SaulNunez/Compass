# alphaTab renders and plays; Compass owns the Song model

A Song's music is stored once (Notes as string + fret, in Measures, in Tracks) and shown as both Tablature and Standard notation, rather than keeping tab and notation as separate documents. Drawing that notation and synthesizing Playback is a multi-month effort on its own, so we use alphaTab (`net.alphatab:alphaTab-android`, MPL-2.0) purely as a renderer and synthesizer: Compass's own Song model is the source of truth and is translated into alphaTab's model whenever it is displayed or played. Editing, the toolbar and Practice mode controls are Compass's own Compose UI around the alphaTab view.

## Considered Options

- **Build our own renderer and synth in Compose Canvas**: full control, but notation layout alone would dominate v1.
- **Adopt alphaTab's model as our domain model**: fastest start, but our vocabulary and invariants (string + fret is primary, Measures shared across Tracks) would be dictated by the library, and swapping renderers later would mean rewriting the domain.

## Consequences

- A translation layer (Compass Song → alphaTab Score) must be kept in step with every Technique and Measure feature we add.
- alphaTab's Android target is newer than its web target; rendering bugs may need upstream fixes or workarounds.
- alphaTab's Android library (`net.alphatab:alphaTab` 1.8.x) requires API 26, so Compass's minimum SDK is 26 (Android 8.0) rather than the template's 24.
- alphaTab's model classes run on the plain JVM, so the Song → Score translation is tested with fast JVM unit tests instead of on-device tests.
- Guitar Pro and MusicXML import later become a translation in the other direction (alphaTab Score → Compass Song) rather than hand-written parsers.
