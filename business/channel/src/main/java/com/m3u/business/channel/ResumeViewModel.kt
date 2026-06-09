package com.m3u.business.channel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.m3u.data.repository.channel.ChannelRepository
import com.m3u.data.service.PlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tiny VM used by the VOD/series detail sheet to read & clear the
 * "continue watching" position for the current channel.  The actual storage
 * lives in [PlayerManager] (DiskLruCache via ChannelPreferenceProvider).
 *
 * It is intentionally narrow so it can be obtained with `hiltViewModel()`
 * from inside the bottom-sheet composable without touching the three
 * existing screens that show the sheet.
 */
@HiltViewModel
class ResumeViewModel @Inject constructor(
    private val playerManager: PlayerManager,
    private val channelRepository: ChannelRepository
) : ViewModel() {

    private val _snapshot = MutableStateFlow<PlayerManager.CwSnapshot?>(null)
    val snapshot: StateFlow<PlayerManager.CwSnapshot?> = _snapshot.asStateFlow()

    /** Re-reads the persisted resume state for [channelUrl]. */
    fun refresh(channelUrl: String) {
        if (channelUrl.isBlank()) {
            _snapshot.value = null
            return
        }
        viewModelScope.launch {
            _snapshot.value = playerManager.getCwSnapshot(channelUrl)
        }
    }

    /** Discards the persisted resume position so the next play() starts from 0. */
    fun clear(channelUrl: String, channelId: Int? = null) {
        if (channelUrl.isBlank()) return
        viewModelScope.launch {
            playerManager.clearCwPosition(channelUrl)
            channelId?.let { channelRepository.clearPlaybackProgress(it) }
            _snapshot.value = null
        }
    }
}
