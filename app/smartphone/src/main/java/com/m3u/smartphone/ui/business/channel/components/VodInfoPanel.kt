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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

        // ─── Primary action: Play / Pause ──────────────────────────────────
        Row(
            modifier = Modifier.padding(horizontal = 20.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
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
                    text = if (isPlaying) "PAUSAR" else "REPRODUCIR",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
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

        Spacer(Modifier.height(8.dp))
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
