package com.m3u.smartphone.ui.material.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.m3u.business.channel.TmdbConfig
import com.m3u.business.channel.TmdbCredits
import com.m3u.core.foundation.components.AbsoluteSmoothCornerShape
import com.m3u.core.foundation.components.CircularProgressIndicator
import com.m3u.core.foundation.wrapper.Resource
import com.m3u.data.database.model.Channel
import com.m3u.data.parser.xtream.XtreamEpisodeInfo
import com.m3u.smartphone.ui.material.model.LocalSpacing

@Composable
fun EpisodesBottomSheet(
    series: Channel?,
    episodes: Resource<List<XtreamEpisodeInfo>>,
    onRefresh: () -> Unit,
    onEpisodeClick: (XtreamEpisodeInfo) -> Unit,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val sheetState = rememberModalBottomSheetState()
    val currentOnRefresh by rememberUpdatedState(onRefresh)

    val visible = series != null
    val loading = episodes == Resource.Loading

    LaunchedEffect(series) { currentOnRefresh() }

    LaunchedEffect(episodes is Resource.Success) {
        if (loading) sheetState.partialExpand()
        else sheetState.expand()
    }

    // Per-episode TMDB enrichment (still + overview). Cached by season so
    // we don't re-fetch when the user scrolls or refreshes. We need the
    // series' tmdb_id which Xtream stores in get_series_info; we recover it
    // lazily by parsing the show title (Xtream sets the "tmdb" id alongside
    // the series, but it's not on the per-episode payload, so we derive it
    // from the series Channel via a separate flow). For the bottom sheet
    // case we accept it via the series.relationId field if present, else
    // try to parse the channel cover URL — TMDB cover_big always carries a
    // /t/p/.../{poster_path}.jpg, but we want the show id, not poster id,
    // so this approach is best-effort.
    val tmdbId = remember(series?.id) { extractTmdbId(series) }
    val episodeMetaMap = remember(series?.id, episodes) { mutableStateOf<Map<Pair<Int, Int>, TmdbCredits.EpisodeMeta>>(emptyMap()) }
    LaunchedEffect(series?.id, episodes) {
        val list = (episodes as? Resource.Success)?.data ?: return@LaunchedEffect
        if (tmdbId.isNullOrBlank() || !TmdbConfig.isEnabled) return@LaunchedEffect
        val sxxExx = Regex("""S(\d+)E(\d+)""", RegexOption.IGNORE_CASE)
        val seasons = list.mapNotNull { ep ->
            sxxExx.find(ep.title.orEmpty())?.groupValues?.get(1)?.toIntOrNull() ?: 1
        }.toSet()
        val collected = mutableMapOf<Pair<Int, Int>, TmdbCredits.EpisodeMeta>()
        seasons.forEach { season ->
            val map = TmdbConfig.episodesForSeason(tmdbId, season)
            map.forEach { (epNum, meta) ->
                collected[season to epNum] = meta
            }
        }
        episodeMetaMap.value = collected
    }

    BottomSheet(
        sheetState = sheetState,
        visible = visible,
        blurBody = true,
        header = {
            series?.let {
                Text(
                    text = it.title,
                    style = MaterialTheme.typography.titleLarge
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.minimumInteractiveComponentSize()
            ) {
                when (episodes) {
                    Resource.Loading -> CircularProgressIndicator()
                    else -> IconButton(onClick = onRefresh) {
                        Icon(imageVector = Icons.Rounded.Refresh, contentDescription = null)
                    }
                }
            }
        },
        body = {
            HorizontalDivider()
            when (episodes) {
                is Resource.Failure -> {
                    Text(
                        text = episodes.message.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
                else -> {}
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(spacing.small)
            ) {
                if (loading) {
                    stickyHeader {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                item { Spacer(modifier = Modifier.height(spacing.small)) }
                when (episodes) {
                    is Resource.Success -> {
                        val sxxExx = Regex("""S(\d+)E(\d+)""", RegexOption.IGNORE_CASE)
                        items(episodes.data) { episode ->
                            val match = sxxExx.find(episode.title.orEmpty())
                            val seasonNum = match?.groupValues?.get(1)?.toIntOrNull() ?: 1
                            val epNum = episode.episodeNum?.toIntOrNull()
                                ?: match?.groupValues?.get(2)?.toIntOrNull() ?: 0
                            val meta = episodeMetaMap.value[seasonNum to epNum]
                            val seriesCover = series?.cover
                            XtreamEpisodeItem(
                                episode = episode,
                                meta = meta,
                                fallbackImage = seriesCover,
                                episodeNumber = epNum,
                                onClick = { onEpisodeClick(episode) },
                                modifier = Modifier.padding(horizontal = spacing.medium)
                            )
                        }
                    }
                    else -> {}
                }
            }
        },
        onDismissRequest = onDismissRequest,
        modifier = modifier
    )
}

/** Best-effort: parse "S01E01" from the channel title to recover a season
 *  number, but the tmdb_id itself lives in the series row's epg_channel_id /
 *  relation_id field (Xtream stores it there for series). Returns null when
 *  we can't recover one — the sheet then falls back to the show cover. */
private fun extractTmdbId(series: Channel?): String? {
    val series = series ?: return null
    // Xtream stores the series tmdb id in the relation_id column when the
    // category index includes it; otherwise null.
    val rel = series.relationId.orEmpty()
    return rel.takeIf { it.all { c -> c.isDigit() } && it.isNotBlank() }
}

@Composable
private fun XtreamEpisodeItem(
    episode: XtreamEpisodeInfo,
    meta: TmdbCredits.EpisodeMeta?,
    fallbackImage: String?,
    episodeNumber: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val spacing = LocalSpacing.current
    val thumbnail = meta?.stillUrl?.takeIf { it.isNotBlank() } ?: fallbackImage
    val displayTitle = meta?.tmdbName?.takeIf { it.isNotBlank() }
        ?: episode.title.orEmpty()

    OutlinedCard(
        shape = AbsoluteSmoothCornerShape(spacing.medium, 65),
        modifier = modifier
    ) {
        ListItem(
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            leadingContent = {
                Box(
                    modifier = Modifier
                        .width(110.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    if (!thumbnail.isNullOrBlank()) {
                        AsyncImage(
                            model = remember(thumbnail) {
                                ImageRequest.Builder(context).data(thumbnail)
                                    .crossfade(160).build()
                            },
                            contentDescription = displayTitle,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.Movie,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    // EP number badge
                    Text(
                        text = "EP%02d".format(episodeNumber),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            },
            headlineContent = {
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = meta?.overview?.takeIf { it.isNotBlank() }?.let { ov ->
                {
                    Text(
                        text = ov,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            },
            modifier = Modifier.clickable { onClick() }
        )
    }
}
