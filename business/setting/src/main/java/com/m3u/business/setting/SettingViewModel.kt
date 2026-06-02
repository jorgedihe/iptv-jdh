package com.m3u.business.setting

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.lifecycle.viewModelScope
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import androidx.work.workDataOf
import com.m3u.core.foundation.architecture.Publisher
import com.m3u.core.foundation.architecture.preferences.PreferencesKeys
import com.m3u.core.foundation.architecture.preferences.Settings
import com.m3u.core.foundation.architecture.preferences.flowOf
import com.m3u.core.foundation.architecture.preferences.set
import com.m3u.core.foundation.util.basic.startWithHttpScheme
import com.m3u.data.api.TvApiDelegate
import com.m3u.data.codec.CodecPackInstallResult
import com.m3u.data.codec.CodecPackRepository
import com.m3u.data.database.dao.ColorSchemeDao
import com.m3u.data.database.example.ColorSchemeExample
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.ColorScheme
import com.m3u.data.database.model.DataSource
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.isSeries
import com.m3u.data.database.model.isVod
import com.m3u.data.parser.xtream.XtreamInput
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.tv.TvRepository
import com.m3u.data.service.Messager
import com.m3u.data.worker.BackupWorker
import com.m3u.data.worker.RestoreWorker
import com.m3u.data.worker.SubscriptionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.Clock

@HiltViewModel
class SettingViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val playlistRepository: PlaylistRepository,
    private val channelRepository: ChannelRepository,
    private val workManager: WorkManager,
    private val settings: Settings,
    private val messager: Messager,
    private val tvRepository: TvRepository,
    private val tvApi: TvApiDelegate,
    private val codecPackRepository: CodecPackRepository,
    publisher: Publisher,
    // FIXME: do not use dao in viewmodel
    private val colorSchemeDao: ColorSchemeDao,
) : ViewModel() {
    private val _codecPackState = MutableStateFlow(codecPackRepository.toPendingState())
    val codecPackState: StateFlow<CodecPackState> = _codecPackState

    init {
        refreshCodecPack()
    }

    val epgs: StateFlow<List<Playlist>> = playlistRepository
        .observeAllEpgs()
        .stateIn(
            scope = viewModelScope,
            initialValue = emptyList(),
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    /** All playlists (live/vod/series of every provider) with channel counts. */
    val playlistsWithCounts: StateFlow<Map<Playlist, Int>> = playlistRepository
        .observeAllCounts()
        .stateIn(
            scope = viewModelScope,
            initialValue = emptyMap(),
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    /** Same as [playlistsWithCounts] but includes EPG playlists. Used by the
     *  Lists tab so users can see (and delete) every saved EPG. */
    val playlistsWithCountsIncludingEpg: StateFlow<Map<Playlist, Int>> = playlistRepository
        .observeAllCountsIncludingEpg()
        .stateIn(
            scope = viewModelScope,
            initialValue = emptyMap(),
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    /** URLs of playlists whose SubscriptionWorker is currently running or queued. */
    val refreshingPlaylistUrls: StateFlow<Set<String>> = workManager
        .getWorkInfosFlow(
            androidx.work.WorkQuery.fromStates(
                androidx.work.WorkInfo.State.RUNNING,
                androidx.work.WorkInfo.State.ENQUEUED
            )
        )
        .combine(playlistRepository.observePlaylistUrls()) { infos, urls ->
            val urlSet = urls.toSet()
            infos
                .filter { SubscriptionWorker.TAG in it.tags }
                .flatMap { it.tags }
                .filter { it in urlSet }
                .toSet()
        }
        .stateIn(
            scope = viewModelScope,
            initialValue = emptySet(),
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    fun refreshPlaylist(url: String) {
        viewModelScope.launch {
            runCatching {
                val playlist = playlistRepository.get(url) ?: return@runCatching
                if (playlist.source == DataSource.Xtream) {
                    val input = XtreamInput.decodeFromPlaylistUrl(playlist.url)
                    // Refresh only the playlist the user pressed on (its own type).
                    SubscriptionWorker.xtream(
                        workManager = workManager,
                        title = playlist.title,
                        url = playlist.url,
                        basicUrl = input.basicUrl,
                        username = input.username,
                        password = input.password
                    )
                    // If any sibling playlist (live / vod / series) for the same provider
                    // is missing, create it now so the user gets the full catalogue.
                    val providerKey = "${input.basicUrl}|${input.username}"
                    val siblings = playlistRepository.getAll().filter { other ->
                        if (other.source != DataSource.Xtream) return@filter false
                        val otherInput = runCatching {
                            XtreamInput.decodeFromPlaylistUrl(other.url)
                        }.getOrNull() ?: return@filter false
                        "${otherInput.basicUrl}|${otherInput.username}" == providerKey
                    }
                    val types = siblings.mapNotNull { other ->
                        runCatching {
                            XtreamInput.decodeFromPlaylistUrl(other.url).type
                        }.getOrNull()
                    }.toSet()
                    val allTypes = listOf(
                        XtreamInput.TYPE_LIVE,
                        XtreamInput.TYPE_VOD,
                        XtreamInput.TYPE_SERIES
                    )
                    allTypes.filterNot { it in types }.forEach { missingType ->
                        val newUrl = XtreamInput.encodeToPlaylistUrl(
                            XtreamInput(
                                basicUrl = input.basicUrl,
                                username = input.username,
                                password = input.password,
                                type = missingType
                            )
                        )
                        SubscriptionWorker.xtream(
                            workManager = workManager,
                            title = playlist.title,
                            url = newUrl,
                            basicUrl = input.basicUrl,
                            username = input.username,
                            password = input.password
                        )
                    }
                    // EPG download for the Live playlist.
                    SubscriptionWorker.epg(
                        workManager = workManager,
                        playlistUrl = playlist.url,
                        ignoreCache = true
                    )
                } else {
                    playlistRepository.refresh(url)
                }
            }
        }
    }

    /**
     * Replace an existing Xtream playlist with a new one (URL/credentials change).
     * Internally: unsubscribe the old one and enqueue a new xtream subscription.
     * Title is kept as provided.
     */
    fun replaceXtreamPlaylist(
        oldUrl: String,
        title: String,
        basicUrl: String,
        username: String,
        password: String
    ) {
        viewModelScope.launch {
            runCatching { playlistRepository.unsubscribe(oldUrl) }
            runCatching {
                SubscriptionWorker.xtream(
                    workManager = workManager,
                    title = title,
                    url = oldUrl, // url field is ignored by xtream worker; basicUrl/username/password are used
                    basicUrl = basicUrl,
                    username = username,
                    password = password
                )
            }
        }
    }

    fun deletePlaylist(url: String) {
        viewModelScope.launch {
            runCatching { playlistRepository.unsubscribe(url) }
        }
    }

    fun renamePlaylist(url: String, title: String) {
        viewModelScope.launch {
            runCatching { playlistRepository.onUpdatePlaylistTitle(url, title) }
        }
    }

    /** Currently active provider key (basicUrl|username), empty if none. */
    val activeProviderKey: kotlinx.coroutines.flow.StateFlow<String> = settings
        .flowOf(PreferencesKeys.ACTIVE_PROVIDER_KEY)
        .stateIn(
            scope = viewModelScope,
            initialValue = "",
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    fun setActiveProviderByPlaylistUrl(url: String) {
        viewModelScope.launch {
            runCatching {
                val input = XtreamInput.decodeFromPlaylistUrl(url)
                val key = "${input.basicUrl}|${input.username}"
                settings[PreferencesKeys.ACTIVE_PROVIDER_KEY] = key
            }
        }
    }

    private fun computeProviderKeyFromUrl(url: String): String? = runCatching {
        val input = XtreamInput.decodeFromPlaylistUrl(url)
        "${input.basicUrl}|${input.username}"
    }.getOrNull()

    fun providerKeyOf(url: String): String? = computeProviderKeyFromUrl(url)

    val hiddenChannels: StateFlow<List<Channel>> = channelRepository
        .observeAllHidden()
        .stateIn(
            scope = viewModelScope,
            initialValue = emptyList(),
            started = SharingStarted.WhileSubscribed(5_000L)
        )

    val hiddenCategoriesWithPlaylists: StateFlow<List<Pair<Playlist, String>>> =
        playlistRepository
            .observeAll()
            .map { playlists ->
                playlists
                    .filter { it.hiddenCategories.isNotEmpty() }
                    .flatMap { playlist -> playlist.hiddenCategories.map { playlist to it } }
            }
            .flowOn(Dispatchers.Default)
            .stateIn(
                scope = viewModelScope,
                initialValue = emptyList(),
                started = SharingStarted.WhileSubscribed(5_000L)
            )

    fun onUnhidePlaylistCategory(playlistUrl: String, group: String) {
        viewModelScope.launch {
            playlistRepository.hideOrUnhideCategory(playlistUrl, group)
        }
    }

    fun refreshCodecPack() {
        viewModelScope.launch(Dispatchers.IO) {
            _codecPackState.value = codecPackRepository.toState()
        }
    }

    fun installCodecPack() {
        if (!_codecPackState.value.enabled) return
        if (_codecPackState.value.installing) return
        _codecPackState.value = _codecPackState.value.copy(installing = true, error = null)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                codecPackRepository.installFromDefaultSnapshot()
            }.fold(
                onSuccess = { result ->
                    _codecPackState.value = codecPackRepository.toState().copy(
                        error = when (result) {
                            is CodecPackInstallResult.UnsupportedAbi -> result.supportedAbis.joinToString()
                            else -> null
                        }
                    )
                },
                onFailure = { error ->
                    _codecPackState.value = codecPackRepository.toState().copy(error = error.message)
                }
            )
        }
    }

    fun deleteCodecPack() {
        viewModelScope.launch(Dispatchers.IO) {
            codecPackRepository.deleteInstalledPack()
            _codecPackState.value = codecPackRepository.toState()
        }
    }

    private fun CodecPackRepository.toState(): CodecPackState {
        return CodecPackState(
            packId = packId,
            enabled = enabled,
            abi = currentAbi,
            installed = isInstalled()
        )
    }

    private fun CodecPackRepository.toPendingState(): CodecPackState {
        return CodecPackState(
            packId = packId,
            enabled = enabled,
            abi = currentAbi
        )
    }

    val colorSchemes: StateFlow<List<ColorScheme>> = combine(
        colorSchemeDao.observeAll().catch { emit(emptyList()) },
        settings.flowOf(PreferencesKeys.FOLLOW_SYSTEM_THEME)
    ) { all, followSystemTheme -> if (followSystemTheme) all.filter { !it.isDark } else all }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList()
        )

    fun onClipboard(url: String) {
        val title = run {
            val filePath = url.split("/")
            val fileSplit = filePath.lastOrNull()?.split(".") ?: emptyList()
            fileSplit.firstOrNull() ?: "Playlist_${System.currentTimeMillis()}"
        }
        properties.titleState.value = Uri.decode(title)
        properties.urlState.value = Uri.decode(url)
        when (properties.selectedState.value) {
            is DataSource.Xtream -> {
                val input = XtreamInput.decodeFromPlaylistUrlOrNull(url) ?: return
                properties.basicUrlState.value = input.basicUrl
                properties.usernameState.value = input.username
                properties.passwordState.value = input.password
                properties.titleState.value = Uri.decode("Xtream_${Clock.System.now().toEpochMilliseconds()}")
            }

            else -> {}
        }
    }

    fun onUnhideChannel(channelId: Int) {
        val hidden = hiddenChannels.value.find { it.id == channelId }
        if (hidden != null) {
            viewModelScope.launch {
                channelRepository.hide(channelId, false)
            }
        }
    }

    /**
     * Add an EPG (XMLTV) playlist directly. Called from the Lists tab's
     * "+ Añadir guía EPG" dialog. Validates that the URL is not already in
     * use by an M3U / Xtream playlist (which would otherwise overwrite the
     * existing list — see [PlaylistRepositoryImpl.insertEpgAsPlaylist]).
     */
    fun addEpg(title: String, epgUrl: String) {
        val trimmedTitle = title.trim()
        val trimmedUrl = epgUrl.trim()
        if (trimmedTitle.isEmpty()) {
            messager.emit(SettingMessage.EmptyEpgTitle)
            return
        }
        if (trimmedUrl.isEmpty()) {
            messager.emit(SettingMessage.EmptyEpg)
            return
        }
        viewModelScope.launch {
            runCatching { playlistRepository.insertEpgAsPlaylist(trimmedTitle, trimmedUrl) }
                .onSuccess { messager.emit(SettingMessage.EpgAdded) }
                .onFailure { e ->
                    messager.emit(
                        e.message ?: "No se pudo guardar el EPG: la URL ya está en uso."
                    )
                }
        }
    }

    /**
     * Attach (or detach if [epgUrl] is null) an EPG URL to a channel playlist.
     * Used by the edit-list dialog so a list can be linked to one of the
     * stored EPGs explicitly (Option A flow). One EPG per playlist for now;
     * the underlying model supports multiple, but the UI exposes a single
     * dropdown so we replace the whole list every time.
     */
    fun setPlaylistEpg(playlistUrl: String, epgUrl: String?) {
        viewModelScope.launch {
            val current = playlistRepository.get(playlistUrl) ?: return@launch
            // Remove every existing EPG association first.
            current.epgUrls.forEach { existing ->
                playlistRepository.onUpdateEpgPlaylist(
                    com.m3u.data.repository.playlist.PlaylistRepository.EpgPlaylistUseCase.Check(
                        playlistUrl = playlistUrl,
                        epgUrl = existing,
                        action = false
                    )
                )
            }
            // Then add the new one, if any.
            if (!epgUrl.isNullOrBlank()) {
                playlistRepository.onUpdateEpgPlaylist(
                    com.m3u.data.repository.playlist.PlaylistRepository.EpgPlaylistUseCase.Check(
                        playlistUrl = playlistUrl,
                        epgUrl = epgUrl,
                        action = true
                    )
                )
            }
        }
    }

    fun subscribe() {
        val title = properties.titleState.value
        val url = properties.urlState.value
        val uri = properties.uriState.value
        val inputBasicUrl = properties.basicUrlState.value
        val username = properties.usernameState.value
        val password = properties.passwordState.value
        val epg = properties.epgState.value
        val selected = properties.selectedState.value
        val localStorage = properties.localStorageState.value
        val forTv = properties.forTvState.value
        val urlOrUri = uri
            .takeIf { uri != Uri.EMPTY }?.toString().orEmpty()
            .takeIf { localStorage }
            ?: url

        val basicUrl = if (inputBasicUrl.startWithHttpScheme()) inputBasicUrl
        else "http://$inputBasicUrl"

        if (forTv) {
            subscribeForTv(
                selected = selected,
                title = title,
                url = url,
                basicUrl = basicUrl,
                username = username,
                password = password,
                epg = epg
            )
            return
        }

        when (selected) {
                DataSource.M3U -> {
                    if (title.isEmpty()) {
                        messager.emit(SettingMessage.EmptyTitle)
                        return
                    }
                    if (localStorage) {
                        if (uri == Uri.EMPTY) {
                            messager.emit(SettingMessage.EmptyFile)
                            return
                        }
                    } else {
                        if (url.isBlank()) {
                            messager.emit(SettingMessage.EmptyUrl)
                            return
                        }
                    }
                    SubscriptionWorker.m3u(workManager, title, urlOrUri)
                    messager.emit(SettingMessage.Enqueued)
                }

                DataSource.EPG -> {
                    // The "EPG" option was removed from the Add-list selector.
                    // Adding EPGs now happens via [addEpg] from the Lists tab.
                    addEpg(title, epg)
                }

                DataSource.Xtream -> {
                    if (title.isEmpty()) {
                        messager.emit(SettingMessage.EmptyTitle)
                        return
                    }
                    SubscriptionWorker.xtream(
                        workManager,
                        title,
                        urlOrUri,
                        basicUrl,
                        username,
                        password
                    )
                    messager.emit(SettingMessage.Enqueued)
                }

                else -> return
            }
        resetAllInputs()
    }

    private fun subscribeForTv(
        selected: DataSource,
        title: String,
        url: String,
        basicUrl: String,
        username: String,
        password: String,
        epg: String
    ) {
        if (tvRepository.connected.value == null) {
            messager.emit(SettingMessage.RemoteTvNotConnected)
            return
        }

        when (selected) {
            DataSource.M3U -> {
                if (title.isEmpty()) {
                    messager.emit(SettingMessage.EmptyTitle)
                    return
                }
                if (url.isBlank()) {
                    messager.emit(SettingMessage.EmptyUrl)
                    return
                }
            }

            DataSource.EPG -> {
                if (title.isEmpty()) {
                    messager.emit(SettingMessage.EmptyEpgTitle)
                    return
                }
                if (epg.isEmpty()) {
                    messager.emit(SettingMessage.EmptyEpg)
                    return
                }
            }

            DataSource.Xtream -> {
                if (title.isEmpty()) {
                    messager.emit(SettingMessage.EmptyTitle)
                    return
                }
            }

            else -> return
        }

        viewModelScope.launch {
            val result = runCatching {
                tvApi.subscribe(
                    title = title,
                    url = url.ifBlank { basicUrl },
                    basicUrl = basicUrl,
                    username = username,
                    password = password,
                    epg = epg.ifBlank { null },
                    dataSource = selected
                )
            }.getOrNull()
            if (result?.result == true) {
                messager.emit(SettingMessage.RemoteTvSubscribeSent)
                resetAllInputs()
            } else {
                messager.emit(SettingMessage.RemoteTvSubscribeFailed)
            }
        }
    }

    val backingUpOrRestoring: StateFlow<BackingUpAndRestoringState> = workManager
        .getWorkInfosFlow(
            WorkQuery.fromStates(
                WorkInfo.State.RUNNING,
                WorkInfo.State.ENQUEUED
            )
        )
        .mapLatest { infos ->
            var backingUp = false
            var restoring = false
            for (info in infos) {
                if (backingUp && restoring) break
                for (tag in info.tags) {
                    if (backingUp && restoring) break
                    if (tag == BackupWorker.TAG) backingUp = true
                    if (tag == RestoreWorker.TAG) restoring = true
                }
            }
            BackingUpAndRestoringState.of(backingUp, restoring)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            // determine ui button enabled or not
            // both as default
            initialValue = BackingUpAndRestoringState.BOTH,
            started = SharingStarted.WhileSubscribed(5000)
        )

    fun backup(uri: Uri) {
        workManager.cancelAllWorkByTag(BackupWorker.TAG)
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .setInputData(
                workDataOf(
                    BackupWorker.INPUT_URI to uri.toString()
                )
            )
            .addTag(BackupWorker.TAG)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueue(request)
        messager.emit(SettingMessage.BackingUp)
    }

    /** Persistent filename for the auto-backup file in Downloads. */
    private val backupFilename = "IPTV-JDH-backup.txt"

    /**
     * Write the backup to the system Downloads folder with no UI picker.
     * Uses MediaStore on Android 10+ (no permission required for own files);
     * falls back to direct File on older versions. Returns the resulting URI
     * and emits a user-facing message describing where the file landed.
     */
    fun backupToDownloads() {
        val uri: Uri? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = appContext.contentResolver
                val collection = MediaStore.Downloads.getContentUri(
                    MediaStore.VOLUME_EXTERNAL_PRIMARY
                )
                // Delete a previous auto-backup if it exists, so users get a
                // fresh "latest backup" instead of an ever-growing folder.
                resolver.query(
                    collection,
                    arrayOf(MediaStore.Downloads._ID),
                    "${MediaStore.Downloads.DISPLAY_NAME}=? AND " +
                            "${MediaStore.Downloads.RELATIVE_PATH}=?",
                    arrayOf(backupFilename, Environment.DIRECTORY_DOWNLOADS + "/"),
                    null
                )?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        resolver.delete(
                            android.content.ContentUris.withAppendedId(collection, id),
                            null,
                            null
                        )
                    }
                }
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, backupFilename)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    put(MediaStore.Downloads.IS_PENDING, 1)
                }
                resolver.insert(collection, values)?.also { newUri ->
                    // The BackupWorker will write content; flip IS_PENDING off
                    // after the worker finishes — but Android also clears it
                    // automatically when the OutputStream is closed by the
                    // backup writer. Setting it now would race with the
                    // worker, so we leave it as 1 and clear via the worker's
                    // success callback path (already opens/closes the stream).
                    values.clear()
                    values.put(MediaStore.Downloads.IS_PENDING, 0)
                    // Pre-clearing is harmless because the worker will still
                    // truncate-and-write on open; clearer state for the
                    // file manager in the meantime.
                    resolver.update(newUri, values, null, null)
                }
            } else {
                // Pre-Android 10: write directly to public Downloads folder.
                // Requires WRITE_EXTERNAL_STORAGE on API <29.
                @Suppress("DEPRECATION")
                val downloads = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                if (!downloads.exists()) downloads.mkdirs()
                val file = java.io.File(downloads, backupFilename)
                // If the file already exists, overwrite it cleanly.
                if (file.exists()) file.delete()
                file.createNewFile()
                Uri.fromFile(file)
            }
        } catch (e: Exception) {
            messager.emit("No se pudo crear el archivo de copia: ${e.message}")
            null
        }
        if (uri == null) return
        backup(uri)
    }

    /**
     * Restore from the auto-backup file in Downloads, if it exists. No UI
     * picker. If multiple backups exist (filename collision was avoided), the
     * MediaStore query returns the most recent one.
     */
    fun restoreFromDownloads() {
        val uri: Uri? = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = appContext.contentResolver
                val collection = MediaStore.Downloads.getContentUri(
                    MediaStore.VOLUME_EXTERNAL_PRIMARY
                )
                var found: Uri? = null
                resolver.query(
                    collection,
                    arrayOf(MediaStore.Downloads._ID),
                    "${MediaStore.Downloads.DISPLAY_NAME}=?",
                    arrayOf(backupFilename),
                    "${MediaStore.Downloads.DATE_MODIFIED} DESC"
                )?.use { c ->
                    if (c.moveToFirst()) {
                        val id = c.getLong(0)
                        found = android.content.ContentUris.withAppendedId(collection, id)
                    }
                }
                found
            } else {
                @Suppress("DEPRECATION")
                val downloads = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                val file = java.io.File(downloads, backupFilename)
                if (file.exists()) Uri.fromFile(file) else null
            }
        } catch (e: Exception) {
            messager.emit("No se pudo abrir la copia: ${e.message}")
            null
        }
        if (uri == null) {
            messager.emit("No hay copia para restaurar en Descargas/$backupFilename")
            return
        }
        restore(uri)
    }

    fun restore(uri: Uri) {
        workManager.cancelAllWorkByTag(RestoreWorker.TAG)
        val request = OneTimeWorkRequestBuilder<RestoreWorker>()
            .setInputData(
                workDataOf(
                    RestoreWorker.INPUT_URI to uri.toString()
                )
            )
            .addTag(RestoreWorker.TAG)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .build()
        workManager.enqueue(request)
        messager.emit(SettingMessage.Restoring)
    }

    private fun resetAllInputs() {
        with(properties) {
            titleState.value = ""
            urlState.value = ""
            uriState.value = Uri.EMPTY
            basicUrlState.value = ""
            usernameState.value = ""
            passwordState.value = ""
            epgState.value = ""
        }
    }

    fun deleteEpgPlaylist(epgUrl: String) {
        viewModelScope.launch {
            playlistRepository.deleteEpgPlaylistAndProgrammes(epgUrl)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun applyColor(
        prev: ColorScheme?,
        argb: Int,
        isDark: Boolean
    ) {
        viewModelScope.launch {
            settings[PreferencesKeys.DARK_MODE] = isDark
            if (prev != null) {
                colorSchemeDao.delete(prev)
            }
            colorSchemeDao.insert(
                ColorScheme(
                    argb = argb,
                    isDark = isDark,
                    name = "#${argb.toHexString(HexFormat.UpperCase)}"
                )
            )
        }
    }

    fun restoreSchemes() {
        val schemes = ColorSchemeExample.schemes
        viewModelScope.launch {
            colorSchemeDao.insertAll(*schemes.toTypedArray())
        }
    }

    val versionName: String = publisher.versionName
    val versionCode: Int = publisher.versionCode

    val properties = SettingProperties()
}
