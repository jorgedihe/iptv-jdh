package com.m3u.smartphone.ui.business.setting.fragments.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ColorLens
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.m3u.core.foundation.util.basic.title
import com.m3u.i18n.R.string
import com.m3u.smartphone.ui.material.components.Preference
import com.m3u.smartphone.ui.material.model.LocalSpacing
import com.m3u.smartphone.ui.material.components.SettingDestination

@Composable
internal fun RegularPreferences(
    fragment: SettingDestination,
    navigateToPlaylistManagement: () -> Unit,
    navigateToThemeSelector: () -> Unit,
    navigateToOptional: () -> Unit,
    codecPackEnabled: Boolean,
    navigateToCodecPack: () -> Unit,
    navigateToAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(spacing.small)
    ) {
        // "Administrar lista" removed from Settings: list management lives in the
        // dedicated "Listas" tab now (with its own FAB to add new lists).
        Preference(
            title = stringResource(string.feat_setting_appearance).title(),
            icon = Icons.Rounded.ColorLens,
            enabled = fragment != SettingDestination.Appearance,
            onClick = navigateToThemeSelector
        )
        Preference(
            title = stringResource(string.feat_setting_optional_features).title(),
            icon = Icons.Rounded.Extension,
            enabled = fragment != SettingDestination.Optional,
            onClick = navigateToOptional
        )
        // "Decoder Components" entry removed — the codec pack is now
        // installed automatically on first launch by M3UApplication.
        // Users don't need to know it exists.
        Preference(
            title = "Acerca de IPTV JDH",
            icon = Icons.Rounded.Info,
            enabled = fragment != SettingDestination.About,
            onClick = navigateToAbout
        )
    }
}