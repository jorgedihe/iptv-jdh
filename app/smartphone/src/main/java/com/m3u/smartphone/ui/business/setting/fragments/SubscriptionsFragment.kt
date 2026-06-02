package com.m3u.smartphone.ui.business.setting.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.google.accompanist.permissions.rememberPermissionState
import com.m3u.business.setting.BackingUpAndRestoringState
import com.m3u.business.setting.SettingProperties
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.preferenceOf
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.i18n.R.string
import com.m3u.smartphone.benchmark.DebugBenchmarkSettings
import com.m3u.smartphone.ui.business.setting.components.DataSourceSelection
import com.m3u.smartphone.ui.business.setting.components.EpgPlaylistItem
import com.m3u.smartphone.ui.business.setting.components.HiddenChannelItem
import com.m3u.smartphone.ui.business.setting.components.HiddenPlaylistGroupItem
import com.m3u.smartphone.ui.business.setting.components.LocalStorageButton
import com.m3u.smartphone.ui.business.setting.components.LocalStorageSwitch
import com.m3u.smartphone.ui.business.setting.components.RemoteControlSubscribeSwitch
import com.m3u.smartphone.ui.common.helper.LocalHelper
import com.m3u.smartphone.ui.material.components.HorizontalPagerIndicator
import com.m3u.smartphone.ui.material.components.PlaceholderField
import com.m3u.smartphone.ui.material.components.SelectionsDefaults
import com.m3u.smartphone.ui.material.ktx.checkPermissionOrRationale
import com.m3u.smartphone.ui.material.ktx.textHorizontalLabel
import com.m3u.smartphone.ui.material.model.LocalSpacing

// Previously this screen lived inside a HorizontalPager with hidden pages
// ("emisiones ocultas", "categorías de listas ocultas", "EPGs"). They were
// confusing because nothing on screen suggested swiping. The hidden pages
// were dead weight in this user flow, so the screen is now a single page.
// EPGs already have their own section in the "Listas" tab. Unhiding hidden
// channels/categories is rare enough that it can live elsewhere in a future
// version if needed.
@Composable
context(_: SettingProperties)
internal fun SubscriptionsFragment(
    onSubscribe: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    MainContentImpl(
        onSubscribe = onSubscribe,
        modifier = modifier,
        contentPadding = contentPadding
    )
}

@Composable
context(properties: SettingProperties)
private fun MainContentImpl(
    onSubscribe: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues()
) {
    val spacing = LocalSpacing.current
    val helper = LocalHelper.current

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(spacing.small),
        contentPadding = PaddingValues(
            start = spacing.medium,
            end = spacing.medium,
            top = spacing.medium,
            bottom = spacing.medium
        ) + contentPadding,
        modifier = modifier
    ) {
        // EPG removed from this selector: it's not a channel list, it's an
        // add-on for an existing list. Adding EPGs lives in the Lists tab
        // (dedicated "+ Añadir guía EPG" button) instead.
        item {
            // Reset selection away from EPG if the user lands here with EPG
            // still active from a previous version.
            LaunchedEffect(Unit) {
                if (properties.selectedState.value == DataSource.EPG) {
                    properties.selectedState.value = DataSource.M3U
                }
            }
            DataSourceSelection(
                selectedState = properties.selectedState,
                supported = listOf(
                    DataSource.M3U,
                    DataSource.Xtream
                )
            )
        }

        item {
            when (properties.selectedState.value) {
                DataSource.M3U -> M3UInputContent()
                DataSource.Xtream -> XtreamInputContent()
                else -> {} // EPG / Emby / Dropbox not exposed here
            }
        }

        item {
            Spacer(Modifier.size(spacing.medium))
        }
        item {
            // Single-action screen now: one big "AÑADIR" button. The clipboard
            // shortcut and the local-storage toggle were removed — users can
            // paste manually if they want, and every list lives as a URL
            // (which is what "Mis listas" already assumes).
            @SuppressLint("InlinedApi")
            val postNotificationPermission = rememberPermissionState(
                Manifest.permission.POST_NOTIFICATIONS
            )
            Button(
                onClick = {
                    postNotificationPermission.checkPermissionOrRationale(
                        showRationale = {
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .apply {
                                    putExtra(
                                        Settings.EXTRA_APP_PACKAGE,
                                        helper.activityContext.packageName
                                    )
                                }
                            helper.activityContext.startActivity(intent)
                        },
                        block = { onSubscribe() }
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(string.feat_setting_label_subscribe).uppercase())
            }
        }

        item {
            Spacer(Modifier.imePadding())
        }
    }
}

// `+` operator for PaddingValues — simple add of all four edges, used above
// so we don't accidentally drop the screen's contentPadding while applying
// our own LazyColumn padding.
private operator fun PaddingValues.plus(other: PaddingValues): PaddingValues = PaddingValues(
    start = this.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) +
            other.calculateStartPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
    end = this.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr) +
            other.calculateEndPadding(androidx.compose.ui.unit.LayoutDirection.Ltr),
    top = this.calculateTopPadding() + other.calculateTopPadding(),
    bottom = this.calculateBottomPadding() + other.calculateBottomPadding()
)

@Composable
private fun EpgsContentImpl(
    epgs: List<Playlist>,
    onDeleteEpgPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(string.feat_setting_label_epg_playlists),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.textHorizontalLabel()
        )
        epgs.forEach { epgPlaylist ->
            EpgPlaylistItem(
                epgPlaylist = epgPlaylist,
                onDeleteEpgPlaylist = { onDeleteEpgPlaylist(epgPlaylist.url) }
            )
        }
    }
}

@Composable
private fun HiddenStreamContentImpl(
    hiddenChannels: List<Channel>,
    onUnhideChannel: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(string.feat_setting_label_hidden_channels),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.textHorizontalLabel()
        )
        hiddenChannels.forEach { channel ->
            HiddenChannelItem(
                channel = channel,
                onHidden = { onUnhideChannel(channel.id) }
            )
        }
    }
}

@Composable
private fun HiddenPlaylistCategoriesContentImpl(
    hiddenCategoriesWithPlaylists: List<Pair<Playlist, String>>,
    onUnhidePlaylistCategory: (playlistUrl: String, category: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = stringResource(string.feat_setting_label_hidden_playlist_groups),
            color = MaterialTheme.colorScheme.onPrimary,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.textHorizontalLabel()
        )
        hiddenCategoriesWithPlaylists.forEach { (playlist, category) ->
            HiddenPlaylistGroupItem(
                playlist = playlist,
                group = category,
                onHidden = { onUnhidePlaylistCategory(playlist.url, category) }
            )
        }
    }
}

@Composable
context(properties: SettingProperties)
private fun M3UInputContent(
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        properties.applyBenchmarkPlaylistPrefill(DebugBenchmarkSettings.from(context))
    }
    // localStorageState is forced to false from here so the rest of the
    // subscribe pipeline keeps treating this as a URL-based list. The
    // toggle that exposed the "save M3U file locally" path was removed
    // because it confused most users — every list is now a URL.
    LaunchedEffect(Unit) {
        if (properties.localStorageState.value) properties.localStorageState.value = false
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        PlaceholderField(
            text = properties.titleState.value,
            placeholder = stringResource(string.feat_setting_placeholder_title).uppercase(),
            onValueChange = { properties.titleState.value = Uri.decode(it) },
            imeAction = ImeAction.Next,
            modifier = Modifier.fillMaxWidth()
        )
        PlaceholderField(
            text = properties.urlState.value,
            placeholder = stringResource(string.feat_setting_placeholder_url).uppercase(),
            onValueChange = { properties.urlState.value = Uri.decode(it) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun SettingProperties.applyBenchmarkPlaylistPrefill(settings: DebugBenchmarkSettings) {
    settings.getString(DebugBenchmarkSettings.PLAYLIST_TITLE)
        ?.let { titleState.value = it }
    settings.getString(DebugBenchmarkSettings.PLAYLIST_URL)
        ?.let { urlState.value = it }
}

@Composable
context(properties: SettingProperties)
private fun EPGInputContent(
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        PlaceholderField(
            text = properties.titleState.value,
            placeholder = stringResource(string.feat_setting_placeholder_epg_title).uppercase(),
            onValueChange = { properties.titleState.value = Uri.decode(it) },
            modifier = Modifier.fillMaxWidth()
        )
        PlaceholderField(
            text = properties.epgState.value,
            placeholder = stringResource(string.feat_setting_placeholder_epg).uppercase(),
            onValueChange = { properties.epgState.value = Uri.decode(it) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
context(properties: SettingProperties)
private fun XtreamInputContent(modifier: Modifier = Modifier) {
    val spacing = LocalSpacing.current

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        PlaceholderField(
            text = properties.titleState.value,
            placeholder = stringResource(string.feat_setting_placeholder_title).uppercase(),
            onValueChange = { properties.titleState.value = Uri.decode(it) },
            modifier = Modifier.fillMaxWidth()
        )
        PlaceholderField(
            text = properties.basicUrlState.value,
            placeholder = stringResource(string.feat_setting_placeholder_basic_url).uppercase(),
            onValueChange = { properties.basicUrlState.value = it },
            modifier = Modifier.fillMaxWidth()
        )
        PlaceholderField(
            text = properties.usernameState.value,
            placeholder = stringResource(string.feat_setting_placeholder_username).uppercase(),
            onValueChange = { properties.usernameState.value = it },
            modifier = Modifier.fillMaxWidth()
        )
        PlaceholderField(
            text = properties.passwordState.value,
            placeholder = stringResource(string.feat_setting_placeholder_password).uppercase(),
            onValueChange = { properties.passwordState.value = it },
            modifier = Modifier.fillMaxWidth()
        )
        Warning(stringResource(string.feat_setting_warning_xtream_takes_much_more_time))
    }
}

@Composable
private fun Warning(
    text: String,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    CompositionLocalProvider(
        LocalContentColor provides LocalContentColor.current.copy(0.54f)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
        ) {
            Icon(imageVector = Icons.Rounded.Warning, contentDescription = null)
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
