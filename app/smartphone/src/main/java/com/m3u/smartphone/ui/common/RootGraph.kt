package com.m3u.smartphone.ui.common

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.m3u.data.database.model.Playlist
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.business.epg.EpgRoute
import com.m3u.smartphone.ui.business.extension.ExtensionRoute
import com.m3u.smartphone.ui.material.ktx.Edge
import com.m3u.smartphone.ui.material.ktx.blurEdge
import com.m3u.smartphone.ui.business.favourite.FavoriteRoute
import com.m3u.smartphone.ui.business.foryou.ForyouRoute
import com.m3u.smartphone.ui.business.setting.SettingRoute
import com.m3u.smartphone.ui.material.components.Destination

fun NavGraphBuilder.rootGraph(
    contentPadding: PaddingValues,
    navigateToPlaylist: (Playlist) -> Unit,
    navigateToPlaylistCategory: (Playlist, String) -> Unit,
    navigateToChannel: () -> Unit,
    navigateToSettingPlaylistManagement: () -> Unit,
    navigateToPlaylistConfiguration: (Playlist) -> Unit,
) {
    composable(
        route = Destination.Iptv.name,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() }
    ) {
        ForyouRoute(
            navigateToPlaylist = navigateToPlaylist,
            navigateToPlaylistCategory = navigateToPlaylistCategory,
            navigateToChannel = navigateToChannel,
            navigateToSettingPlaylistManagement = navigateToSettingPlaylistManagement,
            navigateToPlaylistConfiguration = navigateToPlaylistConfiguration,
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .blurEdge(
                    edge = Edge.Bottom,
                    color = MaterialTheme.colorScheme.background
                )
        )
    }
    composable(
        route = Destination.Search.name,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() }
    ) {
        ComingSoonRoute(
            modifier = Modifier
                .fillMaxSize()
                .blurEdge(
                    edge = Edge.Bottom,
                    color = MaterialTheme.colorScheme.background
                )
        )
    }
    composable(
        route = Destination.Epg.name,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() }
    ) {
        EpgRoute(
            contentPadding = contentPadding,
            onChannelClick = { navigateToChannel() },
            modifier = Modifier
                .fillMaxSize()
                .blurEdge(
                    edge = Edge.Bottom,
                    color = MaterialTheme.colorScheme.background
                )
        )
    }
    composable(
        route = Destination.Library.name,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() }
    ) {
        FavoriteRoute(
            navigateToChannel = navigateToChannel,
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .blurEdge(
                    edge = Edge.Bottom,
                    color = MaterialTheme.colorScheme.background
                )
        )
    }
    composable(
        route = Destination.Playlists.name,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() }
    ) {
        com.m3u.smartphone.ui.business.playlists.PlaylistsRoute(
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .blurEdge(
                    edge = Edge.Bottom,
                    color = MaterialTheme.colorScheme.background
                )
        )
    }
    // Legacy routes kept for backward-compatible internal navigation.
    composable(
        route = Destination.Foryou.name,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() }
    ) {
        ForyouRoute(
            navigateToPlaylist = navigateToPlaylist,
            navigateToPlaylistCategory = navigateToPlaylistCategory,
            navigateToChannel = navigateToChannel,
            navigateToSettingPlaylistManagement = navigateToSettingPlaylistManagement,
            navigateToPlaylistConfiguration = navigateToPlaylistConfiguration,
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .blurEdge(
                    edge = Edge.Bottom,
                    color = MaterialTheme.colorScheme.background
                )
        )
    }
    composable(
        route = Destination.Favorite.name,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() }
    ) {
        FavoriteRoute(
            navigateToChannel = navigateToChannel,
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .blurEdge(
                    edge = Edge.Bottom,
                    color = MaterialTheme.colorScheme.background
                )
        )
    }

    composable(
        route = Destination.Extension.name,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() }
    ) {
        ExtensionRoute(
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .blurEdge(
                    edge = Edge.Bottom,
                    color = MaterialTheme.colorScheme.background
                )
        )
    }

    composable(
        route = Destination.Setting.name,
        enterTransition = { fadeIn() },
        exitTransition = { fadeOut() }
    ) {
        SettingRoute(
            contentPadding = contentPadding,
            modifier = Modifier
                .fillMaxSize()
                .blurEdge(
                    edge = Edge.Bottom,
                    color = MaterialTheme.colorScheme.background
                )
        )
    }
}

@androidx.compose.runtime.Composable
private fun ComingSoonRoute(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(string.ui_placeholder_coming_soon),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
