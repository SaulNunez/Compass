package com.saulnunez.compass.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saulnunez.compass.R
import com.saulnunez.compass.domain.PracticeSettings
import com.saulnunez.compass.domain.Song

/** Mute and solo for each Track of [song], saved in the Practice settings. */
@Composable
internal fun TrackMix(
    song: Song,
    settings: PracticeSettings,
    onSettingsChange: (PracticeSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(stringResource(R.string.track_mix_heading), style = MaterialTheme.typography.labelLarge)
        song.tracks.forEachIndexed { index, track ->
            val isMuted = index in settings.mutedTracks
            val isSoloed = index in settings.soloedTracks
            val muteDescription = stringResource(R.string.track_mute_description, track.name)
            val soloDescription = stringResource(R.string.track_solo_description, track.name)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    track.name,
                    modifier = Modifier.weight(1f).padding(end = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                FilterChip(
                    selected = isMuted,
                    onClick = { onSettingsChange(settings.mute(index, !isMuted)) },
                    label = { Text(stringResource(R.string.track_mute)) },
                    modifier = Modifier.semantics { contentDescription = muteDescription },
                )
                FilterChip(
                    selected = isSoloed,
                    onClick = { onSettingsChange(settings.solo(index, !isSoloed)) },
                    label = { Text(stringResource(R.string.track_solo)) },
                    modifier = Modifier.semantics { contentDescription = soloDescription },
                )
            }
        }
    }
}
