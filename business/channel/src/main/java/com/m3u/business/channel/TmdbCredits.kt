package com.m3u.business.channel

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
 * Pulls cast and crew (with photos) from TMDB's /credits endpoints. We share
 * a single OkHttpClient and a single permissive Json across calls.
 *
 * The TMDB v3 API key is required for read calls. We expect it in the
 * `TMDB_API_KEY` BuildConfig field of the host module; when it's blank we
 * skip the network call and the UI falls back to name initials.
 */
internal object TmdbCredits {

    private val http = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** w185 keeps photos crisp on a 72 dp circle without wasting bandwidth. */
    private const val IMAGE_BASE = "https://image.tmdb.org/t/p/w185"
    private const val MAX_CAST = 15
    private const val MAX_CREW = 6

    suspend fun forMovie(tmdbId: String, apiKey: String): Pair<List<Person>, List<Person>>? =
        fetch("https://api.themoviedb.org/3/movie/$tmdbId/credits", apiKey)

    suspend fun forSeries(tmdbId: String, apiKey: String): Pair<List<Person>, List<Person>>? =
        fetch("https://api.themoviedb.org/3/tv/$tmdbId/credits", apiKey)

    /** Returns (crew, cast) or null on any failure / no key. */
    private suspend fun fetch(baseUrl: String, apiKey: String): Pair<List<Person>, List<Person>>? =
        withContext(Dispatchers.IO) {
            if (apiKey.isBlank()) return@withContext null
            runCatching {
                val url = "$baseUrl?api_key=$apiKey&language=es-ES"
                val request = Request.Builder().url(url).build()
                http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val root = json.parseToJsonElement(response.body.string()) as? JsonObject
                        ?: return@runCatching null

                    val cast = (root["cast"] as? JsonArray).orEmpty().take(MAX_CAST)
                        .mapNotNull { it as? JsonObject }
                        .mapNotNull { obj ->
                            val name = obj.string("name") ?: return@mapNotNull null
                            Person(
                                name = name,
                                role = obj.string("character"),
                                photoUrl = obj.string("profile_path")?.let { IMAGE_BASE + it }
                            )
                        }

                    val crew = (root["crew"] as? JsonArray).orEmpty()
                        .mapNotNull { it as? JsonObject }
                        // Prioritise the roles users actually care about.
                        .filter { obj ->
                            val job = obj.string("job").orEmpty()
                            job == "Director" || job == "Screenplay" || job == "Writer" ||
                                    job == "Original Music Composer"
                        }
                        .take(MAX_CREW)
                        .mapNotNull { obj ->
                            val name = obj.string("name") ?: return@mapNotNull null
                            val role = when (obj.string("job")) {
                                "Director" -> "Dirección"
                                "Screenplay", "Writer" -> "Guion"
                                "Original Music Composer" -> "Música"
                                else -> obj.string("job")
                            }
                            Person(
                                name = name,
                                role = role,
                                photoUrl = obj.string("profile_path")?.let { IMAGE_BASE + it }
                            )
                        }

                    crew to cast
                }
            }.getOrNull()
        }

    private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

    private fun JsonObject.string(name: String): String? {
        val prim = this[name] as? JsonPrimitive ?: return null
        if (prim is JsonNull) return null
        return prim.content.takeIf { it.isNotBlank() && it != "null" }
    }
}
