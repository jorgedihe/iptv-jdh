package com.m3u.business.foryou

import androidx.compose.runtime.Immutable
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Programme

/**
 * A grouping of Xtream playlists that share the same server + credentials.
 * When you subscribe to an Xtream service, M3UAndroid creates three [Playlist]
 * rows (Live, VOD, Series). All three share `basicUrl + username`, so we
 * collapse them into a single Provider for UX purposes.
 */
data class Provider(
    val key: String,
    val displayName: String,
    val live: Playlist? = null,
    val vod: Playlist? = null,
    val series: Playlist? = null
)

@Immutable
data class ChannelWithProgrammeLite(
    val channel: Channel,
    val programme: Programme?
)

/** A category as shown in the home: title + how many channels and a peek of the first ones. */
@Immutable
data class CategorySection(
    val name: String,
    val totalCount: Int,
    val previewChannels: List<Channel>,
    val playlistUrl: String
)
