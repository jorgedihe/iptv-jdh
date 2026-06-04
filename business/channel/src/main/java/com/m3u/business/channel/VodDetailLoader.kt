package com.m3u.business.channel

import com.m3u.data.database.model.Channel
import com.m3u.data.database.model.Playlist
import com.m3u.data.database.model.isSeries
import com.m3u.data.database.model.isVod
import com.m3u.data.parser.xtream.XtreamInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Pure suspend helpers to load TMDB-enriched VOD info and series episodes
 * from outside the ChannelViewModel — used by the pre-play detail screen so
 * the user can review the metadata BEFORE the player even starts. Mirrors
 * the loader used inside ChannelViewModel.
 */
object VodDetailLoader {

    private val http by lazy { OkHttpClient() }
    private val json by lazy { Json { ignoreUnknownKeys = true; isLenient = true } }

    suspend fun loadVodInfo(playlist: Playlist, channel: Channel): VodInfo? =
        withContext(Dispatchers.IO) {
            runCatching {
                val input = XtreamInput.decodeFromPlaylistUrl(playlist.url)
                val pathSegment = if (playlist.isVod) "movie" else "series"
                val idSegment = channel.url
                    .substringAfter("/$pathSegment/${input.username}/${input.password}/")
                    .substringBefore('.')
                    .substringBefore('?')
                if (idSegment.isBlank() || !idSegment.all { it.isDigit() }) return@runCatching null

                val action = if (playlist.isVod) "get_vod_info" else "get_series_info"
                val idParam = if (playlist.isVod) "vod_id" else "series_id"
                val url = "${input.basicUrl}/player_api.php?username=${input.username}" +
                        "&password=${input.password}&action=$action&$idParam=$idSegment"
                val request = Request.Builder().url(url).build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val root = json.parseToJsonElement(response.body.string()) as? JsonObject
                        ?: return@runCatching null
                    val info = (root["info"] as? JsonObject) ?: root
                    val s: (String) -> String? = { name ->
                        val prim = info[name] as? JsonPrimitive
                        if (prim == null || prim is JsonNull) null
                        else prim.content.takeIf { it.isNotBlank() && it != "null" }
                    }
                    val backdrop = (info["backdrop_path"] as? JsonArray)?.firstOrNull()
                        ?.let { it as? JsonPrimitive }
                        ?.takeIf { it !is JsonNull }
                        ?.content
                    val tmdbId = s("tmdb_id") ?: s("tmdb")
                    val base = VodInfo(
                        title = s("name") ?: s("o_name") ?: channel.title,
                        plot = s("plot") ?: s("description"),
                        releaseDate = s("releasedate") ?: s("release_date") ?: s("year"),
                        genre = s("genre"),
                        duration = s("duration"),
                        rating = s("rating") ?: s("rating_5based"),
                        director = s("director"),
                        cast = s("cast") ?: s("actors"),
                        cover = s("cover_big") ?: s("movie_image") ?: channel.cover,
                        backdrop = backdrop,
                        tmdbId = tmdbId,
                    )
                    val tmdb = tmdbId?.takeIf { TmdbConfig.isEnabled }?.let { id ->
                        if (playlist.isVod) TmdbCredits.forMovie(id, TmdbConfig.apiKey)
                        else TmdbCredits.forSeries(id, TmdbConfig.apiKey)
                    }
                    base.copy(
                        crewPeople = tmdb?.first.orEmpty(),
                        castPeople = tmdb?.second.orEmpty(),
                    )
                }
            }.getOrNull()
        }

    private data class ParsedEpisode(
        val season: Int,
        val episodeNumber: Int,
        val id: String,
        val rawTitle: String,
    )

    /** Pulls the episode list straight from get_series_info via OkHttp,
     *  bypassing the repository so this loader stays usable from any
     *  Composable without Hilt plumbing. Enriches with TMDB stills when
     *  a server-provided tmdb_id is available. */
    suspend fun loadEpisodes(
        playlist: Playlist,
        series: Channel,
    ): List<EpisodeRow> = withContext(Dispatchers.IO) {
        if (!playlist.isSeries) return@withContext emptyList()
        val input = runCatching {
            XtreamInput.decodeFromPlaylistUrl(playlist.url)
        }.getOrNull() ?: return@withContext emptyList()
        val seriesId = series.url
            .substringAfter("/series/${input.username}/${input.password}/")
            .substringBefore('.')
            .substringBefore('?')
        if (seriesId.isBlank() || !seriesId.all { it.isDigit() }) {
            return@withContext emptyList()
        }

        val url = "${input.basicUrl}/player_api.php?username=${input.username}" +
                "&password=${input.password}&action=get_series_info&series_id=$seriesId"
        val parsed = mutableListOf<ParsedEpisode>()
        var tmdbId: String? = null
        runCatching {
            http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                if (!resp.isSuccessful) return@use
                val root = json.parseToJsonElement(resp.body.string()) as? JsonObject
                    ?: return@use
                val info = root["info"] as? JsonObject
                tmdbId = info?.let { obj ->
                    val tmdbStr = (obj["tmdb"] as? JsonPrimitive)?.content
                        ?: (obj["tmdb_id"] as? JsonPrimitive)?.content
                    tmdbStr?.takeIf { it.isNotBlank() && it != "null" }
                }
                val episodes = root["episodes"] as? JsonObject ?: return@use
                episodes.entries.forEach { (seasonKey, arrElem) ->
                    val seasonNumber = seasonKey.toIntOrNull() ?: return@forEach
                    val arr = arrElem as? JsonArray ?: return@forEach
                    arr.mapNotNull { it as? JsonObject }.forEach { ep ->
                        val id = (ep["id"] as? JsonPrimitive)?.content
                            ?.takeIf { it.isNotBlank() } ?: return@forEach
                        val rawTitle = (ep["title"] as? JsonPrimitive)?.content.orEmpty()
                        val epNum = (ep["episode_num"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
                        parsed.add(ParsedEpisode(seasonNumber, epNum, id, rawTitle))
                    }
                }
            }
        }
        if (parsed.isEmpty()) return@withContext emptyList()

        val sxxExx = Regex("""S(\d+)E(\d+)""", RegexOption.IGNORE_CASE)
        val initial = parsed.map { p ->
            val match = sxxExx.find(p.rawTitle)
            val cleanTitle = p.rawTitle
                .substringAfter(" - ${match?.value ?: ""}", p.rawTitle)
                .trim().trim('-').trim()
                .ifBlank { "Episodio ${p.episodeNumber}" }
            EpisodeRow(
                id = p.id,
                seasonNumber = p.season,
                episodeNumber = p.episodeNumber,
                title = cleanTitle,
            )
        }.sortedWith(compareBy({ it.seasonNumber }, { it.episodeNumber }))

        if (tmdbId.isNullOrBlank() || !TmdbConfig.isEnabled) return@withContext initial
        val seasons = initial.map { it.seasonNumber }.toSet()
        val seasonMeta = seasons.associateWith { sn ->
            TmdbConfig.episodesForSeason(tmdbId!!, sn)
        }
        initial.map { row ->
            val meta = seasonMeta[row.seasonNumber]?.get(row.episodeNumber) ?: return@map row
            row.copy(
                title = meta.tmdbName?.takeIf { it.isNotBlank() } ?: row.title,
                stillUrl = meta.stillUrl,
                overview = meta.overview,
            )
        }
    }
}
