package com.m3u.business.channel

/**
 * Public access to the TMDB v3 API key baked into the binary, plus helpers
 * to load per-episode artwork. Lets UI code anywhere in the app surface
 * episode stills without having to go through the channel ViewModel.
 */
object TmdbConfig {
    val apiKey: String get() = BuildConfig.TMDB_API_KEY
    val isEnabled: Boolean get() = apiKey.isNotBlank()

    /**
     * Convenience wrapper around [TmdbCredits.episodesForSeason] that returns
     * an empty map when the key is missing or the call fails, so callers
     * don't have to repeat the null-handling.
     */
    suspend fun episodesForSeason(
        tmdbId: String,
        seasonNumber: Int,
    ): Map<Int, TmdbCredits.EpisodeMeta> {
        if (!isEnabled) return emptyMap()
        return TmdbCredits.episodesForSeason(tmdbId, seasonNumber, apiKey)
    }
}
