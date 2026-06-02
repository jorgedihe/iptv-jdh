package com.m3u.smartphone.ui.business.epg

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Programme
import com.m3u.data.database.model.isSeries
import com.m3u.data.database.model.isVod
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.repository.playlist.PlaylistRepository
import com.m3u.data.repository.programme.ProgrammeRepository
import com.m3u.data.worker.SubscriptionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

data class EpgEntry(
    val channel: Channel,
    val current: Programme?,
    val playlistTitle: String
)

@HiltViewModel
class EpgViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val channelRepository: ChannelRepository,
    private val programmeRepository: ProgrammeRepository,
    private val workManager: WorkManager
) : ViewModel() {

    private val _entries = MutableStateFlow<List<EpgEntry>>(emptyList())
    val entries: StateFlow<List<EpgEntry>> = _entries.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        // On first open, kick off an EPG download if no programmes are stored yet,
        // and then load whatever we currently have from the database.
        viewModelScope.launch {
            val livePlaylists = playlistRepository.getAll().filter { !it.isVod && !it.isSeries }
            livePlaylists.forEach { playlist ->
                SubscriptionWorker.epg(
                    workManager = workManager,
                    playlistUrl = playlist.url,
                    ignoreCache = false
                )
            }
        }
        loadFromDatabase()
    }

    /** Force a fresh download of the EPG XMLTV from the provider. */
    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            val livePlaylists = playlistRepository.getAll().filter { !it.isVod && !it.isSeries }
            livePlaylists.forEach { playlist ->
                SubscriptionWorker.epg(
                    workManager = workManager,
                    playlistUrl = playlist.url,
                    ignoreCache = true
                )
            }
            // The worker runs async; poll a few times so the UI shows entries as they arrive.
            repeat(6) {
                delay(2_000)
                loadFromDatabaseSuspend()
            }
            _isLoading.value = false
        }
    }

    private fun loadFromDatabase() {
        viewModelScope.launch { loadFromDatabaseSuspend() }
    }

    private suspend fun loadFromDatabaseSuspend() {
        val livePlaylists = playlistRepository.getAll().filter { !it.isVod && !it.isSeries }
        val raw = livePlaylists.flatMap { playlist ->
            val channels = channelRepository.getByPlaylistUrl(playlist.url)
            val current = runCatching {
                programmeRepository.getProgrammesCurrently(playlist.url)
            }.getOrDefault(emptyMap())

            channels
                .filter { !it.hidden }
                .map { ch ->
                    EpgEntry(
                        channel = ch,
                        current = ch.relationId?.let { current[it] },
                        playlistTitle = playlist.title
                    )
                }
        }
        // Sort by language affinity (device locale first) then by title.
        val localeTag = java.util.Locale.getDefault().language.uppercase()
        fun priority(name: String): Int {
            val upper = name.uppercase()
            return when {
                upper.startsWith("$localeTag|") || upper.startsWith("$localeTag ") -> 0
                upper.matches(Regex("^[A-Z]{2}[| ].*")) -> 2
                else -> 3
            }
        }
        _entries.value = raw.sortedWith(
            compareBy({ priority(it.channel.title) }, { it.channel.title })
        )
    }
}
