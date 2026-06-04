package com.m3u.business.channel

import android.annotation.SuppressLint
import android.media.AudioManager
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.m3u.core.foundation.util.coroutine.flatmapCombined
import com.m3u.core.foundation.wrapper.Sort
import com.m3u.data.database.model.AdjacentChannels
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.ProgrammeRange
import com.m3u.data.database.model.isSeries
import com.m3u.data.database.model.isVod
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.programme.ProgrammeRepository
import com.m3u.data.service.MediaCommand
import com.m3u.data.service.PlayerManager
import com.m3u.data.service.PlayerTrack
import com.m3u.data.service.currentTracks
import com.m3u.data.service.tracks
import com.m3u.data.worker.ProgrammeReminder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.mm2d.upnp.Device
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@HiltViewModel
class ChannelViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val channelRepository: ChannelRepository,
    private val playerManager: PlayerManager,
    private val audioManager: AudioManager,
    private val programmeRepository: ProgrammeRepository,
    private val workManager: WorkManager,
) : ViewModel() {

    /**
     * it is not real-time position but last played position.
     */
    var cwPosition: Long by mutableLongStateOf(-1L)
        private set

    private val cwPositionConsumer = CwPositionConsumer(
        flow = playerManager.cwPosition,
        coroutineScope = viewModelScope
    ) {
        this@ChannelViewModel.cwPosition = it
    }

    fun onResetPlayback() {
        val channelUrl = channel.value?.url ?: return
        viewModelScope.launch {
            playerManager.onResetPlayback(channelUrl)
        }
    }

    fun onMaskStateChanged(visible: Boolean) {
        if (!visible) {
            cwPositionConsumer.notifyConsumed()
        }
    }


    private val _volume: MutableStateFlow<Float> by lazy {
        MutableStateFlow(
            with(audioManager) {
                getStreamVolume(AudioManager.STREAM_MUSIC) * 1f / getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            }
        )
    }
    val volume = _volume.asStateFlow()

    val channel: StateFlow<Channel?> = playerManager.channel
    val playlist: StateFlow<Playlist?> = playerManager.playlist

    val adjacentChannels: StateFlow<AdjacentChannels?> = flatmapCombined(
        playlist,
        channel
    ) { playlist, channel ->
        playlist ?: return@flatmapCombined flowOf(null)
        channel ?: return@flatmapCombined flowOf(null)
        channelRepository.observeAdjacentChannels(
            channelId = channel.id,
            playlistUrl = playlist.url,
            category = channel.category
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )


    val isSeriesPlaylist: Flow<Boolean> = playlist.map { it?.isSeries ?: false }
    val isVodPlaylist: Flow<Boolean> = playlist.map { it?.isVod ?: false }

    /** Episodes of the currently playing series, lazily loaded once the
     *  channel resolves to a series row in an Xtream provider. Empty for VOD
     *  / Live TV / any non-Xtream playlist. */
    val seriesEpisodes: StateFlow<List<EpisodeRow>> = flatmapCombined(playlist, channel) { p, c ->
        if (p == null || c == null || p.source != DataSource.Xtream || !p.isSeries) {
            return@flatmapCombined kotlinx.coroutines.flow.flowOf(emptyList())
        }
        kotlinx.coroutines.flow.flow<List<EpisodeRow>> {
            emit(emptyList())
            val raw = kotlinx.coroutines.withContext(Dispatchers.IO) {
                runCatching { playlistRepository.readEpisodesOrThrow(c) }.getOrDefault(emptyList())
            }
            // XtreamEpisodeInfo only carries id, episodeNum and the raw
            // title. Extract season number by matching SxxExx in the title;
            // fall back to 1 when not present.
            val sxxExx = Regex("""S(\d+)E(\d+)""", RegexOption.IGNORE_CASE)
            val initial = raw.map { ep ->
                val match = sxxExx.find(ep.title.orEmpty())
                val season = match?.groupValues?.get(1)?.toIntOrNull() ?: 1
                val episodeNumber = ep.episodeNum?.toIntOrNull()
                    ?: match?.groupValues?.get(2)?.toIntOrNull() ?: 0
                val cleanTitle = ep.title.orEmpty()
                    .substringAfter(" - ${match?.value ?: ""}", ep.title.orEmpty())
                    .trim().trim('-').trim()
                    .ifBlank { "Episodio $episodeNumber" }
                EpisodeRow(
                    id = ep.id.orEmpty(),
                    seasonNumber = season,
                    episodeNumber = episodeNumber,
                    title = cleanTitle,
                )
            }.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))
            emit(initial)

            // Enrich with TMDB per-episode stills + overviews when we have
            // a tmdb_id for the series and a baked-in API key. We fetch one
            // season at a time (most series have 1-2 seasons in IPTV) and
            // re-emit the list with the new fields populated. Failures stay
            // silent — the user still sees the basic episode rows.
            val seriesTmdbId = kotlinx.coroutines.withContext(Dispatchers.IO) {
                runCatching {
                    val input = com.m3u.data.parser.xtream.XtreamInput
                        .decodeFromPlaylistUrl(p.url)
                    val seriesId = c.url
                        .substringAfter("/series/${input.username}/${input.password}/")
                        .substringBefore('.')
                        .substringBefore('?')
                    seriesId
                }.getOrNull()
            }
            // Use the tmdb id baked into the series' VOD info — we already
            // have it in [vodInfo] but it's a separate flow, so for the
            // episodes path we just rely on Harmony's tmdb_id field.
            val tmdbId = kotlinx.coroutines.withContext(Dispatchers.IO) {
                runCatching {
                    val input = com.m3u.data.parser.xtream.XtreamInput
                        .decodeFromPlaylistUrl(p.url)
                    val sid = c.url
                        .substringAfter("/series/${input.username}/${input.password}/")
                        .substringBefore('.')
                        .substringBefore('?')
                    if (sid.isBlank() || !sid.all { it.isDigit() }) return@runCatching null
                    val url = "${input.basicUrl}/player_api.php?username=${input.username}" +
                            "&password=${input.password}&action=get_series_info&series_id=$sid"
                    val req = okhttp3.Request.Builder().url(url).build()
                    okhttp3.OkHttpClient().newCall(req).execute().use { resp ->
                        if (!resp.isSuccessful) return@runCatching null
                        val body = resp.body.string()
                        val root = vodInfoJson.parseToJsonElement(body)
                            as? kotlinx.serialization.json.JsonObject
                            ?: return@runCatching null
                        val info = root["info"] as? kotlinx.serialization.json.JsonObject
                            ?: return@runCatching null
                        (info["tmdb"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
                            ?: (info["tmdb_id"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() && it != "null" }
                    }
                }.getOrNull()
            }
            if (!tmdbId.isNullOrBlank() && BuildConfig.TMDB_API_KEY.isNotBlank()) {
                val seasonsPresent = initial.map { it.seasonNumber }.toSet()
                val seasonMeta = seasonsPresent.associateWith { season ->
                    TmdbCredits.episodesForSeason(tmdbId, season, BuildConfig.TMDB_API_KEY)
                }
                val enriched = initial.map { row ->
                    val meta = seasonMeta[row.seasonNumber]?.get(row.episodeNumber)
                        ?: return@map row
                    row.copy(
                        title = meta.tmdbName?.takeIf { it.isNotBlank() } ?: row.title,
                        stillUrl = meta.stillUrl,
                        overview = meta.overview,
                    )
                }
                emit(enriched)
            }
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    /** Play a specific episode of the currently playing series. */
    fun onPlayEpisode(episodeId: String) {
        val series = channel.value ?: return
        viewModelScope.launch {
            val episodes = runCatching {
                playlistRepository.readEpisodesOrThrow(series)
            }.getOrNull() ?: return@launch
            val target = episodes.firstOrNull { it.id.toString() == episodeId } ?: return@launch
            playerManager.play(
                com.m3u.data.service.MediaCommand.XtreamEpisode(
                    channelId = series.id,
                    episode = target
                )
            )
        }
    }

    /**
     * VOD metadata for the currently playing movie. Populated when both the
     * playlist is a VOD/Series Xtream list AND the channel resolves to a
     * stream_id we can query via player_api's get_vod_info action. Null in
     * every other case (Live TV, plain M3U, errors).
     */
    val vodInfo: StateFlow<VodInfo?> = flatmapCombined(playlist, channel) { p, c ->
        if (p == null || c == null) return@flatmapCombined kotlinx.coroutines.flow.flowOf(null)
        if (p.source != DataSource.Xtream || !(p.isVod || p.isSeries)) {
            return@flatmapCombined kotlinx.coroutines.flow.flowOf(null)
        }
        kotlinx.coroutines.flow.flow<VodInfo?> {
            emit(null)
            emit(loadVodInfoOrNull(p, c))
        }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    private val vodInfoHttp by lazy { okhttp3.OkHttpClient() }
    private val vodInfoJson by lazy {
        kotlinx.serialization.json.Json { ignoreUnknownKeys = true; isLenient = true }
    }

    /** Hit player_api.php?action=get_vod_info and shape the JSON into [VodInfo]. */
    private suspend fun loadVodInfoOrNull(playlist: Playlist, channel: Channel): VodInfo? =
        kotlinx.coroutines.withContext(Dispatchers.IO) {
            runCatching {
                val input = com.m3u.data.parser.xtream.XtreamInput.decodeFromPlaylistUrl(playlist.url)
                // VOD URL looks like /movie/<u>/<p>/<stream_id>.<ext>, series URL like /series/<u>/<p>/<series_id>
                val pathSegment = if (playlist.isVod) "movie" else "series"
                val idSegment = channel.url.substringAfter("/$pathSegment/${input.username}/${input.password}/")
                    .substringBefore('.')
                    .substringBefore('?')
                if (idSegment.isBlank() || !idSegment.all { it.isDigit() }) return@runCatching null
                val action = if (playlist.isVod) "get_vod_info" else "get_series_info"
                val idParam = if (playlist.isVod) "vod_id" else "series_id"
                val url = "${input.basicUrl}/player_api.php?username=${input.username}" +
                        "&password=${input.password}&action=$action&$idParam=$idSegment"
                val request = okhttp3.Request.Builder().url(url).build()
                vodInfoHttp.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val body = response.body.string()
                    val root = vodInfoJson.parseToJsonElement(body) as? kotlinx.serialization.json.JsonObject
                        ?: return@runCatching null
                    // Xtream wraps the payload under "info" for VOD and "info" too for series.
                    val info = (root["info"] as? kotlinx.serialization.json.JsonObject) ?: root
                    fun s(name: String): String? {
                        val prim = info[name] as? kotlinx.serialization.json.JsonPrimitive ?: return null
                        if (prim is kotlinx.serialization.json.JsonNull) return null
                        return prim.content.takeIf { it.isNotBlank() && it != "null" }
                    }
                    val backdrop = (info["backdrop_path"] as? kotlinx.serialization.json.JsonArray)
                        ?.firstOrNull()
                        ?.let { it as? kotlinx.serialization.json.JsonPrimitive }
                        ?.takeIf { it !is kotlinx.serialization.json.JsonNull }
                        ?.content
                    val tmdbId = s("tmdb_id")
                    val base = VodInfo(
                        title = s("name") ?: s("o_name") ?: channel.title,
                        plot = s("plot") ?: s("description"),
                        releaseDate = s("releasedate") ?: s("release_date") ?: s("year"),
                        genre = s("genre"),
                        duration = s("duration"),
                        rating = s("rating") ?: s("rating_5based"),
                        director = s("director"),
                        cast = s("cast") ?: s("actors"),
                        cover = s("cover_big") ?: s("movie_image") ?: channel.cover,
                        backdrop = backdrop,
                        tmdbId = tmdbId,
                    )
                    // Enrich with TMDB people photos when we have a tmdb_id
                    // and a build-time API key. Failures fall back to the
                    // raw name strings already in `base`.
                    val tmdbResult = tmdbId
                        ?.takeIf { BuildConfig.TMDB_API_KEY.isNotBlank() }
                        ?.let { id ->
                            if (playlist.isVod) {
                                TmdbCredits.forMovie(id, BuildConfig.TMDB_API_KEY)
                            } else {
                                TmdbCredits.forSeries(id, BuildConfig.TMDB_API_KEY)
                            }
                        }
                    base.copy(
                        crewPeople = tmdbResult?.first.orEmpty(),
                        castPeople = tmdbResult?.second.orEmpty()
                    )
                }
            }.getOrNull()
        }

    val isProgrammeSupported: Flow<Boolean> = playlist.map {
        it ?: return@map false
        if (it.isSeries || it.isVod) return@map false
        when (it.source) {
            DataSource.Xtream -> true
            DataSource.M3U -> it.epgUrls.isNotEmpty()
            else -> false
        }
    }

    val tracks: Flow<Map<@C.TrackType Int, List<PlayerTrack>>> = playerManager.tracks

    val currentTracks: Flow<Map<@C.TrackType Int, PlayerTrack?>> = playerManager.currentTracks

    fun chooseTrack(track: PlayerTrack) {
        playerManager.chooseTrack(
            group = track.group,
            index = track.index
        )
    }

    fun clearTrack(type: @C.TrackType Int) {
        playerManager.clearTrack(type)
    }

    // channel playing state
    val playerState: StateFlow<PlayerState> = combine(
        playerManager.player,
        playerManager.playbackState,
        playerManager.size,
        playerManager.playbackException,
        playerManager.isPlaying
    ) { player, playState, videoSize, playbackException, isPlaying ->
        PlayerState(
            playState = playState,
            videoSize = videoSize,
            playerError = playbackException,
            player = player,
            isPlaying = isPlaying
        )
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlayerState()
        )

    private val _isDevicesVisible: MutableStateFlow<Boolean> = MutableStateFlow(false)

    // show searching devices dialog or not
    val isDevicesVisible = _isDevicesVisible.asStateFlow()

    private val dlnaController = DlnaController()

    // searched screencast devices
    val devices: StateFlow<List<Device>> = dlnaController.devices
    // searching or not
    val searching: StateFlow<Boolean> = dlnaController.searching
    // UDN of the active cast target — UI uses it to highlight + disconnect
    val connectedDeviceUdn: StateFlow<String?> = dlnaController.connectedDeviceUdn

    private var dlnaSearchJob: Job? = null

    fun openDlnaDevices() {
        // Start search immediately. The previous 800 ms delay made the bottom
        // sheet flash "No se encontraron dispositivos" for nearly a second
        // before the search even started, which made users think the scan
        // wasn't running.
        dlnaSearchJob?.cancel()
        _isDevicesVisible.value = true
        dlnaSearchJob = viewModelScope.launch {
            dlnaController.startSearch()
        }
    }

    fun closeDlnaDevices() {
        runCatching {
            dlnaSearchJob?.cancel()
            dlnaSearchJob = null
            _isDevicesVisible.value = false
            dlnaController.stopSearch()
        }
    }

    fun connectDlnaDevice(device: Device) {
        // Tap-to-toggle: if the same device is already active, the second tap
        // disconnects it instead of re-sending the same stream. Matches the
        // native Android Cast UX.
        if (dlnaController.connectedDeviceUdn.value == device.udn) {
            dlnaController.stop(device)
            return
        }
        val channel = channel.value ?: return
        dlnaController.play(device, channel)
    }

    fun disconnectDlnaDevice(device: Device) {
        dlnaController.stop(device)
    }

    fun onFavorite() {
        viewModelScope.launch {
            val id = channel.value?.id ?: return@launch
            channelRepository.favouriteOrUnfavourite(id)
        }
    }

    fun onVolume(target: Float) {
        _volume.update { target }
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        audioManager.setStreamVolume(
            AudioManager.STREAM_MUSIC,
            (target * maxVolume).roundToInt(),
            AudioManager.FLAG_VIBRATE
        )

//        controlPoint?.setVolume((target * 100).roundToInt(), null)
    }

    fun getPreviousChannel() {
        viewModelScope.launch {
            val previousChannelId = adjacentChannels.value?.prevId
            if (adjacentChannels.value != null && previousChannelId != null) {
                playerManager.play(MediaCommand.Common(previousChannelId))
            }
        }
    }

    fun getNextChannel() {
        viewModelScope.launch {
            val nextChannelId = adjacentChannels.value?.nextId
            if (adjacentChannels.value != null && nextChannelId != null) {
                playerManager.play(MediaCommand.Common(nextChannelId))
            }
        }
    }

    fun destroy() {
        runCatching {
            dlnaSearchJob?.cancel()
            dlnaSearchJob = null
            dlnaController.stopSearch()

            playerManager.release()
        }
    }

    fun pauseOrContinue(isContinued: Boolean) {
        playerManager.pauseOrContinue(isContinued)
    }

    val programmeReminderIds: StateFlow<List<Int>> = workManager.getWorkInfosFlow(
        WorkQuery.fromStates(
            WorkInfo.State.ENQUEUED
        )
    )
        .map { infos: List<WorkInfo> ->
            infos
                .filter { ProgrammeReminder.TAG in it.tags }
                .mapNotNull { info -> ProgrammeReminder.readProgrammeId(info.tags) }
        }
        .stateIn(
            scope = viewModelScope,
            initialValue = emptyList(),
            started = SharingStarted.Lazily
        )

    fun onRemindProgramme(programme: Programme) {
        ProgrammeReminder(
            workManager = workManager,
            programmeId = programme.id,
            programmeStart = programme.start
        )
    }

    @SuppressLint("RestrictedApi")
    fun onCancelRemindProgramme(programme: Programme) {
        viewModelScope.launch(Dispatchers.IO) {
            val infos = workManager
                .getWorkInfos(WorkQuery.fromStates(WorkInfo.State.ENQUEUED))
                .get()
                .filter { ProgrammeReminder.TAG in it.tags }
                .filter { info -> ProgrammeReminder.readProgrammeId(info.tags) == programme.id }
            infos.forEach {
                workManager.cancelWorkById(it.id)
            }
        }
    }

    // the channels which is in the same category with the current channel
    // or the episodes which is in the same series.
    val pagingChannels: Flow<PagingData<Channel>> = playlist.flatMapLatest { playlist ->
        playlist ?: return@flatMapLatest flowOf(PagingData.empty())
        Pager(PagingConfig(10)) {
            channelRepository.pagingAllByPlaylistUrl(
                playlist.url,
                channel.value?.category.orEmpty(),
                "",
                Sort.UNSPECIFIED
            )
        }
            .flow
    }
        .cachedIn(viewModelScope)

    val programmes: Flow<PagingData<Programme>> = channel.flatMapLatest { channel ->
        channel ?: return@flatMapLatest flowOf(PagingData.empty())
        val relationId = channel.relationId ?: return@flatMapLatest flowOf(PagingData.empty())
        val playlist = channel.playlistUrl.let { playlistRepository.get(it) }
        playlist ?: return@flatMapLatest flowOf(PagingData.empty())
        programmeRepository.pagingProgrammes(
            playlistUrl = playlist.url,
            relationId = relationId
        )
            .cachedIn(viewModelScope)
    }

    private val defaultProgrammeRange: ProgrammeRange
        get() = with(Clock.System.now()) {
            ProgrammeRange(
                this.minus(2.hours).toEpochMilliseconds(),
                this.plus(6.hours).toEpochMilliseconds()
            )
        }

    val programmeRange: StateFlow<ProgrammeRange> = channel.flatMapLatest { channel ->
        channel ?: return@flatMapLatest flowOf(defaultProgrammeRange)
        val relationId = channel.relationId ?: return@flatMapLatest flowOf(defaultProgrammeRange)
        programmeRepository
            .observeProgrammeRange(channel.playlistUrl, relationId)
            .map {
                it
                    .spread(ProgrammeRange.Spread.Increase(5.minutes, 1.hours + 5.minutes))
                    .spread(ProgrammeRange.Spread.Absolute(8.hours))
            }
    }
        .stateIn(
            scope = viewModelScope,
            initialValue = defaultProgrammeRange,
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    fun onSpeedUpdated(race: Float) {
        playerManager.updateSpeed(race)
    }

    fun recordVideo(uri: Uri) {
        viewModelScope.launch {
            playerManager.recordVideo(uri)
        }
    }
}