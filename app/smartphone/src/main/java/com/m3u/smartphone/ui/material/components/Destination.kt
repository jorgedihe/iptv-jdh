package com.m3u.smartphone.ui.material.components

import android.os.Parcelable
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FormatListBulleted
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.m3u.i18n.R.string
import kotlinx.parcelize.Parcelize

@Immutable
enum class Destination(
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    @param:StringRes @field:StringRes val iconTextId: Int
) {
    Iptv(
        selectedIcon = Icons.Rounded.Tv,
        unselectedIcon = Icons.Outlined.Tv,
        iconTextId = string.ui_destination_iptv
    ),
    Search(
        selectedIcon = Icons.Rounded.Search,
        unselectedIcon = Icons.Outlined.Search,
        iconTextId = string.ui_destination_search
    ),
    Epg(
        selectedIcon = Icons.Rounded.CalendarMonth,
        unselectedIcon = Icons.Outlined.CalendarMonth,
        iconTextId = string.ui_destination_epg
    ),
    Library(
        selectedIcon = Icons.Rounded.Star,
        unselectedIcon = Icons.Outlined.StarBorder,
        iconTextId = string.ui_destination_library
    ),
    Playlists(
        selectedIcon = Icons.Rounded.FormatListBulleted,
        unselectedIcon = Icons.Outlined.FormatListBulleted,
        iconTextId = string.ui_destination_playlists
    ),
    Foryou(
        selectedIcon = Icons.Rounded.Home,
        unselectedIcon = Icons.Outlined.Home,
        iconTextId = string.ui_destination_foryou
    ),
    Favorite(
        selectedIcon = Icons.Rounded.Collections,
        unselectedIcon = Icons.Outlined.Collections,
        iconTextId = string.ui_destination_favourite
    ),
    Extension(
        selectedIcon = Icons.Rounded.Extension,
        unselectedIcon = Icons.Outlined.Extension,
        iconTextId = string.ui_destination_extension
    ),
    Setting(
        selectedIcon = Icons.Rounded.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        iconTextId = string.ui_destination_setting
    );

    companion object {
        /** Destinations to show in the bottom navigation, in order. */
        val Tabs: List<Destination> = listOf(Iptv, Library, Playlists, Setting)

        fun of(route: String?): Destination? = try {
            route?.let { valueOf(it) }
        } catch (ignored: Exception) {
            null
        }
    }
}

@Immutable
@Parcelize
sealed interface SettingDestination : Parcelable {
    @Immutable
    @Parcelize
    data object Default : SettingDestination

    @Immutable
    @Parcelize
    data object Playlists : SettingDestination

    @Immutable
    @Parcelize
    data object Appearance : SettingDestination

    @Immutable
    @Parcelize
    data object Optional : SettingDestination

    @Immutable
    @Parcelize
    data object CodecPack : SettingDestination
}
