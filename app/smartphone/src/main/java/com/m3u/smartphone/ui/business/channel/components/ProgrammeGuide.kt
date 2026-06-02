package com.m3u.smartphone.ui.business.channel.components

import android.annotation.SuppressLint
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventTimeoutCancellationException
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.zIndex
import androidx.constraintlayout.compose.ConstraintLayout
import androidx.paging.compose.LazyPagingItems
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.preferenceOf
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.ProgrammeRange
import com.m3u.data.database.model.ProgrammeRange.Companion.HOUR_LENGTH
import com.m3u.smartphone.TimeUtils.formatEOrSh
import com.m3u.smartphone.TimeUtils.toEOrSh
import com.m3u.smartphone.ui.material.components.FontFamilies
import com.m3u.smartphone.ui.material.model.LocalSpacing
import eu.wewox.minabox.MinaBox
import eu.wewox.minabox.MinaBoxItem
import eu.wewox.minabox.MinaBoxScrollDirection
import eu.wewox.minabox.MinaBoxState
import eu.wewox.minabox.rememberSaveableMinaBoxState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.absoluteValue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private enum class Zoom(val time: Float) {
    // Default zoom bumped from 1f to 1.5f so each programme cell has enough
    // vertical room for "hour + 2-line title" without overlapping the next one.
    DEFAULT(1.5f), ZOOM_1_5(2f), ZOOM_2(3f), ZOOM_5(5f)
}

@Composable
internal fun ProgramGuide(
    isPanelExpanded: Boolean,
    programmes: LazyPagingItems<Programme>,
    range: ProgrammeRange,
    programmeReminderIds: List<Int>,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") minaBoxState: MinaBoxState = rememberSaveableMinaBoxState(),
    @Suppress("UNUSED_PARAMETER") height: Float = 256f,
    @Suppress("UNUSED_PARAMETER") padding: Float = 16f,
    @Suppress("UNUSED_PARAMETER") currentTimelineHeight: Float = 48f,
    @Suppress("UNUSED_PARAMETER") scrollOffset: Int = -120,
    onProgrammePressed: (Programme) -> Unit
) {
    val currentMilliseconds by produceCurrentMillisecondState()
    val coroutineScope = rememberCoroutineScope()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val clockMode by preferenceOf(PreferencesKeys.CLOCK_MODE)

    // Find the index of the programme currently airing so we can auto-scroll to it.
    val currentIndex by remember(programmes.itemCount, currentMilliseconds) {
        derivedStateOf {
            (0 until programmes.itemCount)
                .firstOrNull { i ->
                    val p = programmes.peek(i) ?: return@firstOrNull false
                    currentMilliseconds in p.start..p.end
                } ?: -1
        }
    }

    LaunchedEffect(isPanelExpanded, currentIndex) {
        if (isPanelExpanded && currentIndex >= 0) {
            kotlinx.coroutines.delay(120)
            // Scroll to the item just before the current one so the user sees
            // the previous programme on top and the live programme in view.
            val targetIndex = (currentIndex - 1).coerceAtLeast(0)
            runCatching { listState.animateScrollToItem(targetIndex) }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).then(modifier)) {
        androidx.compose.foundation.lazy.LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(count = programmes.itemCount) { idx ->
                val programme = programmes[idx] ?: return@items
                val isCurrent = currentMilliseconds in programme.start..programme.end
                ProgrammeListRow(
                    programme = programme,
                    isCurrent = isCurrent,
                    isInReminder = programme.id in programmeReminderIds,
                    clockMode = clockMode,
                    onClick = { onProgrammePressed(programme) }
                )
            }
        }

        SmallFloatingActionButton(
            onClick = {
                coroutineScope.launch {
                    if (currentIndex >= 0) {
                        runCatching { listState.animateScrollToItem(currentIndex) }
                    } else {
                        listState.animateScrollToItem(0)
                    }
                }
            },
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp
            ),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.KeyboardDoubleArrowUp,
                contentDescription = "Now"
            )
        }
    }
}

@Composable
private fun ProgrammeListRow(
    programme: Programme,
    isCurrent: Boolean,
    isInReminder: Boolean,
    clockMode: Boolean,
    onClick: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val container = when {
        isCurrent -> MaterialTheme.colorScheme.primaryContainer
        isInReminder -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val onContainer = when {
        isCurrent -> MaterialTheme.colorScheme.onPrimaryContainer
        isInReminder -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    // Pulsing live dot.
    val infiniteTransition = rememberInfiniteTransition(label = "live-dot")
    val livePulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(900),
            repeatMode = RepeatMode.Reverse
        ),
        label = "live-dot-alpha"
    )

    Surface(
        color = container,
        contentColor = onContainer,
        shape = AbsoluteRoundedCornerShape(12.dp),
        border = if (isCurrent) androidx.compose.foundation.BorderStroke(2.dp, primary) else null,
        shadowElevation = if (isCurrent) 4.dp else 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = { onClick() })
            }
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            // Side accent bar (visible only when current)
            if (isCurrent) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(4.dp)
                        .background(primary)
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                val start = Instant.fromEpochMilliseconds(programme.start)
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .toEOrSh()
                val end = Instant.fromEpochMilliseconds(programme.end)
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .toEOrSh()
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${start.formatEOrSh(clockMode)} – ${end.formatEOrSh(clockMode)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalContentColor.current.copy(if (isCurrent) 0.95f else 0.7f)
                    )
                    if (isCurrent) {
                        Spacer(Modifier.padding(start = 10.dp))
                        // Pulsing red dot
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    androidx.compose.ui.graphics.Color(0xFFFF4444)
                                        .copy(alpha = livePulse)
                                )
                        )
                        Spacer(Modifier.padding(start = 5.dp))
                        Text(
                            text = "EN DIRECTO",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            ),
                            color = primary
                        )
                    }
                }
                Text(
                    text = programme.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = if (isCurrent) androidx.compose.ui.text.font.FontWeight.Bold
                        else androidx.compose.ui.text.font.FontWeight.SemiBold
                    ),
                    modifier = Modifier.padding(top = 3.dp)
                )
                if (programme.description.isNotBlank()) {
                    Text(
                        text = programme.description,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalContentColor.current.copy(0.75f),
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
                if (isCurrent) {
                    val now = remember(programme.start) { kotlin.time.Clock.System.now().toEpochMilliseconds() }
                    val totalMs = (programme.end - programme.start).coerceAtLeast(1L)
                    val elapsedMs = (now - programme.start).coerceIn(0L, totalMs)
                    val progress = elapsedMs.toFloat() / totalMs.toFloat()
                    Spacer(Modifier.padding(top = 8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        color = primary,
                        trackColor = primary.copy(alpha = 0.2f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }
        }
    }
}


@Composable
private fun ProgrammeCell(
    programme: Programme,
    inReminder: Boolean,
    modifier: Modifier = Modifier,
    onPressed: () -> Unit
) {
    val currentOnPressed by rememberUpdatedState(onPressed)
    val spacing = LocalSpacing.current
    val clockMode by preferenceOf(PreferencesKeys.CLOCK_MODE)

    val content = @Composable {
        // Use ClippingColumn so contents never overflow the cell box. The cell height
        // is computed by the timeline based on programme duration; short programmes
        // can't show much, so we only render time + title (truncated).
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            val start = Instant.fromEpochMilliseconds(programme.start)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .toEOrSh()
            val end = Instant.fromEpochMilliseconds(programme.end)
                .toLocalDateTime(TimeZone.currentSystemDefault())
                .toEOrSh()
            Text(
                text = "${start.formatEOrSh(clockMode)} - ${end.formatEOrSh(clockMode)}",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelSmall,
                color = LocalContentColor.current.copy(0.7f)
            )
            Text(
                text = programme.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold
                ),
                softWrap = true
            )
        }
    }
    val hapticFeedback = LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        label = "programme-cell-scale",
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioHighBouncy,
            stiffness = Spring.StiffnessMediumLow
        )
    )
    val currentColor by animateColorAsState(
        targetValue = if (inReminder) MaterialTheme.colorScheme.tertiary
        else MaterialTheme.colorScheme.tertiaryContainer,
        label = "programme-cell-color"
    )
    val currentContentColor by animateColorAsState(
        targetValue = if (inReminder) MaterialTheme.colorScheme.onTertiary
        else MaterialTheme.colorScheme.onTertiaryContainer,
        label = "programme-cell-color"
    )
    Surface(
        color = currentColor,
        contentColor = currentContentColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = AbsoluteRoundedCornerShape(4.dp),
        modifier = Modifier
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    try {
                        withTimeout(viewConfiguration.longPressTimeoutMillis) {
                            waitForUpOrCancellation()
                        }
                    } catch (_: PointerEventTimeoutCancellationException) {
                        down.consume()
                        currentOnPressed()
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        isPressed = true
                        do {
                            val event = awaitPointerEvent()
                            event.changes.fastForEach { it.consume() }
                        } while (event.changes.fastAny { it.pressed })
                        isPressed = false
                    } finally {
                        isPressed = false
                    }
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(modifier),
        content = content
    )
}

@Composable
private fun CurrentTimelineCell(
    milliseconds: Long,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current

    val twelveHourClock by preferenceOf(PreferencesKeys.CLOCK_MODE)

    val color = MaterialTheme.colorScheme.error
    val contentColor = MaterialTheme.colorScheme.onError
    val currentMilliseconds by rememberUpdatedState(milliseconds)
    val time = remember(currentMilliseconds) {
        Instant
            .fromEpochMilliseconds(currentMilliseconds)
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .formatEOrSh(twelveHourClock, ignoreSeconds = false)
    }
    Box(contentAlignment = Alignment.CenterEnd) {
        Canvas(
            modifier
                .requiredHeight(24.dp)
                .fillMaxWidth()
                .zIndex(2f)
        ) {
            drawLine(
                color = color,
                start = Offset(
                    x = 0f,
                    y = size.minDimension / 2
                ),
                end = Offset(
                    x = size.maxDimension,
                    y = size.minDimension / 2
                ),
                strokeWidth = Stroke.DefaultMiter
            )
        }
        Text(
            text = time,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium.copy(
                fontFamily = FontFamilies.LexendExa,
            ),
            modifier = Modifier
                .padding(horizontal = spacing.medium)
                .clip(AbsoluteRoundedCornerShape(spacing.small))
                .zIndex(4f)
                .background(color)
                .padding(horizontal = spacing.extraSmall)
        )
    }
}


@Composable
private fun Controls(
    animateToCurrentTimeline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ConstraintLayout(
        modifier = modifier
    ) {
        val (scroll) = createRefs()
        SmallFloatingActionButton(
            elevation = FloatingActionButtonDefaults.elevation(0.dp),
            onClick = animateToCurrentTimeline,
            modifier = Modifier.constrainAs(scroll) {
                this.end.linkTo(parent.end)
                this.bottom.linkTo(parent.bottom)
            }
        ) {
            Icon(
                imageVector = Icons.Rounded.KeyboardDoubleArrowUp,
                contentDescription = "scroll to current timeline"
            )
        }
    }
}

@Composable
private fun produceCurrentMillisecondState(
    duration: Duration = 1.seconds
): State<Long> = produceState(
    initialValue = Clock.System.now().toEpochMilliseconds()
) {
    launch {
        while (true) {
            delay(duration)
            value = Clock.System.now().toEpochMilliseconds()
        }
    }
}
