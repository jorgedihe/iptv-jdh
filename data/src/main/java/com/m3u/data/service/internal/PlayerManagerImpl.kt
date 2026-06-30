package com.m3u.data.service.internal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackGroup
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.MediaExtractorCompat
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.HttpMediaDrmCallback
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.hls.DefaultHlsExtractorFactory
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.trackselection.TrackSelector
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
import androidx.media3.muxer.FragmentedMp4Muxer
import androidx.media3.muxer.Mp4Muxer
import androidx.media3.session.MediaSession
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.InAppFragmentedMp4Muxer
import androidx.media3.transformer.InAppMp4Muxer
import androidx.media3.transformer.TransformationRequest
import androidx.media3.transformer.Transformer
import com.m3u.core.foundation.architecture.Publisher
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.ReconnectMode
import com.m3u.core.foundation.architecture.preferences.Settings
import com.m3u.core.foundation.architecture.preferences.get
import com.m3u.data.SSLs
import com.m3u.data.api.OkhttpClient
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.copyXtreamEpisode
import com.m3u.data.database.model.copyXtreamSeries
import com.m3u.data.database.model.isSeries
import com.m3u.data.database.model.isVod
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.service.MediaCommand
import com.m3u.data.service.PlayerManager
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.http.Url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

class PlayerManagerImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    @OkhttpClient(false) private val okHttpClient: OkHttpClient,
    private val playlistRepository: PlaylistRepository,
    private val channelRepository: ChannelRepository,
    private val cache: Cache,
    private val settings: Settings,
    publisher: Publisher,
) : PlayerManager, Player.Listener, MediaSession.Callback {
    private val timber = Timber.tag("PlayerManagerImpl")
    private val mainCoroutineScope = CoroutineScope(Dispatchers.Main)
    private val ioCoroutineScope = CoroutineScope(Dispatchers.IO)

    private val channelPreferenceProvider = ChannelPreferenceProvider(
        directory = context.cacheDir.resolve("channel-preferences"),
        appVersion = publisher.versionCode
    )

    private val continueWatchingCondition = ContinueWatchingCondition.getInstance<Player>()

    override val player = MutableStateFlow<ExoPlayer?>(null)
    override val size = MutableStateFlow(Rect())

    private val mediaCommand = MutableStateFlow<MediaCommand?>(null)

    override val playlist: StateFlow<Playlist?> = mediaCommand.flatMapLatest { command ->
        when (command) {
            is MediaCommand.Common -> {
                val channel = channelRepository.get(command.channelId)
                channel?.let { playlistRepository.observe(it.playlistUrl) } ?: flow { }
            }

            is MediaCommand.XtreamEpisode -> {
                val channel = channelRepository.get(command.channelId)
                channel?.let {
                    playlistRepository
                        .observe(it.playlistUrl)
                        .map { prev -> prev?.copyXtreamSeries(channel) }
                } ?: flowOf(null)
            }

            null -> flowOf(null)
        }
    }
        .stateIn(
            scope = ioCoroutineScope,
            initialValue = null,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    override val channel: StateFlow<Channel?> = mediaCommand
        .onEach { timber.d("received media command: $it") }
        .flatMapLatest { command ->
            when (command) {
                is MediaCommand.Common -> channelRepository.observe(command.channelId)
                is MediaCommand.XtreamEpisode -> channelRepository
                    .observe(command.channelId)
                    .map { it?.copyXtreamEpisode(command.episode) }

                else -> flowOf(null)
            }
        }
        .stateIn(
            scope = ioCoroutineScope,
            initialValue = null,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    override val playbackState = MutableStateFlow<@Player.State Int>(Player.STATE_IDLE)
    override val playbackException = MutableStateFlow<PlaybackException?>(null)
    override val isPlaying = MutableStateFlow(false)
    override val tracksGroups = MutableStateFlow<List<Tracks.Group>>(emptyList())

    private val playbackPosition = MutableStateFlow(-1L)

    init {
        mainCoroutineScope.launch {
            playbackState.collectLatest { state ->
                timber.d("onPlaybackStateChanged: $state")
                when (state) {
                    Player.STATE_IDLE -> onPlaybackIdle()
                    Player.STATE_BUFFERING -> onPlaybackBuffering()
                    Player.STATE_READY -> onPlaybackReady()
                    Player.STATE_ENDED -> onPlaybackEnded()
                }
            }
        }
        mainCoroutineScope.launch {
            while (true) {
                ensureActive()
                playbackPosition.value = player.value?.currentPosition ?: -1L
                delay(1.seconds)
            }
        }
    }

    override suspend fun play(
        command: MediaCommand,
        applyContinueWatching: Boolean
    ) {
        timber.d("play")
        release()
        mediaCommand.value = command
        val channel = when (command) {
            is MediaCommand.Common -> channelRepository.get(command.channelId)
            is MediaCommand.XtreamEpisode -> channelRepository
                .get(command.channelId)
                ?.copyXtreamEpisode(command.episode)
        }
        if (channel != null) {
            val channelUrl = channel.url
            val channelPreference = getChannelPreference(channelUrl)
            val licenseType = channel.licenseType.orEmpty()
            val licenseKey = channel.licenseKey.orEmpty()

            channelRepository.reportPlayed(channel.id)

            val playlist = playlistRepository.get(channel.playlistUrl)
            val userAgent = getUserAgent(channelUrl, playlist)

            this.chain = channelPreference?.mineType
                ?.let { MimetypeChain.Remembered(channelUrl, it) }
                ?: MimetypeChain.Unspecified(channelUrl)

            timber.d("init mimetype: $chain")

            tryPlay(
                url = channelUrl,
                userAgent = userAgent,
                licenseType = licenseType,
                licenseKey = licenseKey,
                applyContinueWatching = applyContinueWatching
            )
        }
    }

    private var extractor: MediaExtractorCompat? = null
    private suspend fun tryPlay(
        url: String = channel.value?.url.orEmpty(),
        userAgent: String? = getUserAgent(channel.value?.url.orEmpty(), playlist.value),
        licenseType: String = channel.value?.licenseType.orEmpty(),
        licenseKey: String = channel.value?.licenseKey.orEmpty(),
        applyContinueWatching: Boolean
    ) {
        // RTMP datasource module dropped in v1.0.66 (16 KB page-size policy).
        // If a user-supplied URL is rtmp://, fall through to the HTTP factory;
        // ExoPlayer will fail to play but won't crash — and IPTV providers
        // virtually never serve RTMP anyway (only HLS / MPEG-TS HTTP / RTSP).
        val tunneling = settings[PreferencesKeys.TUNNELING]

        val mimeType = when (val chain = chain) {
            is MimetypeChain.Remembered -> chain.mimeType
            is MimetypeChain.Trying -> chain.mimetype
            is MimetypeChain.Unspecified -> {
                this.chain = chain.next()
                return tryPlay(url, userAgent, licenseType, licenseKey, applyContinueWatching)
            }

            is MimetypeChain.Unsupported -> throw UnsupportedOperationException()
        }

        timber.d("tryPlay, mimetype: $mimeType, url: $url, user-agent: $userAgent")
        // Disk cache only makes sense for seekable VOD / series. Live streams
        // and rare formats (HLS live edge, RTSP) skip it.
        val pl = playlist.value
        val useCache = pl != null && (pl.isVod || pl.isSeries)
        val dataSourceFactory = createHttpDataSourceFactory(userAgent, useCache = useCache)
        // BUGFIX: these two are bit flags (1 and 2). Using `and` produced 0
        // and silently disabled BOTH flags, leaving the MPEG-TS extractor
        // unable to align on access units. Result: live channels would
        // randomly freeze and "jump forward" when the decoder resynced.
        // The correct combinator is bitwise OR.
        val extractorsFactory = DefaultExtractorsFactory().setTsExtractorFlags(
            FLAG_ALLOW_NON_IDR_KEYFRAMES or FLAG_DETECT_ACCESS_UNITS
        )
        extractor = MediaExtractorCompat(extractorsFactory, dataSourceFactory)
        val mediaSourceFactory = when (mimeType) {
            MimeTypes.APPLICATION_M3U8 -> HlsMediaSource.Factory(dataSourceFactory)
                .setAllowChunklessPreparation(false)
                .setExtractorFactory(DefaultHlsExtractorFactory())

            MimeTypes.APPLICATION_SS -> ProgressiveMediaSource.Factory(
                dataSourceFactory,
                extractorsFactory
            )

            MimeTypes.APPLICATION_RTSP -> RtspMediaSource.Factory()
                .setDebugLoggingEnabled(true)
                .setForceUseRtpTcp(true)
                .setSocketFactory(SSLs.TLSTrustAll.socketFactory)

            else -> DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
        }
        timber.d("media-source-factory: ${mediaSourceFactory::class.qualifiedName}")
        if (licenseType.isNotEmpty()) {
            val drmCallback = when {
                (licenseType in arrayOf(
                    Channel.LICENSE_TYPE_CLEAR_KEY,
                    Channel.LICENSE_TYPE_CLEAR_KEY_2
                )) && !licenseKey.startsWith("http") -> LocalMediaDrmCallback(licenseKey.toByteArray())

                else -> HttpMediaDrmCallback(
                    licenseKey,
                    dataSourceFactory
                )
            }
            val uuid = when (licenseType) {
                Channel.LICENSE_TYPE_CLEAR_KEY, Channel.LICENSE_TYPE_CLEAR_KEY_2 -> C.CLEARKEY_UUID
                Channel.LICENSE_TYPE_WIDEVINE -> C.WIDEVINE_UUID
                Channel.LICENSE_TYPE_PLAY_READY -> C.PLAYREADY_UUID
                else -> C.UUID_NIL
            }
            if (uuid != C.UUID_NIL && FrameworkMediaDrm.isCryptoSchemeSupported(uuid)) {
                val drmSessionManager = DefaultDrmSessionManager.Builder()
                    .setUuidAndExoMediaDrmProvider(uuid, FrameworkMediaDrm.DEFAULT_PROVIDER)
                    .setMultiSession(
                        licenseType !in arrayOf(
                            Channel.LICENSE_TYPE_CLEAR_KEY,
                            Channel.LICENSE_TYPE_CLEAR_KEY_2
                        )
                    )
                    .build(drmCallback)
                mediaSourceFactory.setDrmSessionManagerProvider { drmSessionManager }
            }
        }
        val player = player.updateAndGet { prev ->
            timber.d("player instance updated")
            prev ?: createPlayer(mediaSourceFactory, tunneling)
        }!!
        val mediaItem = MediaItem.fromUri(url)
        val mediaSource: MediaSource = mediaSourceFactory.createMediaSource(mediaItem)
        player.setMediaSource(mediaSource)
        player.prepare()
        mainCoroutineScope.launch {
            if (applyContinueWatching) {
                restoreContinueWatching(player, url)
            } else {
                cwPosition.emit(-1L)
            }
        }
    }

    override suspend fun replay() {
        val prev = mediaCommand.value
        release()
        prev?.let { play(it, applyContinueWatching = false) }
    }

    override fun release() {
        timber.d("release")
        extractor = null
        player.update {
            it ?: return
            it.stop()
            it.release()
            it.removeListener(this)
            mediaCommand.value = null
            size.value = Rect()
            playbackState.value = Player.STATE_IDLE
            playbackException.value = null
            tracksGroups.value = emptyList()
            chain = MimetypeChain.Unsupported(chain.url)
            null
        }
    }

    override fun clearCache() {
        cache.keys.forEach {
            cache.getCachedSpans(it).forEach { span ->
                cache.removeSpan(span)
            }
        }
    }

    override fun chooseTrack(group: TrackGroup, index: Int) {
        val currentPlayer = player.value ?: return
        val type = group.type
        val override = TrackSelectionOverride(group, index)
        currentPlayer.trackSelectionParameters = currentPlayer.trackSelectionParameters
            .buildUpon()
            .setOverrideForType(override)
            .setTrackTypeDisabled(type, false)
            .build()
    }

    override fun clearTrack(type: @C.TrackType Int) {
        val currentPlayer = player.value ?: return
        val builder = currentPlayer.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(type)
        if (type == C.TRACK_TYPE_TEXT) {
            builder.setTrackTypeDisabled(type, true)
        } else {
            builder.setTrackTypeDisabled(type, false)
        }
        currentPlayer.trackSelectionParameters = builder.build()
    }

    override val cacheSpace: Flow<Long> = flow {
        while (true) {
            emit(cache.cacheSpace)
            delay(1.seconds)
        }
    }
        .flowOn(Dispatchers.IO)

    override suspend fun reloadThumbnail(channelUrl: String): Uri? {
        val channelPreference = getChannelPreference(channelUrl)
        return channelPreference?.thumbnail
    }

    private val thumbnailDir by lazy {
        context.cacheDir.resolve("thumbnails").apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }

    override suspend fun syncThumbnail(channelUrl: String): Uri? = withContext(Dispatchers.IO) {
        val thumbnail = Codecs.getThumbnail(context, channelUrl.toUri()) ?: return@withContext null
        val filename = UUID.randomUUID().toString() + ".jpeg"
        val file = File(thumbnailDir, filename)
        while (!file.createNewFile()) {
            ensureActive()
            file.delete()
        }
        FileOutputStream(file).use {
            thumbnail.compress(Bitmap.CompressFormat.JPEG, 50, it)
        }
        val uri = file.toUri()
        addChannelPreference(
            channelUrl,
            getChannelPreference(channelUrl)?.copy(
                thumbnail = uri
            ) ?: ChannelPreference(thumbnail = uri)
        )
        uri
    }

    private fun createPlayer(
        mediaSourceFactory: MediaSource.Factory,
        tunneling: Boolean
    ): ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(mediaSourceFactory)
        .setRenderersFactory(renderersFactory)
        .setTrackSelector(createTrackSelector(tunneling))
        .setHandleAudioBecomingNoisy(true)
        // LoadControl tuned aggressively for live IPTV MPEG-TS streams (where
        // ABR cannot rescale quality, only buffer absorbs bandwidth dips):
        //  - maxBufferMs 240 s lets the player stockpile up to 4 min when the
        //    Wi-Fi is fast, so a sustained dip below the stream bitrate can
        //    consume that reserve without ever pausing.
        //  - minBufferMs 60 s keeps the playback cursor far from the head of
        //    the buffer so small jitter never empties it.
        //  - bufferForPlaybackAfterRebufferMs 3 s: if we DO rebuffer, resume
        //    fast (3 s) instead of forcing a long 10 s wait that's visible
        //    as a big "Cargando…" overlay.
        //  - prioritizeTimeOverSizeThresholds(true) makes the buffer policy
        //    measure DURATION instead of bytes, so high-bitrate channels
        //    (La Sexta HD ~5 Mbps) don't trigger early back-pressure.
        .setLoadControl(
            androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    /* minBufferMs */ 60_000,
                    /* maxBufferMs */ 240_000,
                    /* bufferForPlaybackMs */ 6_000,
                    /* bufferForPlaybackAfterRebufferMs */ 3_000
                )
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        )
        .build()
        .apply {
            val attributes = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            setAudioAttributes(attributes, true)
            playWhenReady = true
            addListener(this@PlayerManagerImpl)
        }

    private val renderersFactory: RenderersFactory by lazy {
        Codecs.createRenderersFactory(context)
    }

    private fun createTrackSelector(tunneling: Boolean): TrackSelector {
        return DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    // Was: setForceHighestSupportedBitrate(true). On unstable
                    // Wi-Fi that produced 1–3 s freezes when the link briefly
                    // dipped below the max bitrate. Leaving the ABR algorithm
                    // free to pick a lower rendition keeps playback smooth at
                    // the cost of a momentary quality dip.
                    .setTunnelingEnabled(tunneling)
            )
        }
    }

    /**
     * Live channels are streamed (no useful caching) but VOD / series benefit
     * from a disk cache: seeking back inside an episode is instant, and brief
     * network blips re-read from the cache instead of refetching. We wrap the
     * OkHttp upstream in a CacheDataSource when the playlist source supports
     * seek (VOD/series); live and other never-seekable sources keep the bare
     * upstream so we don't waste cache space on transient bytes.
     */
    private fun createHttpDataSourceFactory(
        userAgent: String?,
        useCache: Boolean = false
    ): DataSource.Factory {
        val upstream = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(userAgent)
        return if (useCache) {
            androidx.media3.datasource.cache.CacheDataSource.Factory()
                .setUpstreamDataSourceFactory(upstream)
                .setCache(cache)
                .setFlags(androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        } else {
            upstream
        }
    }

    override fun onVideoSizeChanged(videoSize: VideoSize) {
        super.onVideoSizeChanged(videoSize)
        timber.d("onVideoSizeChanged, [${videoSize.toRect()}]")
        size.value = videoSize.toRect()
    }

    override fun onPlaybackStateChanged(state: Int) {
        super.onPlaybackStateChanged(state)
        playbackState.value = state
    }

    override fun onPlayerErrorChanged(exception: PlaybackException?) {
        super.onPlayerErrorChanged(exception)
        when (val errorCode = exception?.errorCode) {
            PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                timber.w("onPlayerErrorChanged, ERROR_CODE_BEHIND_LIVE_WINDOW, trying to replay")
                player.value?.let {
                    it.seekToDefaultPosition()
                    it.prepare()
                }
            }

            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED -> {
                timber.w("onPlayerErrorChanged, ${PlaybackException.getErrorCodeName(errorCode)}")
                when (val chain = chain) {
                    is MimetypeChain.Remembered -> {
                        ioCoroutineScope.launch {
                            val channelPreference = getChannelPreference(chain.url)
                            if (channelPreference != null) {
                                addChannelPreference(
                                    chain.url,
                                    channelPreference.copy(mineType = null)
                                )
                            }
                        }
                    }

                    else -> {}
                }
                if (chain.hasNext()) {
                    val next = chain.next()
                    chain = next
                    when (next) {
                        is MimetypeChain.Unsupported -> {
                            playbackException.value = exception
                        }

                        else -> mainCoroutineScope.launch { tryPlay(applyContinueWatching = false) }
                    }
                }
            }

            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> {
                // Transient network hiccups on live IPTV streams: instead of
                // surfacing the error and stopping playback, re-prepare the
                // player so it reconnects to the upstream within ~1 s. This
                // recovers cleanly from short Wi-Fi blips that would
                // otherwise show as "Cargando…" stuck on screen.
                timber.w("Transient IO error (${PlaybackException.getErrorCodeName(errorCode)}), auto-reconnecting")
                player.value?.let {
                    it.seekToDefaultPosition()
                    it.prepare()
                }
            }

            else -> {
                if (exception != null) {
                    timber.e(exception, PlaybackException.getErrorCodeName(exception.errorCode))
                }
                playbackException.value = exception
            }
        }
    }

    override fun onTracksChanged(tracks: Tracks) {
        super.onTracksChanged(tracks)
        player.value?.isPlaying
        tracksGroups.value = tracks.groups
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        this.isPlaying.value = isPlaying
    }

    override fun pauseOrContinue(value: Boolean) {
        player.value?.apply {
            if (!value) pause() else {
                playWhenReady = true
                prepare()
            }
        }
    }

    override fun updateSpeed(race: Float) {
        player.value?.apply {
            setPlaybackSpeed(race.coerceAtLeast(0.1f))
        }
    }

    override suspend fun recordVideo(uri: Uri) {
        withContext(Dispatchers.Main) {
            try {
                val currentPlayer = player.value ?: return@withContext
                val tracksGroup = currentPlayer.currentTracks.groups.first {
                    it.type == C.TRACK_TYPE_VIDEO
                } ?: return@withContext
                val formats = (0 until tracksGroup.length).mapNotNull {
                    if (!tracksGroup.isTrackSupported(it)) null
                    else tracksGroup.getTrackFormat(it)
                }
                    .mapNotNull { it.containerMimeType ?: it.sampleMimeType }
                val (mimeType, muxerFactory) = when {
                    formats.any { it in FragmentedMp4Muxer.SUPPORTED_VIDEO_SAMPLE_MIME_TYPES } -> {
                        val mimeType =
                            formats.first { it in FragmentedMp4Muxer.SUPPORTED_VIDEO_SAMPLE_MIME_TYPES }
                        val muxerFactory = InAppFragmentedMp4Muxer.Factory()
                        mimeType to muxerFactory
                    }

                    formats.any { it in Mp4Muxer.SUPPORTED_VIDEO_SAMPLE_MIME_TYPES } -> {
                        val mimeType =
                            formats.first { it in Mp4Muxer.SUPPORTED_VIDEO_SAMPLE_MIME_TYPES }
                        val muxerFactory = InAppMp4Muxer.Factory()
                        mimeType to muxerFactory
                    }

                    else -> {
                        timber.e("Failed to record frame, Unsupported video formats: $formats")
                        return@withContext
                    }
                }
                val transformer = Transformer.Builder(context)
                    .setMuxerFactory(muxerFactory)
                    .setVideoMimeType(mimeType)
                    .setEncoderFactory(
                        DefaultEncoderFactory.Builder(context.applicationContext)
                            .setEnableFallback(true)
//                        .setRequestedVideoEncoderSettings(
//                            VideoEncoderSettings.Builder()
//                                .
//                                .build()
//                        )
                            .build()
                    )
                    .addListener(
                        object : Transformer.Listener {
                            override fun onCompleted(
                                composition: Composition,
                                exportResult: ExportResult
                            ) {
                                super.onCompleted(composition, exportResult)
                                timber.d("transformer, onCompleted")
                            }

                            override fun onError(
                                composition: Composition,
                                exportResult: ExportResult,
                                exportException: ExportException
                            ) {
                                super.onError(composition, exportResult, exportException)
                                timber.e(exportException, "transformer, onError")
                            }

                            override fun onFallbackApplied(
                                composition: Composition,
                                originalTransformationRequest: TransformationRequest,
                                fallbackTransformationRequest: TransformationRequest
                            ) {
                                super.onFallbackApplied(
                                    composition,
                                    originalTransformationRequest,
                                    fallbackTransformationRequest
                                )
                            }
                        }
                    )
                    .build()

                withContext(Dispatchers.Main) {
                    transformer.start(
                        MediaItem.fromUri(channel.value?.url.orEmpty()),
                        uri.path.orEmpty()
                    )
                }
            } finally {
                timber.d("Record frame completed")
            }
        }
    }

    override val cwPosition = MutableSharedFlow<Long>(replay = 1)

    override suspend fun onResetPlayback(channelUrl: String) {
        cwPosition.emit(-1L)
        resetContinueWatching(channelUrl, ignorePositionCondition = true)
        val currentPlayer = player.value ?: return
        if (currentPlayer.isCommandAvailable(Player.COMMAND_SEEK_TO_DEFAULT_POSITION)) {
            currentPlayer.seekToDefaultPosition()
        }
    }

    override suspend fun getCwPosition(channelUrl: String): Long {
        val channelPreference = getChannelPreference(channelUrl)
        return channelPreference?.cwPosition ?: -1L
    }

    override suspend fun getCwSnapshot(channelUrl: String): PlayerManager.CwSnapshot? {
        val pref = getChannelPreference(channelUrl) ?: return null
        val pos = pref.cwPosition
        if (pos <= 0L) return null
        return PlayerManager.CwSnapshot(position = pos, duration = pref.cwDuration)
    }

    override suspend fun clearCwPosition(channelUrl: String) {
        val pref = getChannelPreference(channelUrl) ?: return
        addChannelPreference(
            channelUrl,
            pref.copy(cwPosition = -1L, cwDuration = -1L)
        )
    }

    private suspend fun onPlaybackIdle() {}
    private suspend fun onPlaybackBuffering() {}

    private suspend fun onPlaybackReady() {
        timber.d("onPlaybackReady, trying the playChain $chain")
        when (val chain = chain) {
            is MimetypeChain.Remembered -> {
                storeContinueWatching(chain.url)
            }

            is MimetypeChain.Trying -> {
                val channelPreference = getChannelPreference(chain.url)
                addChannelPreference(
                    chain.url,
                    channelPreference?.copy(mineType = chain.mimetype)
                        ?: ChannelPreference(mineType = chain.mimetype)
                )
                storeContinueWatching(chain.url)
            }

            else -> {}
        }
    }

    private suspend fun onPlaybackEnded() {
        if (settings[PreferencesKeys.RECONNECT_MODE] == ReconnectMode.RECONNECT) {
            mainCoroutineScope.launch { replay() }
        }
        val channelUrl = chain.url
        if (channelUrl.isNotEmpty()) {
            resetContinueWatching(channelUrl)
        }
    }

    @OptIn(FlowPreview::class)
    private suspend fun storeContinueWatching(channelUrl: String) {
        timber.d("start storeContinueWatching")
        // avoid memory leaks caused by loops
        fun checkContinueWatching(): Boolean {
            val currentPlayer = player.value ?: return false
            return continueWatchingCondition.isStoringSupported(currentPlayer)
        }
        if (!checkContinueWatching()) {
            timber.w("failed to storeContinueWatching, playback is not supported.")
            return
        }
        playbackPosition
            .sample(5.seconds)
            .collect { newCwPosition ->
                timber.d("storeContinueWatching, received new position: $newCwPosition")
                if (newCwPosition == -1L) return@collect
                val newCwDuration = player.value?.contentDuration?.takeIf { it > 0 } ?: -1L
                val channelPreference = getChannelPreference(channelUrl)
                addChannelPreference(
                    channelUrl,
                    channelPreference?.copy(cwPosition = newCwPosition, cwDuration = newCwDuration)
                        ?: ChannelPreference(cwPosition = newCwPosition, cwDuration = newCwDuration)
                )
                // Also mirror to the DB so the "Continue watching" row can enumerate.
                channel.value?.id?.let { id ->
                    channelRepository.updatePlaybackProgress(
                        id = id,
                        position = newCwPosition,
                        duration = newCwDuration.coerceAtLeast(0L),
                        updatedAt = kotlin.time.Clock.System.now().toEpochMilliseconds()
                    )
                }
            }
    }

    private suspend fun restoreContinueWatching(player: Player, channelUrl: String) {
        val channelPreference = getChannelPreference(channelUrl)
        val cachedCwPosition = channelPreference?.cwPosition?.takeIf { it != -1L } ?: run {
            cwPosition.emit(-1L)
            return
        }
        withContext(Dispatchers.Main) {
            if (continueWatchingCondition.isRestoringSupported(player)) {
                timber.d("restoreContinueWatching, $cachedCwPosition")
                cwPosition.emit(cachedCwPosition)
                player.seekTo(cachedCwPosition)
            }
        }
    }

    private suspend fun resetContinueWatching(
        channelUrl: String,
        ignorePositionCondition: Boolean = false
    ) {
        timber.d("resetContinueWatching, channelUrl=$channelUrl, ignorePositionCondition=$ignorePositionCondition")
        val channelPreference = getChannelPreference(channelUrl)
        val player = this@PlayerManagerImpl.player.value
        withContext(Dispatchers.Main) {
            if (player != null && continueWatchingCondition.isResettingSupported(
                    player,
                    ignorePositionCondition
                )
            ) {
                addChannelPreference(
                    channelUrl,
                    channelPreference?.copy(cwPosition = -1L, cwDuration = -1L)
                        ?: ChannelPreference(cwPosition = -1L, cwDuration = -1L)
                )
                // Also drop the DB row so the "Continue watching" carousel removes it.
                channel.value?.id?.let { channelRepository.clearPlaybackProgress(it) }
            }
        }
    }

    private var chain: MimetypeChain = MimetypeChain.Unsupported("")

    /**
     * Get the kodi url options like this:
     * http://host[:port]/directory/file?a=b&c=d|option1=value1&option2=value2
     * Will get:
     * {option1=value1, option2=value2}
     *
     * https://kodi.wiki/view/HTTP
     */
    private fun String.readKodiUrlOptions(): Map<String, String?> {
        val index = this.indexOf('|')
        if (index == -1) return emptyMap()
        val options = this.drop(index + 1).split("&")
        return options
            .filter { it.isNotBlank() }
            .associate {
                val pair = it.split("=")
                val key = pair.getOrNull(0).orEmpty()
                val value = pair.getOrNull(1)
                key to value
            }
    }

    /**
     * Read user-agent appended to the channelUrl.
     * If there is no result from url, it will use playlist user-agent instead.
     */
    private fun getUserAgent(channelUrl: String, playlist: Playlist?): String? {
        val kodiUrlOptions = channelUrl.readKodiUrlOptions()
        val userAgent = kodiUrlOptions[KodiAdaptions.HTTP_OPTION_UA] ?: playlist?.userAgent
        return userAgent
    }

    private suspend fun getChannelPreference(channelUrl: String): ChannelPreference? {
        if (channelUrl.isEmpty()) return null
        return channelPreferenceProvider[channelUrl]
    }

    private suspend fun addChannelPreference(
        channelUrl: String,
        channelPreference: ChannelPreference
    ) {
        if (channelUrl.isEmpty()) return
        channelPreferenceProvider[channelUrl] = channelPreference
    }
}

fun VideoSize.toRect(): Rect {
    return Rect(0, 0, width, height)
}

private sealed class MimetypeChain(val url: String) {
    class Remembered(
        url: String,
        val mimeType: String
    ) : MimetypeChain(url)

    class Unspecified(url: String) : MimetypeChain(url)
    class Trying(url: String, val mimetype: String) : MimetypeChain(url)
    class Unsupported(url: String) : MimetypeChain(url)

    companion object {
        val ORDERS = arrayOf(
            MimeTypes.APPLICATION_SS,
            MimeTypes.APPLICATION_M3U8,
            MimeTypes.APPLICATION_MPD,
            MimeTypes.APPLICATION_RTSP
        )
    }

    override fun toString(): String = when (this) {
        is Unspecified -> "Unspecified[$url]"
        is Trying -> "Trying[$url, $mimetype]"
        is Remembered -> "Remembered[$url, $mimeType]"
        is Unsupported -> "Unsupported[$url]"
    }

    operator fun hasNext(): Boolean = this !is Unsupported

    operator fun next(): MimetypeChain = when (this) {
        is Unspecified -> Trying(url, ORDERS.first())
        is Trying -> {
            ORDERS
                .getOrNull(ORDERS.indexOf(mimetype) + 1)
                ?.let { Trying(url, it) }
                ?: Unsupported(url)
        }

        is Remembered -> Unspecified(url)

        else -> throw IllegalArgumentException()
    }
}
