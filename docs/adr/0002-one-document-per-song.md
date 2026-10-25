# Each Song is stored as one versioned document

Songs are saved as one JSON document per Song in app storage, carrying a format version number, with a small JSON index file used only to list the Library (title, artist, last practiced). A Song is always opened, edited and backed up as a whole, so splitting it into relational tables (Song/Track/Measure/Note) would add mapping cost with no query benefit; per-Song documents make backup a zip of files and let the format evolve by version-numbered migrations. Practice settings are stored separately from the Song document so Practice mode never writes to it.

## Consequences

- The Library index is a plain JSON file rather than a database: the Library is small, search and sort happen in memory, and the index can be rebuilt from the Song documents if it is ever lost.
- Each Song gets its own folder holding its document and, later, its Practice settings, so deleting a Song removes both.
