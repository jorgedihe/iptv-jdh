package com.m3u.smartphone.ui.business.channel.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.m3u.business.channel.EpisodeRow
import com.m3u.business.channel.Person
import com.m3u.business.channel.VodInfo

/**
 * Detail panel rendered in the pull-up area when the playing item is a movie
 * (VOD) or a series. The layout mimics DiiXtream's "movie detail" screen:
 *   - hero backdrop (or poster as fallback)
 *   - title + TMDB-style rating badge
 *   - meta line: year · duration · genre
 *   - one-tap Favourite button
 *   - synopsis
 *   - cast row (circular avatars with name initials)
 */
@Composable
internal fun VodInfoPanel(
    info: VodInfo,
    favourite: Boolean,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onToggleFavourite: () -> Unit,
    episodes: List<EpisodeRow> = emptyList(),
    onPlayEpisode: (EpisodeRow) -> Unit = {},
    // Resume state (VOD / series). When non-null the primary button switches
    // to "REANUDAR (mm:ss)" and a secondary "Empezar desde el inicio" appears.
    resumePosition: Long = -1L,
    resumeDuration: Long = -1L,
    onPlayFromStart: (() -> Unit)? = null,
    // True when this is a series — comes from playlist.isSeries, NOT from the
    // episodes-list size. We need it before episodes load so the play button
    // never flashes for a fraction of a second on series.
    isSeries: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val heroUrl = info.backdrop?.takeIf { it.isNotBlank() } ?: info.cover

    // Prefer TMDB-enriched lists (have photos); fall back to comma-split
    // strings from the Xtream payload so we always show *something*.
    val crewPeople: List<Person> = remember(info.crewPeople, info.director) {
        info.crewPeople.ifEmpty {
            info.director
                ?.split(',', ';', '|')
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.distinct()
                ?.map { Person(name = it, role = "Dirección") }
                ?: emptyList()
        }
    }
    val castPeople: List<Person> = remember(info.castPeople, info.cast) {
        info.castPeople.ifEmpty {
            info.cast
                ?.split(',', ';', '|')
                ?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?.distinct()
                ?.map { Person(name = it) }
                ?: emptyList()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ─── Hero ──────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            if (!heroUrl.isNullOrBlank()) {
                AsyncImage(
                    model = remember(heroUrl) {
                        ImageRequest.Builder(context).data(heroUrl).crossfade(220).build()
                    },
                    contentDescription = info.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Movie,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(64.dp)
                    )
                }
            }
            // Gradient fade so the title sits on a darker base regardless of
            // what the backdrop image happens to look like.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.85f)
                        )
                    )
            )
        }

        // ─── Title + rating + meta ─────────────────────────────────────────
        Column(
            modifier = Modifier.padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = info.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface
            )
            info.rating?.takeIf { it.isNotBlank() }?.let { rating ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = formatRating(rating),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            val metaLine = buildString {
                val year = info.releaseDate?.take(4)?.takeIf { y -> y.all { it.isDigit() } }
                if (!year.isNullOrBlank()) append(year)
                if (!info.duration.isNullOrBlank()) {
                    if (isNotEmpty()) append("  ·  ")
                    append(info.duration)
                }
                if (!info.genre.isNullOrBlank()) {
                    if (isNotEmpty()) append("  ·  ")
                    append(info.genre)
                }
            }
            if (metaLine.isNotBlank()) {
                Text(
                    text = metaLine,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        // ─── Primary action: Play / Resume / Pause ─────────────────────────
        // For series we DO NOT show the big "REPRODUCIR" button because the
        // base channel.url is the series info endpoint (not a playable stream).
        // The user must tap an individual episode card below; that path uses
        // MediaCommand.XtreamEpisode which builds the correct stream URL.
        val hasResume = !isPlaying && resumePosition > 0L
        val progressFraction: Float? = if (hasResume && resumeDuration > 0L) {
            (resumePosition.toFloat() / resumeDuration.toFloat()).coerceIn(0f, 1f)
        } else null
        val primaryLabel: String = when {
            isPlaying -> "PAUSAR"
            hasResume -> "REANUDAR ${formatMillis(resumePosition)}"
            else -> "REPRODUCIR"
        }
        Column(
            modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isSeries) {
                Button(
                    onClick = onPlayPause,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = primaryLabel,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            } else {
                // Brief hint so the user knows to pick an episode below.
                Text(
                    text = "Toca un episodio para reproducirlo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            // Thin progress bar that mirrors how far you got through the file.
            // Only for VOD — for series each episode tracks its own resume.
            if (!isSeries && progressFraction != null) {
                LinearProgressIndicator(
                    progress = { progressFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            }
            // Secondary "Start from beginning" link, only when there's
            // something to reset (and we're not a series).
            if (!isSeries && hasResume && onPlayFromStart != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TextButton(onClick = onPlayFromStart) {
                        Text(
                            text = "Empezar desde el inicio",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // ─── Secondary action: Favourite ───────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            CircularActionButton(
                icon = if (favourite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                tint = if (favourite) Color(0xFFE91E63) else MaterialTheme.colorScheme.onSurface,
                label = if (favourite) "En Favoritos" else "Añadir a Favoritos",
                onClick = onToggleFavourite
            )
        }

        // ─── Plot ──────────────────────────────────────────────────────────
        info.plot?.takeIf { it.isNotBlank() }?.let { plot ->
            Text(
                text = plot,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        }

        // ─── Cast & crew ───────────────────────────────────────────────────
        if (castPeople.isNotEmpty() || crewPeople.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "REPARTO Y EQUIPO",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(crewPeople) { p -> PersonChip(person = p) }
                    items(castPeople) { p -> PersonChip(person = p) }
                }
            }
        }

        // ─── Seasons + episodes (series only) ──────────────────────────────
        if (episodes.isNotEmpty()) {
            val grouped = episodes.groupBy { it.seasonNumber }.toSortedMap()
            grouped.forEach { (seasonNumber, list) ->
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    androidx.compose.material3.Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.padding(horizontal = 20.dp)
                    ) {
                        Text(
                            text = "SEASON$seasonNumber  ·  ${list.size} Episodios",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                    LazyRow(
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(list) { ep ->
                            EpisodeCard(
                                episode = ep,
                                seriesCover = info.cover,
                                seriesBackdrop = info.backdrop,
                                onClick = { onPlayEpisode(ep) }
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun EpisodeCard(
    episode: EpisodeRow,
    seriesCover: String?,
    seriesBackdrop: String?,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    // Prefer the episode-specific still when we have it (TMDB enrichment),
    // else fall back to the series backdrop / cover so the card is never
    // empty. The aspect ratio matches DiiXtream's episode tiles.
    val img = episode.stillUrl?.takeIf { it.isNotBlank() }
        ?: seriesBackdrop?.takeIf { it.isNotBlank() }
        ?: seriesCover
    Column(
        modifier = Modifier
            .width(240.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            if (!img.isNullOrBlank()) {
                AsyncImage(
                    model = remember(img) {
                        ImageRequest.Builder(context).data(img).crossfade(180).build()
                    },
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Movie,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            // Centered play overlay so the user clearly understands the card
            // is the start of playback (mirrors Netflix / Prime episode tiles).
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
            // Top-right EP badge
            Text(
                text = "EP%02d".format(episode.episodeNumber),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
        Text(
            text = episode.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        episode.duration?.takeIf { it.isNotBlank() }?.let { dur ->
            Text(
                text = dur,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        episode.overview?.takeIf { it.isNotBlank() }?.let { ov ->
            Text(
                text = ov,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Format a numeric rating that may arrive as "8.243" or "8.2" or "8" and round
 * it to a single decimal place. Non-numeric values pass through untouched.
 */
private fun formatRating(raw: String): String {
    val n = raw.toDoubleOrNull() ?: return raw
    // Some servers return 0-5 scale ("rating_5based"), others 0-10. Heuristic:
    // anything <= 5 we leave as is; > 5 we treat as 0-10.
    return if (n > 5) "%.1f / 10".format(n) else "%.1f / 5".format(n)
}

@Composable
private fun PersonChip(person: Person) {
    val context = LocalContext.current
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.width(78.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            if (!person.photoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = remember(person.photoUrl) {
                        ImageRequest.Builder(context)
                            .data(person.photoUrl)
                            .crossfade(180)
                            .build()
                    },
                    contentDescription = person.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                val initials = person.name.split(' ').filter { it.isNotBlank() }
                    .take(2).joinToString("") { it.first().uppercase() }
                    .ifBlank { "?" }
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Text(
            text = person.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        person.role?.takeIf { it.isNotBlank() }?.let { role ->
            Text(
                text = role.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun CircularActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .width(96.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** Format a millisecond duration as "mm:ss" or "hh:mm:ss" if ≥ 1h. */
private fun formatMillis(millis: Long): String {
    if (millis <= 0L) return "0:00"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
