package com.m3u.smartphone.ui.business.playlist.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.m3u.business.channel.EpisodeRow
import com.m3u.business.channel.VodDetailLoader
import com.m3u.business.channel.VodInfo
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist
import com.m3u.smartphone.ui.business.channel.components.VodInfoPanel

/**
 * Full-screen pre-play detail for VOD / series. Loaded BEFORE the player —
 * the user sees the poster, plot, cast, episodes etc. and only enters the
 * actual player when they tap REPRODUCIR.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VodDetailSheet(
    channel: Channel?,
    playlist: Playlist?,
    onPlay: (Channel) -> Unit,
    onPlayEpisode: (Channel, EpisodeRow) -> Unit,
    onFavourite: (Int) -> Unit,
    onDismissRequest: () -> Unit
) {
    if (channel == null) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Load TMDB-enriched info + episodes as soon as the sheet opens.
    var vodInfo by remember(channel.id) { mutableStateOf<VodInfo?>(null) }
    var episodes by remember(channel.id) { mutableStateOf<List<EpisodeRow>>(emptyList()) }
    LaunchedEffect(channel.id, playlist?.url) {
        val p = playlist ?: return@LaunchedEffect
        vodInfo = VodDetailLoader.loadVodInfo(p, channel)
        episodes = VodDetailLoader.loadEpisodes(p, channel)
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.background,
        // Take the full screen so users see this as a proper detail page
        // rather than a half-height sheet.
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            VodInfoPanel(
                info = vodInfo ?: VodInfo(
                    title = channel.title.orEmpty(),
                    cover = channel.cover
                ),
                favourite = channel.favourite,
                isPlaying = false,
                onPlayPause = { onPlay(channel) },
                onToggleFavourite = { onFavourite(channel.id) },
                episodes = episodes,
                onPlayEpisode = { ep -> onPlayEpisode(channel, ep) }
            )
            // Close button (X) top-left, mirrors DiiXtream.
            IconButton(
                onClick = onDismissRequest,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Cerrar",
                    tint = Color.White
                )
            }
        }
    }
}
