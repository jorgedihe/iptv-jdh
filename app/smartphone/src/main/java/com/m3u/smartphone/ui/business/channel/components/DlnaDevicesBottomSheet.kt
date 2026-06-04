package com.m3u.smartphone.ui.business.channel.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.m3u.core.foundation.util.basic.title
import com.m3u.i18n.R.string
import com.m3u.core.foundation.components.CircularProgressIndicator
import androidx.compose.material3.Icon
import com.m3u.smartphone.ui.material.components.mask.MaskState
import com.m3u.smartphone.ui.material.model.LocalSpacing
import com.m3u.smartphone.ui.material.components.UnstableBadge
import com.m3u.smartphone.ui.material.components.UnstableValue
import net.mm2d.upnp.Device

@Composable
internal fun DlnaDevicesBottomSheet(
    isDevicesVisible: Boolean,
    devices: List<Device>,
    searching: Boolean,
    connectedDeviceUdn: String?,
    maskState: MaskState,
    onDismiss: () -> Unit,
    connectDlnaDevice: (device: Device) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalSpacing.current
    val state = rememberModalBottomSheetState()

    // Discovery can take a few seconds; we don't want to flash "no devices"
    // the instant the sheet opens before the network scan even produces its
    // first reply. Wait at least 4 s after opening before declaring empty.
    var graceElapsed by remember { mutableStateOf(false) }
    LaunchedEffect(isDevicesVisible) {
        if (isDevicesVisible) {
            graceElapsed = false
            kotlinx.coroutines.delay(4_000)
            graceElapsed = true
        }
    }


    LaunchedEffect(isDevicesVisible) {
        if (isDevicesVisible) state.show()
        else state.hide()
    }
    if (isDevicesVisible) {
        ModalBottomSheet(
            sheetState = state,
            onDismissRequest = onDismiss,
            modifier = modifier,
//            windowInsets = WindowInsets(0)
        ) {
            LaunchedEffect(devices, state.isVisible) {
                if (state.isVisible) state.expand()
            }
            LaunchedEffect(Unit) {
                maskState.sleep()
            }

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(spacing.small),
                    modifier = Modifier.padding(
                        horizontal = spacing.medium,
                        vertical = spacing.small
                    )
                ) {
                    Text(
                        text = stringResource(string.feat_channel_dialog_dlna_devices),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    // Experimental badge removed — the cast flow is now
                    // production-ready (device discovery + tap-to-toggle).
                    Spacer(
                        modifier = Modifier.weight(1f)
                    )
                    AnimatedVisibility(
                        visible = searching
                    ) {
                        CircularProgressIndicator()
                    }
                }
                LazyColumn(
                    modifier = Modifier
                        .sizeIn(
                            maxHeight = 320.dp
                        )
                ) {
                    // "Abrir en aplicación externa" entry removed — it
                    // confused users who expected to see cast targets here,
                    // and ExoPlayer already handles every format the external
                    // intent could redirect to.
                    when {
                        devices.isEmpty() && !graceElapsed -> {
                            item {
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            text = "Buscando dispositivos…",
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            "Tu TV / Chromecast / receptor DLNA debe estar " +
                                                    "encendido y en la misma red Wi-Fi."
                                        )
                                    },
                                    trailingContent = { CircularProgressIndicator() }
                                )
                            }
                        }
                        devices.isEmpty() -> {
                            item {
                                ListItem(
                                    headlineContent = {
                                        Text(
                                            text = "No se encontraron dispositivos",
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    },
                                    supportingContent = {
                                        Text(
                                            "Asegúrate de que tu TV / Chromecast / receptor " +
                                                    "DLNA está encendido y conectado a la misma " +
                                                    "red Wi-Fi que el teléfono."
                                        )
                                    }
                                )
                            }
                        }
                        else -> {
                            items(devices) { device ->
                                DlnaDeviceItem(
                                    device = device,
                                    isConnected = device.udn == connectedDeviceUdn,
                                    onClick = { connectDlnaDevice(device) },
                                )
                            }
                        }
                    }
                    item {
                        Spacer(modifier = Modifier.navigationBarsPadding())
                    }
                }
            }
        }
    }
}