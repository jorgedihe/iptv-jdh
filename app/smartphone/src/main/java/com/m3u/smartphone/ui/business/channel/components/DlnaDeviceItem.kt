package com.m3u.smartphone.ui.business.channel.components

import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.material3.Icon
import net.mm2d.upnp.Device

@Composable
internal fun DlnaDeviceItem(
    device: Device?,
    onClick: () -> Unit,
    isConnected: Boolean = false,
    modifier: Modifier = Modifier
) {
    ListItem(
        headlineContent = { Text(device?.friendlyName.orEmpty()) },
        supportingContent = if (isConnected) {
            { Text("Reproduciendo aquí · toca para detener") }
        } else null,
        trailingContent = {
            Icon(
                imageVector = if (isConnected) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                tint = if (isConnected) MaterialTheme.colorScheme.primary
                else androidx.compose.material3.LocalContentColor.current,
                contentDescription = null
            )
        },
        colors = if (isConnected) {
            ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            )
        } else ListItemDefaults.colors(),
        modifier = modifier.clickable(onClick = onClick)
    )
}