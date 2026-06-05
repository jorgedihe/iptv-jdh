package com.m3u.smartphone.ui.business.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.parser.xtream.XtreamInput

@Composable
fun PlaylistsRoute(
    navigateToAddPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: com.m3u.business.setting.SettingViewModel = hiltViewModel()
) {
    // Use the EPG-inclusive flow here — otherwise users can't see (or delete)
    // the EPG playlists they added. The Foryou screen and the player keep
    // using the non-EPG flow.
    val playlists by viewModel.playlistsWithCountsIncludingEpg.collectAsStateWithLifecycle()
    var showAddEpgDialog by remember { mutableStateOf(false) }
    var showCuratedListsDialog by remember { mutableStateOf(false) }
    val activeKey by viewModel.activeProviderKey.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshingPlaylistUrls.collectAsStateWithLifecycle()
    // Backup / Restore are one-tap actions now — the file lives in the system
    // Downloads folder under a fixed name (IPTV-JDH-backup.txt). No file
    // picker, no path selector, works the same on every Android phone.

    data class ProviderGroup(
        val key: String,
        val displayName: String,
        val basicUrl: String,
        val username: String,
        val password: String,
        val live: Playlist?,
        val vod: Playlist?,
        val series: Playlist?,
        val totalChannels: Int
    )

    val (xtreamGroups, otherEntries, epgEntries) = remember(playlists) {
        val xtream = mutableMapOf<String, MutableList<Pair<Playlist, Int>>>()
        val other = mutableListOf<Pair<Playlist, Int>>()
        val epg = mutableListOf<Pair<Playlist, Int>>()
        playlists.entries.forEach { (playlist, count) ->
            when (playlist.source) {
                DataSource.Xtream -> runCatching {
                    val input = XtreamInput.decodeFromPlaylistUrl(playlist.url)
                    val key = "${input.basicUrl}|${input.username}"
                    xtream.getOrPut(key) { mutableListOf() }.add(playlist to count)
                }.getOrNull() ?: other.add(playlist to count)
                DataSource.EPG -> epg.add(playlist to count)
                else -> other.add(playlist to count)
            }
        }
        val groups = xtream.map { (key, items) ->
            val first = items.first().first
            val input = XtreamInput.decodeFromPlaylistUrl(first.url)
            ProviderGroup(
                key = key,
                displayName = first.title,
                basicUrl = input.basicUrl,
                username = input.username,
                password = input.password,
                live = items.firstOrNull { p -> XtreamInput.decodeFromPlaylistUrl(p.first.url).type == XtreamInput.TYPE_LIVE }?.first,
                vod = items.firstOrNull { p -> XtreamInput.decodeFromPlaylistUrl(p.first.url).type == XtreamInput.TYPE_VOD }?.first,
                series = items.firstOrNull { p -> XtreamInput.decodeFromPlaylistUrl(p.first.url).type == XtreamInput.TYPE_SERIES }?.first,
                totalChannels = items.sumOf { it.second }
            )
        }.sortedBy { it.displayName.lowercase() }
        Triple(
            groups,
            other.sortedBy { it.first.title.lowercase() },
            epg.sortedBy { it.first.title.lowercase() }
        )
    }
    val totalLists = xtreamGroups.size + otherEntries.size + epgEntries.size

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Small secondary FAB on top — opens the curated free-lists
                // picker (iptv-org). Users who already have an Xtream / M3U
                // can ignore it; users without one can fill the app with
                // public TV in two taps.
                androidx.compose.material3.SmallFloatingActionButton(
                    onClick = { showCuratedListsDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Icon(
                        Icons.Rounded.Download,
                        contentDescription = "Listas IPTV gratuitas"
                    )
                }
                ExtendedFloatingActionButton(
                    onClick = navigateToAddPlaylist,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    text = { Text("Añadir lista") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(
                        defaultElevation = 6.dp,
                        pressedElevation = 12.dp
                    )
                )
            }
        }
    ) { scaffoldPadding ->
    Column(modifier = Modifier.fillMaxSize().padding(scaffoldPadding).background(MaterialTheme.colorScheme.background)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)
        ) {
            Text(
                text = "MIS LISTAS",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            if (totalLists > 0) {
                Text(
                    text = "$totalLists",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
            // Backup is only meaningful once there is something to back up.
            if (totalLists > 0) {
                IconButton(onClick = { viewModel.backupToDownloads() }) {
                    Icon(
                        imageVector = Icons.Rounded.CloudUpload,
                        contentDescription = "Guardar copia de seguridad",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            // Restore is always available — including for fresh installs.
            IconButton(onClick = { viewModel.restoreFromDownloads() }) {
                Icon(
                    imageVector = Icons.Rounded.CloudDownload,
                    contentDescription = "Restaurar copia de seguridad",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (totalLists == 0) {
            // FAB ya invita a añadir lista — el empty state es sólo texto, sin
            // un segundo botón duplicado.
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(horizontal = 32.dp)
                ) {
                    Text(
                        text = "Aún no tienes listas",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Pulsa «+ Añadir lista» abajo para empezar",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                items(items = xtreamGroups, key = { it.key }) { group ->
                    val isActive = group.key == activeKey
                    val anyRefreshing = listOfNotNull(group.live, group.vod, group.series)
                        .any { it.url in refreshing }
                    ProviderCard(
                        displayName = group.displayName,
                        basicUrl = group.basicUrl,
                        username = group.username,
                        password = group.password,
                        liveCount = countOf(playlists, group.live),
                        vodCount = countOf(playlists, group.vod),
                        seriesCount = countOf(playlists, group.series),
                        totalChannels = group.totalChannels,
                        isActive = isActive,
                        isRefreshing = anyRefreshing,
                        onMakeActive = {
                            group.live?.let { viewModel.setActiveProviderByPlaylistUrl(it.url) }
                                ?: group.vod?.let { viewModel.setActiveProviderByPlaylistUrl(it.url) }
                                ?: group.series?.let { viewModel.setActiveProviderByPlaylistUrl(it.url) }
                        },
                        onRename = { newTitle ->
                            // Rename every sibling playlist of the provider.
                            listOfNotNull(group.live, group.vod, group.series).forEach {
                                viewModel.renamePlaylist(it.url, newTitle)
                            }
                        },
                        onReplaceXtream = { newTitle, basicUrl, username, password ->
                            // Delete every sibling then re-subscribe the whole provider.
                            listOfNotNull(group.live, group.vod, group.series).forEach {
                                viewModel.deletePlaylist(it.url)
                            }
                            viewModel.replaceXtreamPlaylist(
                                oldUrl = "",
                                title = newTitle,
                                basicUrl = basicUrl,
                                username = username,
                                password = password
                            )
                        },
                        onRefresh = {
                            // One of the playlists is enough — refreshPlaylist already
                            // recreates the missing siblings of the same provider.
                            val any = group.live ?: group.vod ?: group.series
                            any?.let { viewModel.refreshPlaylist(it.url) }
                        },
                        onDelete = {
                            listOfNotNull(group.live, group.vod, group.series).forEach {
                                viewModel.deletePlaylist(it.url)
                            }
                        }
                    )
                }
                items(items = otherEntries, key = { it.first.url }) { (playlist, count) ->
                    PlaylistCard(
                        title = playlist.title,
                        sourceLabel = playlist.source.value,
                        url = playlist.url,
                        channelCount = count,
                        isActive = false,
                        isRefreshing = playlist.url in refreshing,
                        canMakeActive = false,
                        onMakeActive = {},
                        onRename = { viewModel.renamePlaylist(playlist.url, it) },
                        onReplaceXtream = { _, _, _, _ -> },
                        onRefresh = { viewModel.refreshPlaylist(playlist.url) },
                        onDelete = { viewModel.deletePlaylist(playlist.url) },
                        // Offer each non-Xtream list the chance to attach
                        // one of the saved EPGs (or detach an existing one).
                        epgPlaylists = epgEntries.map { (epg, _) -> epg.url to epg.title },
                        currentEpgUrl = playlist.epgUrls.firstOrNull(),
                        onSetEpg = { newEpg -> viewModel.setPlaylistEpg(playlist.url, newEpg) }
                    )
                }
                // EPG section is ALWAYS shown (even when empty) so users can
                // discover the "+ Añadir guía EPG" entry. It's a separate
                // entity from channel lists.
                item(key = "epg-section-header") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 4.dp, start = 4.dp, end = 4.dp)
                    ) {
                        Text(
                            text = "GUÍAS EPG",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { showAddEpgDialog = true }) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.size(4.dp))
                            Text(
                                text = "Añadir guía EPG",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                if (epgEntries.isEmpty()) {
                    item(key = "epg-empty") {
                        Text(
                            text = "Aún no tienes guías EPG. Asóciale una guía a tu lista para ver " +
                                    "qué programa está dando cada canal en directo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp)
                        )
                    }
                } else {
                    items(items = epgEntries, key = { "epg-" + it.first.url }) { (playlist, _) ->
                        EpgCard(
                            title = playlist.title,
                            url = playlist.url,
                            isRefreshing = playlist.url in refreshing,
                            onRefresh = { viewModel.refreshPlaylist(playlist.url) },
                            onDelete = { viewModel.deletePlaylist(playlist.url) }
                        )
                    }
                }
            }
        }
    }
    } // close Scaffold content lambda

    if (showAddEpgDialog) {
        AddEpgDialog(
            onDismiss = { showAddEpgDialog = false },
            onConfirm = { title, url ->
                viewModel.addEpg(title, url)
                showAddEpgDialog = false
            }
        )
    }
    if (showCuratedListsDialog) {
        CuratedListsDialog(
            onDismiss = { showCuratedListsDialog = false },
            onConfirm = { selected ->
                selected.forEach { entry ->
                    viewModel.subscribeM3uDirect(entry.title, entry.url)
                }
                showCuratedListsDialog = false
            }
        )
    }
}

@Composable
private fun AddEpgDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, url: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir guía EPG") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Una guía EPG (XMLTV) muestra qué programa están dando " +
                            "ahora mismo los canales. Después podrás asociarla a tus " +
                            "listas desde el botón «Editar».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Nombre (p. ej. EPG España)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL del XMLTV") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(title, url) },
                enabled = title.isNotBlank() && url.isNotBlank()
            ) { Text("Añadir") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun EpgCard(
    title: String,
    url: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title.ifBlank { "EPG sin título" },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (isRefreshing) "epg · descargando…" else "epg · guía de programación",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isRefreshing) {
                    Spacer(Modifier.size(6.dp))
                    androidx.compose.material3.LinearProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }
            IconButton(onClick = onRefresh, enabled = !isRefreshing) {
                Icon(
                    Icons.Rounded.Refresh,
                    contentDescription = "Refrescar EPG",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Rounded.Delete,
                    contentDescription = "Eliminar EPG",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// LazyColumn items() helper alias to keep this file self-contained.
private fun <T> androidx.compose.foundation.lazy.LazyListScope.items(
    items: List<T>,
    key: (T) -> Any,
    itemContent: @Composable (T) -> Unit
) {
    items(items.size, key = { key(items[it]) }) { itemContent(items[it]) }
}

private fun countOf(map: Map<Playlist, Int>, playlist: Playlist?): Int =
    playlist?.let { map[it] } ?: 0

@Composable
private fun ProviderCard(
    displayName: String,
    basicUrl: String,
    username: String,
    password: String,
    liveCount: Int,
    vodCount: Int,
    seriesCount: Int,
    totalChannels: Int,
    isActive: Boolean,
    isRefreshing: Boolean,
    onMakeActive: () -> Unit,
    onRename: (String) -> Unit,
    onReplaceXtream: (title: String, basicUrl: String, username: String, password: String) -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit
) {
    var editing by remember { mutableStateOf(false) }
    Surface(
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        border = if (isActive) androidx.compose.foundation.BorderStroke(
            2.dp, MaterialTheme.colorScheme.primary
        ) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { editing = true }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isActive) {
                            Spacer(Modifier.size(6.dp))
                            Text(
                                text = "ACTIVA",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "xtream · %,d canales en total".format(totalChannels),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isRefreshing) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp).padding(8.dp)
                    )
                } else {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Actualizar",
                            tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                CategoryChip("Live TV", liveCount, isActive)
                CategoryChip("Películas", vodCount, isActive)
                CategoryChip("Series", seriesCount, isActive)
            }
            if (isRefreshing) {
                Spacer(Modifier.size(10.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.primary,
                    trackColor = (if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.primary).copy(alpha = 0.15f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                )
            }
        }
    }
    if (editing) {
        EditProviderDialog(
            currentTitle = displayName,
            basicUrl = basicUrl,
            username = username,
            password = password,
            totalChannels = totalChannels,
            isActive = isActive,
            onRename = onRename,
            onReplaceXtream = onReplaceXtream,
            onMakeActive = onMakeActive,
            onDelete = onDelete,
            onDismiss = { editing = false }
        )
    }
}

@Composable
private fun RowScope.CategoryChip(label: String, count: Int, isActive: Boolean) {
    Surface(
        color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
        else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.weight(1f)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 6.dp)
        ) {
            Text(
                text = "%,d".format(count),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EditProviderDialog(
    currentTitle: String,
    basicUrl: String,
    username: String,
    password: String,
    totalChannels: Int,
    isActive: Boolean,
    onRename: (String) -> Unit,
    onReplaceXtream: (title: String, basicUrl: String, username: String, password: String) -> Unit,
    onMakeActive: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var newTitle by remember { mutableStateOf(currentTitle) }
    var newBasicUrl by remember { mutableStateOf(basicUrl) }
    var newUsername by remember { mutableStateOf(username) }
    var newPassword by remember { mutableStateOf(password) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar proveedor") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = newTitle, onValueChange = { newTitle = it },
                    label = { Text("Nombre") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.size(10.dp))
                OutlinedTextField(
                    value = newBasicUrl, onValueChange = { newBasicUrl = it },
                    label = { Text("Servidor (http://...)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.size(10.dp))
                OutlinedTextField(
                    value = newUsername, onValueChange = { newUsername = it },
                    label = { Text("Usuario") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.size(10.dp))
                OutlinedTextField(
                    value = newPassword, onValueChange = { newPassword = it },
                    label = { Text("Contraseña") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.size(12.dp))
                Text(
                    text = "Total: %,d canales (Live + Películas + Series)".format(totalChannels),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.size(12.dp))
                if (!isActive) {
                    TextButton(onClick = { onMakeActive(); onDismiss() }) {
                        Icon(
                            imageVector = Icons.Outlined.StarBorder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("Marcar como activa")
                    }
                }
                TextButton(onClick = { confirmDelete = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("Eliminar proveedor", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t = newTitle.trim().ifEmpty { currentTitle }
                val credsChanged = newBasicUrl.trim() != basicUrl ||
                    newUsername.trim() != username ||
                    newPassword != password
                if (credsChanged && newBasicUrl.isNotBlank() && newUsername.isNotBlank()) {
                    onReplaceXtream(t, newBasicUrl.trim(), newUsername.trim(), newPassword)
                } else if (t != currentTitle) {
                    onRename(t)
                }
                onDismiss()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar proveedor") },
            text = { Text("¿Eliminar \"$currentTitle\"? Se borrarán las 3 playlists (Live + Películas + Series), canales y favoritos.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); confirmDelete = false; onDismiss() }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun PlaylistCard(
    title: String,
    sourceLabel: String,
    url: String,
    channelCount: Int,
    isActive: Boolean,
    isRefreshing: Boolean,
    canMakeActive: Boolean,
    onMakeActive: () -> Unit,
    onRename: (String) -> Unit,
    onReplaceXtream: (title: String, basicUrl: String, username: String, password: String) -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    epgPlaylists: List<Pair<String, String>> = emptyList(),
    currentEpgUrl: String? = null,
    onSetEpg: (newEpgUrl: String?) -> Unit = {}
) {
    var editing by remember { mutableStateOf(false) }
    Surface(
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(14.dp),
        border = if (isActive) androidx.compose.foundation.BorderStroke(
            2.dp, MaterialTheme.colorScheme.primary
        ) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { editing = true }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isActive) {
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = "ACTIVA",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                val subtitle = if (isRefreshing) {
                    "$sourceLabel · descargando…"
                } else {
                    "$sourceLabel · %,d canales".format(channelCount)
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isRefreshing) {
                    Spacer(Modifier.size(8.dp))
                    androidx.compose.material3.LinearProgressIndicator(
                        color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.primary,
                        trackColor = (if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.primary).copy(alpha = 0.15f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                    )
                }
            }
            if (isRefreshing) {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(36.dp)
                        .padding(8.dp)
                )
            } else {
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Actualizar",
                        tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
    if (editing) {
        EditPlaylistDialog(
            currentTitle = title,
            sourceLabel = sourceLabel,
            url = url,
            channelCount = channelCount,
            isActive = isActive,
            canMakeActive = canMakeActive,
            onRename = onRename,
            onReplaceXtream = onReplaceXtream,
            onMakeActive = onMakeActive,
            onDelete = onDelete,
            onDismiss = { editing = false },
            epgPlaylists = epgPlaylists,
            currentEpgUrl = currentEpgUrl,
            onSetEpg = onSetEpg
        )
    }
}

@Composable
private fun EditPlaylistDialog(
    currentTitle: String,
    sourceLabel: String,
    url: String,
    channelCount: Int,
    isActive: Boolean,
    canMakeActive: Boolean,
    onRename: (String) -> Unit,
    onReplaceXtream: (title: String, basicUrl: String, username: String, password: String) -> Unit,
    onMakeActive: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    // EPG association (only meaningful for non-Xtream lists; Xtream gets
    // its EPG over its own API automatically). Pass empty list + null to
    // hide the dropdown.
    epgPlaylists: List<Pair<String, String>> = emptyList(), // (url, title)
    currentEpgUrl: String? = null,
    onSetEpg: (newEpgUrl: String?) -> Unit = {}
) {
    val isXtream = sourceLabel == "xtream"
    val currentXtream = remember(url) {
        if (isXtream) runCatching {
            XtreamInput.decodeFromPlaylistUrl(url)
        }.getOrNull() else null
    }
    var newTitle by remember { mutableStateOf(currentTitle) }
    var newBasicUrl by remember { mutableStateOf(currentXtream?.basicUrl.orEmpty()) }
    var newUsername by remember { mutableStateOf(currentXtream?.username.orEmpty()) }
    var newPassword by remember { mutableStateOf(currentXtream?.password.orEmpty()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var selectedEpgUrl by remember(currentEpgUrl) { mutableStateOf(currentEpgUrl) }
    var epgMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar lista") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = newTitle, onValueChange = { newTitle = it },
                    label = { Text("Nombre") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (isXtream && currentXtream != null) {
                    Spacer(Modifier.size(10.dp))
                    OutlinedTextField(
                        value = newBasicUrl, onValueChange = { newBasicUrl = it },
                        label = { Text("Servidor (http://...)") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.size(10.dp))
                    OutlinedTextField(
                        value = newUsername, onValueChange = { newUsername = it },
                        label = { Text("Usuario") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.size(10.dp))
                    OutlinedTextField(
                        value = newPassword, onValueChange = { newPassword = it },
                        label = { Text("Contraseña") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Spacer(Modifier.size(12.dp))
                Text(
                    text = "$sourceLabel · $channelCount canales",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // EPG dropdown — only for non-Xtream lists. Xtream already
                // gets EPG via player_api.
                if (!isXtream && epgPlaylists.isNotEmpty()) {
                    Spacer(Modifier.size(16.dp))
                    Text(
                        text = "Guía EPG asociada",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.size(4.dp))
                    Box {
                        OutlinedTextField(
                            value = epgPlaylists.firstOrNull { it.first == selectedEpgUrl }?.second
                                ?: "Ninguna",
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            label = { Text("Selecciona una guía") },
                            trailingIcon = {
                                IconButton(onClick = { epgMenuExpanded = !epgMenuExpanded }) {
                                    Icon(
                                        imageVector = Icons.Rounded.ArrowDropDown,
                                        contentDescription = null
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { epgMenuExpanded = true }
                        )
                        androidx.compose.material3.DropdownMenu(
                            expanded = epgMenuExpanded,
                            onDismissRequest = { epgMenuExpanded = false }
                        ) {
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("Ninguna") },
                                onClick = {
                                    selectedEpgUrl = null
                                    epgMenuExpanded = false
                                }
                            )
                            epgPlaylists.forEach { (epgUrl, epgTitle) ->
                                androidx.compose.material3.DropdownMenuItem(
                                    text = { Text(epgTitle) },
                                    onClick = {
                                        selectedEpgUrl = epgUrl
                                        epgMenuExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.size(12.dp))
                if (canMakeActive && !isActive) {
                    TextButton(onClick = { onMakeActive(); onDismiss() }) {
                        Icon(
                            imageVector = Icons.Outlined.StarBorder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("Marcar como activa")
                    }
                }
                TextButton(onClick = { confirmDelete = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(Modifier.size(6.dp))
                    Text("Eliminar lista", color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val t = newTitle.trim().ifEmpty { currentTitle }
                val credsChanged = isXtream && currentXtream != null && (
                    newBasicUrl.trim() != currentXtream.basicUrl ||
                    newUsername.trim() != currentXtream.username ||
                    newPassword != currentXtream.password
                )
                if (credsChanged && newBasicUrl.isNotBlank() && newUsername.isNotBlank()) {
                    onReplaceXtream(t, newBasicUrl.trim(), newUsername.trim(), newPassword)
                } else if (t != currentTitle) {
                    onRename(t)
                }
                // Persist EPG association change for non-Xtream lists.
                if (!isXtream && selectedEpgUrl != currentEpgUrl) {
                    onSetEpg(selectedEpgUrl)
                }
                onDismiss()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar lista") },
            text = { Text("¿Eliminar \"$currentTitle\"? Se borrarán sus canales y favoritos asociados. Esta acción es irreversible.") },
            confirmButton = {
                TextButton(onClick = { onDelete(); confirmDelete = false; onDismiss() }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
            }
        )
    }
}

/** Hand-curated subset of the iptv-org public playlists. Picking from a
 *  short, opinionated list is much friendlier than dropping the user in
 *  front of a directory of 200+ files. */
private data class CuratedList(
    val title: String,
    val url: String,
    val description: String,
)

private val CURATED_LISTS = listOf(
    CuratedList(
        title = "España",
        url = "https://iptv-org.github.io/iptv/countries/es.m3u",
        description = "Canales públicos españoles (RTVE, autonómicas, locales)."
    ),
    CuratedList(
        title = "Latinoamérica",
        url = "https://iptv-org.github.io/iptv/regions/amer.m3u",
        description = "Canales de toda Latinoamérica."
    ),
    CuratedList(
        title = "Internacional",
        url = "https://iptv-org.github.io/iptv/index.m3u",
        description = "Lista global completa (+8000 canales de todo el mundo)."
    ),
    CuratedList(
        title = "Noticias",
        url = "https://iptv-org.github.io/iptv/categories/news.m3u",
        description = "Canales de noticias internacionales (BBC, DW, France 24, Euronews…)."
    ),
    CuratedList(
        title = "Cine",
        url = "https://iptv-org.github.io/iptv/categories/movies.m3u",
        description = "Canales 24/7 de películas."
    ),
    CuratedList(
        title = "Música",
        url = "https://iptv-org.github.io/iptv/categories/music.m3u",
        description = "Canales musicales temáticos."
    ),
)

@Composable
private fun CuratedListsDialog(
    onDismiss: () -> Unit,
    onConfirm: (List<CuratedList>) -> Unit,
) {
    val selected = remember { mutableStateOf(setOf<String>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Listas IPTV gratuitas") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Listas mantenidas por la comunidad iptv-org (dominio " +
                            "público). Marca las que quieras añadir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Column(
                    modifier = Modifier
                        .height(360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    CURATED_LISTS.forEach { entry ->
                        val checked = entry.url in selected.value
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selected.value = if (checked)
                                        selected.value - entry.url
                                    else
                                        selected.value + entry.url
                                }
                                .padding(vertical = 6.dp, horizontal = 4.dp)
                        ) {
                            androidx.compose.material3.Checkbox(
                                checked = checked,
                                onCheckedChange = null
                            )
                            Spacer(Modifier.size(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = entry.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = entry.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.value.isNotEmpty(),
                onClick = {
                    val picks = CURATED_LISTS.filter { it.url in selected.value }
                    onConfirm(picks)
                }
            ) { Text("Añadir") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
