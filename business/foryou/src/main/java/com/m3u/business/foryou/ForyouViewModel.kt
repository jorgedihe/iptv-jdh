package com.m3u.business.foryou

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map as pagingMap
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.Settings
import com.m3u.core.foundation.architecture.preferences.flowOf
import com.m3u.core.foundation.architecture.preferences.set
import com.m3u.core.foundation.wrapper.Sort
import com.m3u.core.foundation.wrapper.Resource
import com.m3u.core.foundation.wrapper.mapResource
import com.m3u.core.foundation.wrapper.resource
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.isSeries
import com.m3u.data.database.model.isVod
import com.m3u.data.parser.xtream.XtreamEpisodeInfo
import com.m3u.data.parser.xtream.XtreamInput
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.programme.ProgrammeRepository
import com.m3u.data.service.PlayerManager
import com.m3u.data.worker.SubscriptionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@HiltViewModel
class ForyouViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val channelRepository: ChannelRepository,
    private val programmeRepository: ProgrammeRepository,
    private val playerManager: PlayerManager,
    private val settings: Settings,
    private val workManager: WorkManager,
) : ViewModel() {
    val playlists: StateFlow<Map<Playlist, Int>> = playlistRepository
        .observeAllCounts()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = emptyMap()
        )

    // ---- Provider grouping (Xtream playlists sharing basicUrl+username) ----

    val providers: StateFlow<List<Provider>> = playlistRepository
        .observeAll()
        .map { all -> groupProviders(all) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    private val storedActiveProviderKey: StateFlow<String> = settings
        .flowOf(PreferencesKeys.ACTIVE_PROVIDER_KEY)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = ""
        )

    /** The currently selected Provider, or the first available one as fallback. */
    val activeProvider: StateFlow<Provider?> = combine(
        providers,
        storedActiveProviderKey
    ) { list, key ->
        if (list.isEmpty()) null
        else list.firstOrNull { it.key == key } ?: list.first()
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = null
        )

    fun setActiveProvider(key: String) {
        viewModelScope.launch {
            settings[PreferencesKeys.ACTIVE_PROVIDER_KEY] = key
        }
    }

    init {
        // For the active provider, kick off channel sync ONLY for Xtream
        // playlists that have zero channels stored — that's a recovery path
        // from an incomplete initial subscribe and it can't run on demand
        // because the user would just see an empty list.
        //
        // NOTE: the previous version of this block also auto-fired an EPG
        // XMLTV download on every provider change (via SubscriptionWorker.epg).
        // That silently hit the Xtream endpoint in the background even while
        // the user was watching a channel; if the provider hiccuped and
        // returned an HTML error page, the app popped a "el proveedor no
        // responde" notification on top of playback. Removed. EPG now only
        // refreshes when the user opens the EPG screen or pulls to refresh
        // the list.
        viewModelScope.launch {
            var lastProviderKey: String? = null
            activeProvider.collect { provider ->
                if (provider == null) return@collect
                if (provider.key == lastProviderKey) return@collect
                lastProviderKey = provider.key

                listOfNotNull(provider.live, provider.vod, provider.series).forEach { pl ->
                    val channelCount = channelRepository.getByPlaylistUrl(pl.url).size
                    if (channelCount == 0) {
                        runCatching { playlistRepository.refresh(pl.url) }
                    }
                }
            }
        }
    }

    private val currentProgrammesByPlaylist: (String) -> Flow<Map<String, Programme>> = { url ->
        channelRepository.observeRelationIdsByPlaylistUrl(url)
            .map { it.toSet() }
            .distinctUntilChanged()
            .flatMapLatest { flow { emit(programmeRepository.getProgrammesCurrently(url)) } }
    }

    val liveChannels: Flow<PagingData<ChannelWithProgrammeLite>> = activeProvider
        .flatMapLatest { provider -> pagedChannelsWithProgramme(provider?.live?.url) }

    val vodChannels: Flow<PagingData<ChannelWithProgrammeLite>> = activeProvider
        .flatMapLatest { provider -> pagedChannelsWithProgramme(provider?.vod?.url) }

    val seriesChannels: Flow<PagingData<ChannelWithProgrammeLite>> = activeProvider
        .flatMapLatest { provider -> pagedChannelsWithProgramme(provider?.series?.url) }

    val liveSections: StateFlow<List<CategorySection>> = activeProvider
        .flatMapLatest { provider -> sectionsForUrl(provider?.live?.url) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val vodSections: StateFlow<List<CategorySection>> = activeProvider
        .flatMapLatest { provider -> sectionsForUrl(provider?.vod?.url) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    val seriesSections: StateFlow<List<CategorySection>> = activeProvider
        .flatMapLatest { provider -> sectionsForUrl(provider?.series?.url) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), emptyList())

    private fun sectionsForUrl(url: String?): Flow<List<CategorySection>> {
        if (url.isNullOrEmpty()) return flowOf(emptyList())
        return channelRepository.observeAllByPlaylistUrl(url)
            .map { all ->
                val visible = all.filter { !it.hidden }
                visible
                    .groupBy { it.category.trim().ifBlank { "Otros" } }
                    .map { (name, list) ->
                        CategorySection(
                            name = name,
                            totalCount = list.size,
                            previewChannels = list.take(12),
                            playlistUrl = url
                        )
                    }
                    // Order categories by language affinity: device locale first,
                    // then other Latin-script tags, then everything else. This puts
                    // "ES|" channels above "EN|" for a Spanish-language user.
                    .sortedWith(categoryLocaleComparator())
            }
            .flowOn(Dispatchers.Default)
    }

    private fun categoryLocaleComparator(): Comparator<CategorySection> {
        val localeTag = java.util.Locale.getDefault().language.uppercase()
        // Priority: device locale (e.g. ES) > common LATAM (LA, MX) > everything else.
        // Lower number sorts first.
        fun priority(name: String): Int {
            val upper = name.uppercase()
            return when {
                upper.startsWith("$localeTag|") || upper.startsWith("$localeTag ") -> 0
                localeTag == "ES" && (upper.startsWith("LA|") || upper.startsWith("MX|") || upper.startsWith("AR|")) -> 1
                upper.matches(Regex("^[A-Z]{2}[| ].*")) -> 2  // Some other country tag
                else -> 3
            }
        }
        return compareBy({ priority(it.name) }, { it.name })
    }

    private fun pagedChannelsWithProgramme(
        playlistUrl: String?
    ): Flow<PagingData<ChannelWithProgrammeLite>> {
        if (playlistUrl.isNullOrEmpty()) return flowOf(PagingData.empty())
        val pager = Pager(PagingConfig(pageSize = 20)) {
            channelRepository.pagingAllByPlaylistUrl(
                playlistUrl,
                category = "",
                query = "",
                sort = Sort.MIXED
            )
        }
            .flow
            .cachedIn(viewModelScope)
        val programmes = currentProgrammesByPlaylist(playlistUrl)
        return combine(pager, programmes) { data, programmesMap ->
            data.pagingMap { channel ->
                ChannelWithProgrammeLite(
                    channel = channel,
                    programme = channel.relationId?.let(programmesMap::get)
                )
            }
        }
    }

    private fun groupProviders(all: List<Playlist>): List<Provider> {
        // EPG playlists must never reach the Foryou provider selector. They
        // are a guide (XMLTV), not a channel source — surfacing them here
        // would let users "pick" an EPG to watch, which produces nothing.
        val nonEpg = all.filter { it.source != com.m3u.data.database.model.DataSource.EPG }

        // Use the DB-stored source instead of trying to decode the URL —
        // XtreamInput.decodeFromPlaylistUrl never rejects a well-formed http
        // URL, so plain M3U links like the iptv-org playlists were getting
        // mis-classified as Xtream and then collapsed under the same
        // "host|" key, losing every entry after the first.
        val xtreamPlaylists = nonEpg.filter {
            it.source == com.m3u.data.database.model.DataSource.Xtream &&
                    runCatching { XtreamInput.decodeFromPlaylistUrl(it.url) }
                        .getOrNull()?.username?.isNotBlank() == true
        }
        val plainPlaylists = nonEpg.filter {
            it.source != com.m3u.data.database.model.DataSource.Xtream
        }

        // Xtream providers: group the live/vod/series sub-playlists under a
        // single provider entry keyed by basicUrl + username.
        val xtreamGroups = xtreamPlaylists.groupBy { playlist ->
            val input = XtreamInput.decodeFromPlaylistUrl(playlist.url)
            "${input.basicUrl}|${input.username}"
        }
        val xtreamProviders = xtreamGroups.map { (key, playlists) ->
            val displayName = playlists
                .firstOrNull { !it.isVod && !it.isSeries }
                ?.title
                ?: playlists.first().title
                    .replace(Regex("\\s*(Live|VOD|Series|XTream).*", RegexOption.IGNORE_CASE), "")
                    .ifBlank { playlists.first().title }
            Provider(
                key = key,
                displayName = displayName.trim().ifBlank { key },
                live = playlists.firstOrNull { !it.isVod && !it.isSeries },
                vod = playlists.firstOrNull { it.isVod },
                series = playlists.firstOrNull { it.isSeries }
            )
        }

        // Plain M3U providers: every M3U list becomes its own provider entry
        // so users can switch between them in the IPTV tab dropdown. M3U has
        // no VOD / series concept, only Live TV.
        val m3uProviders = plainPlaylists.map { playlist ->
            Provider(
                key = playlist.url,
                displayName = playlist.title.trim().ifBlank { playlist.url },
                live = playlist,
                vod = null,
                series = null,
            )
        }

        return (xtreamProviders + m3uProviders).sortedBy { it.displayName.lowercase() }
    }

    val subscribingPlaylistUrls: StateFlow<List<String>> =
        workManager.getWorkInfosFlow(
            WorkQuery.fromStates(
                WorkInfo.State.RUNNING,
                WorkInfo.State.ENQUEUED,
            )
        )
            .combine(playlistRepository.observePlaylistUrls()) { infos, playlistUrls ->
                infos
                    .filter { info -> SubscriptionWorker.TAG in info.tags }
                    .mapNotNull { info -> info.tags.find { it in playlistUrls } }
            }
            .stateIn(
                scope = viewModelScope,
                initialValue = emptyList(),
                started = SharingStarted.WhileSubscribed(5_000L)
            )

    val refreshingEpgUrls: Flow<List<String>> = programmeRepository.refreshingEpgUrls

    private val unseensDuration = settings.flowOf(PreferencesKeys.UNSEENS_MILLISECONDS)
        .map { it.toDuration(DurationUnit.MILLISECONDS) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Lazily,
            initialValue = Duration.INFINITE
        )

    val specs = combine(
        unseensDuration.flatMapLatest { channelRepository.observeAllUnseenFavorites(it) },
        channelRepository.observeContinueWatching(limit = 12),
    ) { unseen, cw ->
        // Continue-watching cards first (newest progress on the left), then
        // unseen favourites. We map the stored playback_position straight into
        // CwSpec so the carousel item can render a progress bar.
        buildList<Recommend.Spec> {
            cw.forEach { ch -> add(Recommend.CwSpec(ch, ch.playbackPosition)) }
            unseen.take(8).forEach { ch -> add(Recommend.UnseenSpec(ch)) }
        }
    }
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(1_000L),
            initialValue = emptyList()
        )

    /**
     * VOD / series the user has left in progress. Drives the "Seguir viendo"
     * carousel at the top of the For-You screen. Limited to 12 items.
     */
    val continueWatching: StateFlow<List<Channel>> = channelRepository
        .observeContinueWatching(limit = 12)
        .flowOn(Dispatchers.IO)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    /** Allow swipe-to-dismiss / long-press to remove a card from the carousel. */
    fun dismissContinueWatching(channelId: Int) {
        viewModelScope.launch {
            channelRepository.clearPlaybackProgress(channelId)
        }
    }

    /** Toggle favourite state for a channel. Used by VodDetailSheet's heart
     *  button when the sheet is opened from the Foryou tab. */
    fun favourite(channelId: Int) {
        viewModelScope.launch {
            channelRepository.favouriteOrUnfavourite(channelId)
        }
    }

    fun onUnsubscribePlaylist(url: String) {
        viewModelScope.launch {
            playlistRepository.unsubscribe(url)
        }
    }

    val series = MutableStateFlow<Channel?>(null)
    val seriesReplay = MutableStateFlow(0)
    val episodes: StateFlow<Resource<List<XtreamEpisodeInfo>>> = series
        .combine(seriesReplay) { series, _ -> series }
        .flatMapLatest { series ->
            if (series == null) flow { }
            else resource { playlistRepository.readEpisodesOrThrow(series) }
                .mapResource { it }
        }
        .stateIn(
            scope = viewModelScope,
            initialValue = Resource.Loading,
            // don't lose
            started = SharingStarted.Lazily
        )

    val query = MutableStateFlow<String>("")

    suspend fun getPlaylist(playlistUrl: String): Playlist? =
        playlistRepository.get(playlistUrl)
}
