package com.m3u.smartphone.ui.business.channel

import android.Manifest
import android.content.Intent
import android.graphics.Rect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.media3.common.Player
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.takeOrElse
import androidx.compose.ui.util.fastRoundToInt
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import com.google.accompanist.permissions.rememberPermissionState
import com.m3u.business.channel.ChannelViewModel
import com.m3u.business.channel.PlayerState
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.preferenceOf
import com.m3u.core.foundation.util.basic.isNotEmpty
import com.m3u.core.foundation.util.basic.title
import com.m3u.data.database.model.AdjacentChannels
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.isVod
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.business.channel.components.DlnaDevicesBottomSheet
import com.m3u.smartphone.ui.business.channel.components.FormatsBottomSheet
import com.m3u.smartphone.ui.business.channel.components.Paddings
import com.m3u.smartphone.ui.business.channel.components.MaskGestureValuePanel
import com.m3u.smartphone.ui.business.channel.components.PlayerPanel
import com.m3u.smartphone.ui.business.channel.components.VerticalGestureArea
import com.m3u.smartphone.ui.common.helper.LocalHelper
import com.m3u.smartphone.ui.common.helper.OnPipModeChanged
import com.m3u.smartphone.ui.material.components.Player
import com.m3u.smartphone.ui.material.components.PullPanelLayout
import com.m3u.smartphone.ui.material.components.PullPanelLayoutDefaults
import com.m3u.smartphone.ui.material.components.isExpanded
import com.m3u.smartphone.ui.material.components.mask.MaskInterceptor
import com.m3u.smartphone.ui.material.components.mask.MaskState
import com.m3u.smartphone.ui.material.components.mask.rememberMaskState
import com.m3u.smartphone.ui.material.components.mask.toggle
import com.m3u.smartphone.ui.material.components.rememberPlayerState
import com.m3u.smartphone.ui.material.components.rememberPullPanelLayoutState
import com.m3u.smartphone.ui.material.ktx.checkPermissionOrRationale
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.time.Duration.Companion.seconds

@Composable
fun ChannelRoute(
    modifier: Modifier = Modifier,
    viewModel: ChannelViewModel = hiltViewModel(),
) {
    val openInExternalPlayerString = stringResource(string.feat_channel_open_in_external_app)

    val helper = LocalHelper.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current

    val isPanelEnabled by preferenceOf(PreferencesKeys.PLAYER_PANEL)
    val zappingMode by preferenceOf(PreferencesKeys.ZAPPING_MODE)

    val requestIgnoreBatteryOptimizations =
        rememberPermissionState(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)

    val playerState: PlayerState by viewModel.playerState.collectAsStateWithLifecycle()
    val channel by viewModel.channel.collectAsStateWithLifecycle()
    val adjacentChannels by viewModel.adjacentChannels.collectAsStateWithLifecycle()
    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val isDevicesVisible by viewModel.isDevicesVisible.collectAsStateWithLifecycle()
    val searching by viewModel.searching.collectAsStateWithLifecycle()
    val connectedDeviceUdn by viewModel.connectedDeviceUdn.collectAsStateWithLifecycle()

    val tracks by viewModel.tracks.collectAsStateWithLifecycle(emptyMap())
    val selectedFormats by viewModel.currentTracks.collectAsStateWithLifecycle(emptyMap())

    val volume by viewModel.volume.collectAsStateWithLifecycle()
    val isSeriesPlaylist by viewModel.isSeriesPlaylist.collectAsStateWithLifecycle(false)
    val vodInfo by viewModel.vodInfo.collectAsStateWithLifecycle()
    val seriesEpisodes by viewModel.seriesEpisodes.collectAsStateWithLifecycle()
    val isProgrammeSupported by viewModel.isProgrammeSupported.collectAsStateWithLifecycle(
        initialValue = false
    )

    val channels = viewModel.pagingChannels.collectAsLazyPagingItems()
    val programmes = viewModel.programmes.collectAsLazyPagingItems()
    val programmeRange by viewModel.programmeRange.collectAsStateWithLifecycle()

    val programmeReminderIds by viewModel.programmeReminderIds.collectAsStateWithLifecycle()

    var brightness by remember { mutableFloatStateOf(helper.brightness) }
    val isSupportBrightnessGesture by remember { derivedStateOf { brightness != -1f } }
    var speed by remember { mutableFloatStateOf(1f) }
    var isPipMode by remember { mutableStateOf(false) }
    var isAutoZappingMode by remember { mutableStateOf(true) }
    var choosing by remember { mutableStateOf(false) }

    val brightnessGesture by preferenceOf(PreferencesKeys.BRIGHTNESS_GESTURE)
    val volumeGesture by preferenceOf(PreferencesKeys.VOLUME_GESTURE)

    val brightnessGestureEnabled by remember { derivedStateOf { isSupportBrightnessGesture && brightnessGesture } }
    val volumeGestureEnabled by remember { derivedStateOf { volumeGesture } }

    val useVertical = PullPanelLayoutDefaults.UseVertical

    val maskState = rememberMaskState()
    // Start the info/EPG panel expanded in portrait (split layout: player + EPG below)
    // but collapsed in landscape so the video takes the full screen — the panel just
    // gets in the way when the phone is rotated to watch fullscreen.
    val pullPanelLayoutState = rememberPullPanelLayoutState(
        fraction = if (useVertical) 1f else 0f
    )
    // When the user rotates the phone, snap the panel to the right state for the new
    // orientation: fullscreen video in landscape, split layout back in portrait.
    LaunchedEffect(useVertical) {
        if (useVertical) pullPanelLayoutState.expand() else pullPanelLayoutState.collapse()
    }

    val isPanelExpanded = pullPanelLayoutState.isExpanded
    val fraction = pullPanelLayoutState.fraction

    // For movies and series the app no longer starts playback the instant the
    // user taps a tile in the list. Instead we open the player with the video
    // STOPPED (no buffering, no half-loaded frame flashing on top), expand the
    // info panel so the user sees the synopsis / cast / favourite button
    // first, and require them to tap the big "REPRODUCIR" button to actually
    // start the stream. Mirrors DiiXtream's UX.
    val isVodOrSeries = (playlist?.isVod == true) || isSeriesPlaylist
    // NOTE: the previous auto-stop + auto-expand of the info panel here was
    // designed for the old flow where the VodInfoPanel lived INSIDE the
    // PlayerActivity. Now the pre-play detail (VodDetailSheet) opens BEFORE
    // PlayerActivity is even started, so by the time we land here the user
    // already tapped REPRODUCIR / an episode and the player must keep
    // playing — calling player.stop() here was the root cause of
    // "series arrancan en pausa".

    val createRecordFileLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("video/mp4")) { uri ->
            uri ?: return@rememberLauncherForActivityResult
            viewModel.recordVideo(uri)
        }

    LifecycleResumeEffect(Unit) {
        with(helper) {
            isSystemBarUseDarkMode = true
            onPipModeChanged = OnPipModeChanged { info ->
                isPipMode = info.isInPictureInPictureMode
                if (!isPipMode) {
                    maskState.wake()
                    isAutoZappingMode = false
                }
            }
        }
        onPauseOrDispose {
            viewModel.closeDlnaDevices()
        }
    }

    LaunchedEffect(zappingMode, playerState.videoSize) {
        val videoSize = playerState.videoSize
        if (isAutoZappingMode && zappingMode && !isPipMode) {
            maskState.sleep()
            val rect = if (videoSize.isNotEmpty) videoSize
            else Rect(0, 0, 1920, 1080)
            helper.enterPipMode(rect)
        }
    }

    val isBarVisible by remember {
        derivedStateOf {
            if (pullPanelLayoutState.isExpanded) true
            else maskState.visible
        }
    }

    LaunchedEffect(Unit) {
        if (isSupportBrightnessGesture) {
            snapshotFlow { brightness }
                .drop(1)
                .onEach { helper.brightness = it }
                .launchIn(this)
        }

        snapshotFlow { isBarVisible }
            .onEach { isBarVisible ->
                helper.statusBarVisibility = isBarVisible
                helper.navigationBarVisibility = isBarVisible
            }
            .launchIn(this)

        snapshotFlow { maskState.visible }
            .onEach { viewModel.onMaskStateChanged(it) }
            .launchIn(this)

        snapshotFlow { pullPanelLayoutState.fraction }
            .drop(1)
            .onEach { maskState.sleep() }
            .launchIn(this)
    }

    if (brightnessGestureEnabled) {
        DisposableEffect(Unit) {
            val prev = helper.brightness
            onDispose {
                helper.brightness = prev
            }
        }
    }

    LaunchedEffect(isPipMode) {
        val interceptor: MaskInterceptor? = if (isPipMode) { _ -> false } else null
        maskState.intercept(interceptor)
    }

    var currentPaddings: Paddings by remember { mutableStateOf(Paddings()) }
    val onPaddingsChanged = { paddings: Paddings -> currentPaddings = paddings }
    val topPadding by animateDpAsState(
        currentPaddings.top.takeOrElse { 0.dp }.takeIf { isPanelExpanded } ?: 0.dp
    )
    val bottomPadding by animateDpAsState(
        currentPaddings.bottom.takeOrElse { 0.dp }.takeIf { isPanelExpanded } ?: 0.dp
    )

    val aspectRatio = with(density) {
        val source = playerState.videoSize
        val scaledSourceWidth = source.width()
        val scaledSourceHeight = source.height()
        val sourceAspectRatio = (scaledSourceWidth * 1f / scaledSourceHeight)
        if (sourceAspectRatio.isNaN()) {
            PullPanelLayoutDefaults.AspectRatio
        } else {
            val destWidth = windowInfo.containerSize.width.toDp()
            val destHeight = destWidth / sourceAspectRatio
            (destWidth * 1f / (destHeight + topPadding + bottomPadding))
        }
    }
    val onAlignment = { size: IntSize, space: IntSize ->
        val centerX = (space.width - size.width).toFloat() / 2f
        val centerY = (space.height - size.height).toFloat() / 2f
        val x = centerX
        val y = centerY - (centerY - with(density) { topPadding.toPx() }) * fraction
        IntOffset(x.fastRoundToInt(), y.fastRoundToInt())
    }

    // For VOD / series the user already saw the rich detail sheet BEFORE
    // pressing play; this screen should be a pure full-screen video — no
    // sliding panel, no info card peeking from the bottom. The "Conectando…"
    // overlay added inside ChannelPlayer still shows while buffering.
    if (isVodOrSeries) {
        Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            ChannelPlayer(
                isSeriesPlaylist = isSeriesPlaylist,
                openDlnaDevices = { viewModel.openDlnaDevices() },
                openChooseFormat = { choosing = true },
                openOrClosePanel = { /* no panel here */ },
                onFavorite = viewModel::onFavorite,
                maskState = maskState,
                playerState = playerState,
                playlist = playlist,
                channel = channel,
                adjacentChannels = adjacentChannels,
                hasTrack = tracks.isNotEmpty(),
                isPanelExpanded = false,
                brightness = brightness,
                onBrightness = { brightness = it },
                volume = volume,
                onVolume = viewModel::onVolume,
                brightnessGestureEnabled = brightnessGestureEnabled,
                volumeGestureEnabled = volumeGestureEnabled,
                speed = speed,
                onSpeedUpdated = {
                    viewModel.onSpeedUpdated(it)
                    speed = it
                },
                cwPosition = viewModel.cwPosition,
                onResetPlayback = viewModel::onResetPlayback,
                onPreviousChannelClick = viewModel::getPreviousChannel,
                onNextChannelClick = viewModel::getNextChannel,
                onEnterPipMode = {
                    helper.enterPipMode(playerState.videoSize)
                    maskState.unlockAll()
                },
                onPaddingsChanged = onPaddingsChanged,
                onAlignment = onAlignment,
            )
        }
        return
    }

    PullPanelLayout(
        state = pullPanelLayoutState,
        enabled = isPanelEnabled,
        aspectRatio = aspectRatio,
        useVertical = useVertical,
        panel = {
            // For VOD / series swap the EPG-and-channel-list panel for a
            // VodInfoPanel showing poster, plot, year, genre, duration, cast
            // and director from get_vod_info. The EPG view here would be
            // empty for movies (no programme data) and the channel list is
            // already shown elsewhere in the app.
            val currentVodInfo = vodInfo
            val isVodPlaylist = playlist?.isVod == true
            val isVodOrSeriesNow = isVodPlaylist || isSeriesPlaylist
            if (isVodOrSeriesNow && currentVodInfo != null) {
                com.m3u.smartphone.ui.business.channel.components.VodInfoPanel(
                    info = currentVodInfo,
                    favourite = channel?.favourite == true,
                    isPlaying = playerState.isPlaying,
                    onPlayPause = {
                        val player = playerState.player
                        if (player != null) {
                            if (playerState.isPlaying) {
                                player.pause()
                            } else {
                                // After stop() we need prepare() to reload
                                // the media. play() alone would no-op.
                                player.prepare()
                                player.play()
                                pullPanelLayoutState.collapse()
                            }
                        }
                    },
                    onToggleFavourite = viewModel::onFavorite,
                    episodes = seriesEpisodes,
                    onPlayEpisode = { ep -> viewModel.onPlayEpisode(ep.id) }
                )
            } else if (isVodOrSeriesNow) {
                // VOD / series but vodInfo is still loading from the network.
                // Show a minimal placeholder with the data we already have
                // from the Channel row (cover + title) instead of falling
                // back to PlayerPanel, which would flash for half a second
                // before the real panel arrives.
                com.m3u.smartphone.ui.business.channel.components.VodInfoPanel(
                    info = com.m3u.business.channel.VodInfo(
                        title = channel?.title.orEmpty(),
                        cover = channel?.cover
                    ),
                    favourite = channel?.favourite == true,
                    isPlaying = playerState.isPlaying,
                    onPlayPause = {
                        val player = playerState.player
                        if (player != null) {
                            if (playerState.isPlaying) player.pause()
                            else {
                                player.play()
                                pullPanelLayoutState.collapse()
                            }
                        }
                    },
                    onToggleFavourite = viewModel::onFavorite,
                    episodes = seriesEpisodes,
                    onPlayEpisode = { ep -> viewModel.onPlayEpisode(ep.id) }
                )
            } else {
                PlayerPanel(
                    title = channel?.title.orEmpty(),
                    playlistTitle = playlist?.title.orEmpty(),
                    channelId = channel?.id ?: -1,
                    isPanelExpanded = isPanelExpanded,
                    isChannelsSupported = !isSeriesPlaylist,
                    isProgrammeSupported = isProgrammeSupported,
                    useVertical = useVertical,
                    channels = channels,
                    programmes = programmes,
                    programmeRange = programmeRange,
                    programmeReminderIds = programmeReminderIds,
                    onRemindProgramme = {
                        requestIgnoreBatteryOptimizations.checkPermissionOrRationale {
                            viewModel.onRemindProgramme(it)
                        }
                    },
                    onCancelRemindProgramme = viewModel::onCancelRemindProgramme,
                    onRequestClosed = { pullPanelLayoutState.collapse() }
                )
            }
        },
        content = {
            ChannelPlayer(
                isSeriesPlaylist = isSeriesPlaylist,
                openDlnaDevices = {
                    viewModel.openDlnaDevices()
                    pullPanelLayoutState.collapse()
                },
                openChooseFormat = {
                    choosing = true
                    pullPanelLayoutState.collapse()
                },
                openOrClosePanel = {
                    if (isPanelExpanded) {
                        pullPanelLayoutState.collapse()
                    } else {
                        pullPanelLayoutState.expand()
                    }
                },
                onFavorite = viewModel::onFavorite,
                maskState = maskState,
                playerState = playerState,
                playlist = playlist,
                adjacentChannels = adjacentChannels,
                channel = channel,
                hasTrack = tracks.isNotEmpty(),
                isPanelExpanded = isPanelExpanded,
                brightness = brightness,
                onBrightness = { brightness = it },
                volume = volume,
                onVolume = viewModel::onVolume,
                brightnessGestureEnabled = brightnessGestureEnabled,
                volumeGestureEnabled = volumeGestureEnabled,
                speed = speed,
                onSpeedUpdated = {
                    viewModel.onSpeedUpdated(it)
                    speed = it
                },
                cwPosition = viewModel.cwPosition,
                onResetPlayback = viewModel::onResetPlayback,
                onPreviousChannelClick = viewModel::getPreviousChannel,
                onNextChannelClick = viewModel::getNextChannel,
                onEnterPipMode = {
                    helper.enterPipMode(playerState.videoSize)
                    maskState.unlockAll()
                    pullPanelLayoutState.collapse()
                },
                onPaddingsChanged = onPaddingsChanged,
                onAlignment = onAlignment
            )
        },
        modifier = modifier
    )

    DlnaDevicesBottomSheet(
        maskState = maskState,
        searching = searching,
        isDevicesVisible = isDevicesVisible,
        devices = devices,
        connectedDeviceUdn = connectedDeviceUdn,
        connectDlnaDevice = { viewModel.connectDlnaDevice(it) },
        onDismiss = { viewModel.closeDlnaDevices() }
    )

    FormatsBottomSheet(
        visible = choosing,
        tracks = tracks,
        selectedTracks = selectedFormats,
        maskState = maskState,
        onDismiss = { choosing = false },
        onChooseTrack = { track ->
            viewModel.chooseTrack(track)
        },
        onClearTrack = { type ->
            viewModel.clearTrack(type)
        }
    )
}

@Composable
private fun ChannelPlayer(
    maskState: MaskState,
    playerState: PlayerState,
    playlist: Playlist?,
    channel: Channel?,
    adjacentChannels: AdjacentChannels?,
    isSeriesPlaylist: Boolean,
    hasTrack: Boolean,
    isPanelExpanded: Boolean,
    brightness: Float,
    volume: Float,
    brightnessGestureEnabled: Boolean,
    volumeGestureEnabled: Boolean,
    speed: Float,
    cwPosition: Long,
    onResetPlayback: () -> Unit,
    onFavorite: () -> Unit,
    openDlnaDevices: () -> Unit,
    openChooseFormat: () -> Unit,
    openOrClosePanel: () -> Unit,
    onVolume: (Float) -> Unit,
    onBrightness: (Float) -> Unit,
    onPreviousChannelClick: () -> Unit,
    onNextChannelClick: () -> Unit,
    onEnterPipMode: () -> Unit,
    onSpeedUpdated: (Float) -> Unit,
    onPaddingsChanged: (Paddings) -> Unit,
    onAlignment: (size: IntSize, space: IntSize) -> IntOffset,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val windowInfo = LocalWindowInfo.current

    val title = channel?.title ?: "--"
    val cover = channel?.cover.orEmpty()
    val playlistTitle = playlist?.title ?: "--"
    val favourite = channel?.favourite ?: false
    var gesture: MaskGesture? by remember { mutableStateOf(null) }
    val currentBrightness by rememberUpdatedState(brightness)
    val currentVolume by rememberUpdatedState(volume)
    val currentSpeed by rememberUpdatedState(speed)

    val clipMode by preferenceOf(PreferencesKeys.CLIP_MODE)

    val useVertical = with(windowInfo.containerSize) { width < height }

    LaunchedEffect(cwPosition) {
        if (cwPosition != -1L) {
            maskState.wake(6.seconds)
        }
    }
    Box(modifier) {
        val state = rememberPlayerState(
            player = playerState.player,
            clipMode = clipMode
        )
        Player(
            state = state,
            modifier = Modifier
                .fillMaxWidth()
                .align { size: IntSize, space: IntSize, _ ->
                    onAlignment(size, space)
                }
        )

        // Loading overlay — visible whenever ExoPlayer is buffering or idle,
        // independent of the mask. Without this the user only sees a black
        // screen during the first 5-10s of channel switching and has no idea
        // whether the app is doing something or stuck. A central spinner +
        // "Conectando…" label removes that ambiguity entirely.
        //
        // Suppress the overlay while the VOD / series "preview" panel is
        // open: in that state we *intentionally* stop the player (STATE_IDLE)
        // so the user can read the synopsis before tapping REPRODUCIR.
        // Showing "Cargando…" there is a lie — nothing is loading.
        val playState = playerState.playState
        val isLoading = playState == Player.STATE_BUFFERING ||
                playState == Player.STATE_IDLE
        val hasError = playerState.playerError != null
        val isVodPreview = (isSeriesPlaylist || playlist?.isVod == true) && isPanelExpanded
        // Debounce the loading overlay: very short buffering events (<700 ms)
        // are invisible UX-wise — flashing a spinner for two frames makes a
        // smooth stream feel worse than it is. We only show "Cargando…" if
        // buffering persists past the debounce window.
        var showLoadingOverlay by remember { mutableStateOf(false) }
        LaunchedEffect(isLoading) {
            if (isLoading) {
                kotlinx.coroutines.delay(700)
                showLoadingOverlay = true
            } else {
                showLoadingOverlay = false
            }
        }
        if (showLoadingOverlay && !hasError && !isVodPreview) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(
                        color = Color.Black.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(14.dp)
                    )
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        strokeWidth = 2.5.dp,
                        color = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                    Text(
                        text = when (playState) {
                            Player.STATE_BUFFERING -> "Conectando…"
                            else -> "Cargando…"
                        },
                        color = Color.White,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        VerticalGestureArea(
            percent = currentBrightness,
            time = 0.65f,
            onDragStart = {
                gesture = MaskGesture.BRIGHTNESS
                maskState.sleep()
            },
            onDragEnd = { gesture = null },
            onDrag = onBrightness,
            onClick = maskState::toggle,
            modifier = Modifier
                .fillMaxHeight(0.7f)
                .fillMaxWidth(0.18f)
                .align(Alignment.CenterStart),
            enabled = brightnessGestureEnabled
        )

        VerticalGestureArea(
            percent = currentVolume,
            time = 0.35f,
            onDragStart = {
                gesture = MaskGesture.VOLUME
                maskState.sleep()
            },
            onDragEnd = { gesture = null },
            onDrag = onVolume,
            onClick = maskState::toggle,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(0.7f)
                .fillMaxWidth(0.18f),
            enabled = volumeGestureEnabled
        )

        ChannelMask(
            adjacentChannels = adjacentChannels,
            title = title,
            playlistTitle = playlistTitle,
            playerState = playerState,
            volume = volume,
            brightness = brightness,
            maskState = maskState,
            favourite = favourite,
            isSeriesPlaylist = isSeriesPlaylist,
            isVodPlaylist = playlist?.isVod == true,
            useVertical = useVertical,
            hasTrack = hasTrack,
            cwPosition = cwPosition,
            onResetPlayback = onResetPlayback,
            isPanelExpanded = isPanelExpanded,
            onFavorite = onFavorite,
            openDlnaDevices = openDlnaDevices,
            openChooseFormat = openChooseFormat,
            openOrClosePanel = openOrClosePanel,
            onVolume = onVolume,
            onEnterPipMode = onEnterPipMode,
            onPreviousChannelClick = onPreviousChannelClick,
            onNextChannelClick = onNextChannelClick,
            onSpeedUpdated = onSpeedUpdated,
            onSpeedStart = { gesture = MaskGesture.SPEED },
            onSpeedEnd = { gesture = null },
            gesture = gesture,
            onPaddingsChanged = onPaddingsChanged
        )

        if (gesture != null) {
            MaskGestureValuePanel(
                value = when (gesture) {
                    MaskGesture.BRIGHTNESS -> "${currentBrightness.times(100).toInt()}%"
                    MaskGesture.VOLUME -> "${currentVolume.times(100).toInt()}"
                    MaskGesture.SPEED -> "${"%.1f".format(currentSpeed)}x"
                    else -> ""
                },
                icon = when (gesture) {
                    MaskGesture.BRIGHTNESS -> when {
                        brightness < 0.5f -> Icons.Rounded.DarkMode
                        else -> Icons.Rounded.LightMode
                    }

                    MaskGesture.VOLUME -> when {
                        volume == 0f -> Icons.AutoMirrored.Rounded.VolumeOff
                        volume < 0.5f -> Icons.AutoMirrored.Rounded.VolumeDown
                        else -> Icons.AutoMirrored.Rounded.VolumeUp
                    }

                    else -> Icons.Rounded.Speed
                },
                modifier = Modifier.align(Alignment.Center)
            )
        }

        LaunchedEffect(playerState.playerError) {
            if (playerState.playerError != null) {
                maskState.wake()
            }
        }
    }
}