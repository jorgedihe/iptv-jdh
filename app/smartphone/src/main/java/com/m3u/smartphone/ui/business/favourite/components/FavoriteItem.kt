package com.m3u.smartphone.ui.business.favourite.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.m3u.data.database.model.Channel
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.model.LocalSpacing
import com.m3u.core.foundation.components.AbsoluteSmoothCornerShape
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Composable
internal fun FavoriteItem(
    channel: Channel,
    recently: Boolean,
    zapping: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current

    val recentlyString = stringResource(string.ui_sort_recently)
    val neverPlayedString = stringResource(string.ui_sort_never_played)

    OutlinedCard(
        modifier = Modifier.semantics(mergeDescendants = true) { },
        border = CardDefaults.outlinedCardBorder(zapping),
        colors = CardDefaults.cardColors(Color.Transparent),
        shape = AbsoluteSmoothCornerShape(spacing.medium, 65),
    ) {
        val context = LocalContext.current
        ListItem(
            leadingContent = {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(AbsoluteSmoothCornerShape(spacing.small, 65))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!channel.cover.isNullOrBlank()) {
                        AsyncImage(
                            model = remember(channel.cover) {
                                ImageRequest.Builder(context)
                                    .data(channel.cover)
                                    .crossfade(160)
                                    .build()
                            },
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Tv,
                            contentDescription = null,
                            tint = LocalContentColor.current.copy(alpha = 0.5f)
                        )
                    }
                }
            },
            headlineContent = {
                Text(
                    text = channel.title.trim(),
                    style = MaterialTheme.typography.titleSmall,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    fontWeight = FontWeight.Bold,
                )
            },
            supportingContent = {
                if (recently) {
                    Text(
                        text = remember(channel.seen) {
                            val now = Clock.System.now()
                            val instant = Instant.fromEpochMilliseconds(channel.seen)
                            val duration = now - instant
                            duration.toComponents { days, hours, minutes, seconds, _ ->
                                when {
                                    channel.seen == 0L -> neverPlayedString
                                    days > 0 -> days.days.toString()
                                    hours > 0 -> hours.hours.toString()
                                    minutes > 0 -> minutes.minutes.toString()
                                    seconds > 0 -> seconds.seconds.toString()
                                    else -> recentlyString
                                }
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(0.56f)
                    )
                }
            },
            colors = ListItemDefaults.colors(Color.Transparent),
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .then(modifier)
        )
    }
}
