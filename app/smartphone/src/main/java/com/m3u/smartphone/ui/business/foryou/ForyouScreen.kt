package com.m3u.smartphone.ui.business.foryou

import android.content.res.Configuration.ORIENTATION_PORTRAIT
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.m3u.business.foryou.CategorySection
import com.m3u.business.foryou.ChannelWithProgrammeLite
import com.m3u.business.foryou.ForyouViewModel
import com.m3u.business.foryou.Provider
import com.m3u.business.foryou.Recommend
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.mutablePreferenceOf
import com.m3u.core.foundation.architecture.preferences.preferenceOf
import com.m3u.core.foundation.ui.composableOf
import com.m3u.core.foundation.ui.thenIf
import com.m3u.core.foundation.util.basic.title
import com.m3u.core.foundation.wrapper.Resource
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.PlaylistWithCount
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.isSeries
import com.m3u.data.database.model.isVod
import com.m3u.data.service.MediaCommand
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.business.foryou.components.HeadlineBackground
import com.m3u.smartphone.ui.business.foryou.components.HeroContinueWatching
import com.m3u.smartphone.ui.business.foryou.components.OnboardingWizard
import com.m3u.smartphone.ui.business.foryou.components.PlaylistGallery
import com.m3u.smartphone.ui.business.foryou.components.recommend.RecommendGallery
import com.m3u.smartphone.ui.business.playlist.components.ChannelItem
import com.m3u.smartphone.ui.common.helper.Action
import com.m3u.smartphone.ui.common.helper.LocalHelper
import com.m3u.smartphone.ui.common.helper.Metadata
import com.m3u.smartphone.ui.material.components.EpisodesBottomSheet
import com.m3u.smartphone.ui.material.components.MediaSheet
import com.m3u.smartphone.ui.material.components.MediaSheetValue
import com.m3u.smartphone.ui.material.ktx.interceptVolumeEvent
import com.m3u.smartphone.ui.material.model.LocalSpacing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ForyouRoute(
    navigateToPlaylist: (Playlist) -> Unit,
    navigateToPlaylistCategory: (Playlist, String) -> Unit = { p, _ -> navigateToPlaylist(p) },
    navigateToChannel: () -> Unit,
    navigateToPlaylistConfiguration: (Playlist) -> Unit,
    navigateToSettingPlaylistManagement: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    viewModel: ForyouViewModel = hiltViewModel()
) {
    val helper = LocalHelper.current
    val coroutineScope = rememberCoroutineScope()

    var rowCount by mutablePreferenceOf(PreferencesKeys.ROW_COUNT)
    val godMode by preferenceOf(PreferencesKeys.GOD_MODE)

    val title = stringResource(string.ui_title_foryou)

    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val specs by viewModel.specs.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()

    val series: Channel? by viewModel.series.collectAsStateWithLifecycle()
    // Pre-play detail sheet state — opened for VOD and series; the actual
    // player only starts when the user taps REPRODUCIR / an episode card.
    var vodDetailChannel by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<Channel?>(null) }
    var vodDetailPlaylist by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.m3u.data.database.model.Playlist?>(null) }
    val subscribingPlaylistUrls by viewModel.subscribingPlaylistUrls.collectAsStateWithLifecycle()
    val refreshingEpgUrls by viewModel.refreshingEpgUrls.collectAsStateWithLifecycle(emptyList())

    val activeProvider by viewModel.activeProvider.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val liveSections by viewModel.liveSections.collectAsStateWithLifecycle()
    val vodSections by viewModel.vodSections.collectAsStateWithLifecycle()
    val seriesSections by viewModel.seriesSections.collectAsStateWithLifecycle()

    LifecycleResumeEffect(title) {
        Metadata.title = AnnotatedString(title.title())
        Metadata.color = Color.Unspecified
        Metadata.contentColor = Color.Unspecified
        Metadata.actions = listOf(
            Action(
                icon = Icons.Rounded.Add,
                contentDescription = "add",
                onClick = navigateToSettingPlaylistManagement
            )
        )
        onPauseOrDispose {
            Metadata.actions = emptyList()
            Metadata.headlineUrl = ""
        }
    }

    Box(modifier) {
        ForyouScreen(
            playlists = playlists,
            subscribingPlaylistUrls = subscribingPlaylistUrls,
            refreshingEpgUrls = refreshingEpgUrls,
            specs = specs,
            rowCount = rowCount,
            contentPadding = contentPadding,
            navigateToPlaylist = navigateToPlaylist,
            activeProvider = activeProvider,
            providers = providers,
            liveSections = liveSections,
            vodSections = vodSections,
            seriesSections = seriesSections,
            onProviderSelected = viewModel::setActiveProvider,
            navigateToPlaylistCategory = navigateToPlaylistCategory,
            navigateToSettingPlaylistManagement = navigateToSettingPlaylistManagement,
            onPlayChannel = { channel ->
                coroutineScope.launch {
                    val playlist = viewModel.getPlaylist(channel.playlistUrl)
                    when {
                        // VOD and series open the pre-play detail sheet first.
                        playlist?.isSeries == true -> {
                            vodDetailChannel = channel
                            vodDetailPlaylist = playlist
                        }
                        playlist?.isVod == true -> {
                            vodDetailChannel = channel
                            vodDetailPlaylist = playlist
                        }
                        else -> {
                            helper.play(MediaCommand.Common(channel.id))
                            navigateToChannel()
                        }
                    }
                }
            },
            navigateToPlaylistConfiguration = navigateToPlaylistConfiguration,
            onUnsubscribePlaylist = viewModel::onUnsubscribePlaylist,
            modifier = Modifier
                .fillMaxSize()
                .thenIf(godMode) {
                    Modifier.interceptVolumeEvent { event ->
                        rowCount = when (event) {
                            KeyEvent.KEYCODE_VOLUME_UP -> (rowCount - 1).coerceAtLeast(1)
                            KeyEvent.KEYCODE_VOLUME_DOWN -> (rowCount + 1).coerceAtMost(2)
                            else -> return@interceptVolumeEvent
                        }
                    }
                }
        )

        // The old EpisodesBottomSheet was replaced by the richer VodDetailSheet
        // (full info + episode cards). We keep `series` and `episodes` state
        // around for backward compat but no longer render this sheet.

        // Pre-play detail sheet for VOD / series. Loads TMDB-enriched info
        // and (for series) the episode list, then enters the actual player
        // only when the user taps REPRODUCIR or an episode card.
        com.m3u.smartphone.ui.business.playlist.components.VodDetailSheet(
            channel = vodDetailChannel,
            playlist = vodDetailPlaylist,
            onPlay = { ch ->
                val target = vodDetailChannel
                val pl = vodDetailPlaylist
                vodDetailChannel = null
                vodDetailPlaylist = null
                if (target != null) {
                    coroutineScope.launch {
                        helper.play(MediaCommand.Common(ch.id))
                        navigateToChannel()
                    }
                }
            },
            onPlayEpisode = { seriesCh, ep ->
                vodDetailChannel = null
                vodDetailPlaylist = null
                coroutineScope.launch {
                    val full = com.m3u.data.parser.xtream.XtreamEpisodeInfo(
                        containerExtension = ep.containerExtension,
                        episodeNum = ep.episodeNumber.toString(),
                        id = ep.id,
                        title = ep.title,
                    )
                    helper.play(MediaCommand.XtreamEpisode(seriesCh.id, full))
                    navigateToChannel()
                }
            },
            onFavourite = { id -> viewModel.favourite(id) },
            onDismissRequest = {
                vodDetailChannel = null
                vodDetailPlaylist = null
            }
        )
    }
}

@Composable
private fun ForyouScreen(
    rowCount: Int,
    playlists: Map<Playlist, Int>,
    subscribingPlaylistUrls: List<String>,
    refreshingEpgUrls: List<String>,
    specs: List<Recommend.Spec>,
    contentPadding: PaddingValues,
    navigateToPlaylist: (Playlist) -> Unit,
    onPlayChannel: (Channel) -> Unit,
    navigateToPlaylistConfiguration: (Playlist) -> Unit,
    onUnsubscribePlaylist: (playlistUrl: String) -> Unit,
    activeProvider: Provider?,
    providers: List<Provider>,
    liveSections: List<com.m3u.business.foryou.CategorySection>,
    vodSections: List<com.m3u.business.foryou.CategorySection>,
    seriesSections: List<com.m3u.business.foryou.CategorySection>,
    onProviderSelected: (String) -> Unit,
    navigateToPlaylistCategory: (Playlist, String) -> Unit,
    navigateToSettingPlaylistManagement: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var headlineSpec: Recommend.Spec? by remember { mutableStateOf(null) }
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val filteredPlaylists = remember(playlists, selectedTabIndex) {
        playlists.filter { (playlist, _) ->
            when (selectedTabIndex) {
                0 -> !playlist.isVod && !playlist.isSeries // Live TV
                1 -> playlist.isVod                         // Movies
                2 -> playlist.isSeries                      // Series
                else -> true
            }
        }
    }

    val actualRowCount = remember(rowCount, configuration.orientation) {
        when (configuration.orientation) {
            ORIENTATION_PORTRAIT -> rowCount
            else -> rowCount + 2
        }
    }
    var mediaSheetValue: MediaSheetValue.ForyouScreen by remember {
        mutableStateOf(MediaSheetValue.ForyouScreen())
    }

    LaunchedEffect(headlineSpec) {
        val spec = headlineSpec
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(400.milliseconds)
            Metadata.headlineUrl = when (spec) {
                is Recommend.UnseenSpec -> spec.channel.cover.orEmpty()
                is Recommend.DiscoverSpec -> ""
                is Recommend.NewRelease -> ""
                else -> ""
            }
        }
    }

    var viewModePref by mutablePreferenceOf(PreferencesKeys.HOME_VIEW_MODE)
    val viewMode = ViewMode.fromInt(viewModePref)

    // Build the tab strip dynamically: only show PELÍCULAS / SERIES when the
    // active provider actually has those sub-playlists (Xtream has all three;
    // a plain M3U provider has only Live, so showing the other two empty
    // would be confusing).
    //
    // Each visible entry is (filterIndex, label). filterIndex maps to:
    //   0 = Live TV   1 = Movies (VOD)   2 = Series
    // Order matches DiiXtream: Movies, Series, Live TV (Live TV stays default).
    val moviesLabel = stringResource(string.ui_tab_videos)
    val seriesLabel = stringResource(string.ui_tab_series)
    val liveLabel = stringResource(string.ui_tab_live_tv)
    val visibleTabs: List<Pair<Int, String>> = remember(activeProvider) {
        buildList {
            if (activeProvider?.vod != null) add(1 to moviesLabel)
            if (activeProvider?.series != null) add(2 to seriesLabel)
            if (activeProvider?.live != null) add(0 to liveLabel)
        }
    }

    // If the previously selected filter is no longer available (e.g. user
    // switched from an Xtream provider to a plain M3U), reset to the first
    // visible tab so the screen doesn't render an empty state silently.
    LaunchedEffect(visibleTabs) {
        if (visibleTabs.isNotEmpty() && visibleTabs.none { it.first == selectedTabIndex }) {
            selectedTabIndex = visibleTabs.first().first
        }
    }

    val displayTabIndex = visibleTabs.indexOfFirst { it.first == selectedTabIndex }
        .coerceAtLeast(0)

    Box(modifier) {
        HeadlineBackground()

        if (activeProvider == null) {
            OnboardingWizard(
                onAddProvider = navigateToSettingPlaylistManagement,
                modifier = Modifier.fillMaxSize()
            )
            return@Box
        }

        val sections = when (selectedTabIndex) {
            0 -> liveSections
            1 -> vodSections
            2 -> seriesSections
            else -> emptyList()
        }
        val isVodOrSeries = selectedTabIndex != 0
        val activePlaylist = when (selectedTabIndex) {
            0 -> activeProvider.live
            1 -> activeProvider.vod
            2 -> activeProvider.series
            else -> null
        }

        LazyColumn(
            contentPadding = contentPadding,
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "header") {
                Column {
                    ProviderHeader(
                        activeProvider = activeProvider,
                        providers = providers,
                        onProviderSelected = onProviderSelected,
                        onSettingsClick = navigateToSettingPlaylistManagement,
                        viewMode = viewMode,
                        onCycleViewMode = { viewModePref = viewMode.next().toInt() }
                    )
                    // Newest "continue watching" item becomes a full-bleed
                    // hero card at the top of the screen (Netflix-style). The
                    // remaining items (if any) keep going through the small
                    // RecommendGallery carousel below so nothing is lost.
                    val heroSpec = specs.firstOrNull { it is Recommend.CwSpec } as? Recommend.CwSpec
                    val tailSpecs = if (heroSpec != null) {
                        specs.filterNot { it === heroSpec }
                    } else specs
                    if (heroSpec != null) {
                        HeroContinueWatching(
                            channel = heroSpec.channel,
                            positionMs = heroSpec.position,
                            onPlay = { onPlayChannel(it) },
                            onInfo = { onPlayChannel(it) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (tailSpecs.isNotEmpty()) {
                        Text(
                            text = stringResource(string.ui_section_continue_watching),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
                        )
                        RecommendGallery(
                            specs = tailSpecs,
                            navigateToPlaylist = navigateToPlaylist,
                            onPlayChannel = onPlayChannel,
                            onSpecChanged = { spec -> headlineSpec = spec },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    // Hide the tab strip entirely when there is only one section
                    // available (e.g. a plain M3U provider with only Live TV).
                    if (visibleTabs.size > 1) {
                        PrimaryTabRow(
                            selectedTabIndex = displayTabIndex,
                            containerColor = Color.Transparent
                        ) {
                            visibleTabs.forEachIndexed { uiIndex, (filterIndex, label) ->
                                Tab(
                                    selected = displayTabIndex == uiIndex,
                                    onClick = { selectedTabIndex = filterIndex },
                                    text = {
                                        Text(
                                            text = label.uppercase(),
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Visible
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }
            if (sections.isEmpty()) {
                item(key = "loader") {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    )
                }
            } else {
                items(
                    items = sections,
                    key = { it.name }
                ) { section ->
                    if (viewMode == ViewMode.TEXT_ONLY) {
                        CollapsibleCategorySection(
                            section = section,
                            onPlayChannel = onPlayChannel,
                            onMoreClick = { activePlaylist?.let { navigateToPlaylistCategory(it, section.name) } }
                        )
                    } else {
                        CategorySectionRow(
                            section = section,
                            isVodOrSeries = isVodOrSeries,
                            viewMode = viewMode,
                            onPlayChannel = onPlayChannel,
                            onMoreClick = {
                                activePlaylist?.let { navigateToPlaylistCategory(it, section.name) }
                            }
                        )
                    }
                }
            }
        }
        MediaSheet(
            value = mediaSheetValue,
            onUnsubscribePlaylist = {
                onUnsubscribePlaylist(it.url)
                mediaSheetValue = MediaSheetValue.ForyouScreen()
            },
            onPlaylistConfiguration = navigateToPlaylistConfiguration,
            onDismissRequest = {
                mediaSheetValue = MediaSheetValue.ForyouScreen()
            }
        )
    }
}

private enum class ViewMode(val posterWidthDp: Int, val channelWidthDp: Int, val showImages: Boolean) {
    COMPACT(82, 110, true),
    COMFORTABLE(108, 140, true),
    LARGE(140, 180, true),
    TEXT_ONLY(0, 0, false);

    fun next(): ViewMode = when (this) {
        COMPACT -> COMFORTABLE
        COMFORTABLE -> LARGE
        LARGE -> TEXT_ONLY
        TEXT_ONLY -> COMPACT
    }

    fun toInt(): Int = when (this) {
        COMPACT -> 0
        COMFORTABLE -> 1
        LARGE -> 2
        TEXT_ONLY -> 3
    }

    companion object {
        fun fromInt(value: Int): ViewMode = when (value) {
            1 -> COMFORTABLE
            2 -> LARGE
            3 -> TEXT_ONLY
            else -> COMPACT
        }
    }
}

@Composable
private fun ProviderHeader(
    activeProvider: Provider,
    providers: List<Provider>,
    onProviderSelected: (String) -> Unit,
    onSettingsClick: () -> Unit,
    viewMode: ViewMode,
    onCycleViewMode: () -> Unit
) {
    val multiProvider = providers.size > 1
    var expanded by remember { mutableStateOf(false) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 4.dp, top = 12.dp, bottom = 8.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .then(if (multiProvider) Modifier.clickable { expanded = true } else Modifier)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = activeProvider.displayName.uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                if (multiProvider) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
            if (multiProvider) {
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    providers.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.displayName) },
                            onClick = {
                                onProviderSelected(p.key)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
        IconButton(onClick = onCycleViewMode) {
            Icon(
                imageVector = when (viewMode) {
                    ViewMode.COMPACT -> Icons.Rounded.GridView
                    ViewMode.COMFORTABLE -> Icons.Rounded.ViewModule
                    ViewMode.LARGE -> Icons.Rounded.ViewAgenda
                    ViewMode.TEXT_ONLY -> Icons.AutoMirrored.Rounded.ViewList
                },
                contentDescription = "View mode",
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun EmptyProviderState(
    onAddProvider: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(32.dp)
    ) {
        Text(
            text = stringResource(string.ui_no_provider_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = stringResource(string.ui_no_provider_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
        )
        Button(
            onClick = onAddProvider,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(Icons.Rounded.Add, contentDescription = null)
            Text(
                text = stringResource(string.ui_no_provider_action),
                modifier = Modifier.padding(start = 6.dp)
            )
        }
    }
}

@Composable
private fun CategorySectionRow(
    section: CategorySection,
    isVodOrSeries: Boolean,
    viewMode: ViewMode,
    onPlayChannel: (Channel) -> Unit,
    onMoreClick: () -> Unit
) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, bottom = 4.dp)
        ) {
            Text(
                text = section.name.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(
                onClick = onMoreClick,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
            ) {
                Text(
                    text = "${section.totalCount}  ›",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items = section.previewChannels, key = { it.id }) { ch ->
                when {
                    !viewMode.showImages -> TextOnlyChip(channel = ch, onClick = { onPlayChannel(ch) })
                    isVodOrSeries -> PosterMini(channel = ch, widthDp = viewMode.posterWidthDp, onClick = { onPlayChannel(ch) })
                    else -> ChannelMini(channel = ch, widthDp = viewMode.channelWidthDp, onClick = { onPlayChannel(ch) })
                }
            }
        }
    }
}

@Composable
private fun PosterMini(channel: Channel, widthDp: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .width(widthDp.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            if (!channel.cover.isNullOrBlank()) {
                coil.compose.SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context).data(channel.cover).crossfade(220).build(),
                    contentDescription = channel.title,
                    contentScale = ContentScale.Crop,
                    error = { FallbackIcon() },
                    loading = { FallbackIcon() },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                FallbackIcon()
            }
        }
        Text(
            text = channel.title.trim(),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp, start = 2.dp, end = 2.dp)
        )
    }
}

@Composable
private fun ChannelMini(channel: Channel, widthDp: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .width(widthDp.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!channel.cover.isNullOrBlank()) {
                coil.compose.SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context).data(channel.cover).crossfade(220).build(),
                    contentDescription = channel.title,
                    contentScale = ContentScale.Fit,
                    error = { FallbackIcon() },
                    loading = { FallbackIcon() },
                    modifier = Modifier.fillMaxSize().padding(14.dp)
                )
            } else {
                FallbackIcon()
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = channel.title.trim(),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun TextOnlyChip(channel: Channel, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Text(
            text = channel.title.trim(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CollapsibleCategorySection(
    section: CategorySection,
    onPlayChannel: (Channel) -> Unit,
    onMoreClick: () -> Unit
) {
    var expanded by remember(section.name) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = section.name.uppercase(),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = section.totalCount.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(end = 8.dp)
            )
            Icon(
                imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (expanded) {
            section.previewChannels.forEach { ch ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPlayChannel(ch) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = ch.title.trim(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            if (section.totalCount > section.previewChannels.size) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onMoreClick)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Ver todos (${section.totalCount})",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun FallbackIcon() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Tv,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
