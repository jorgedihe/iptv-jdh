package com.m3u.business.channel

import androidx.compose.runtime.Immutable

/**
 * Lightweight, UI-facing view of an Xtream VOD info payload. We only keep the
 * fields the player overlay actually renders — plot, year, genre, duration,
 * director, cast, rating, poster — so the model is stable across server
 * variants (some Xtream backends ship slightly different JSON shapes).
 *
 * [crewPeople] and [castPeople] are populated by a follow-up TMDB call when a
 * tmdb_id is available, so the UI can render proper photos instead of name
 * initials. When TMDB is not reachable we still have [director] / [cast]
 * as raw comma-separated strings to fall back on.
 */
@Immutable
data class VodInfo(
    val title: String,
    val plot: String? = null,
    val releaseDate: String? = null,
    val genre: String? = null,
    val duration: String? = null,
    val rating: String? = null,
    val director: String? = null,
    val cast: String? = null,
    val cover: String? = null,
    val backdrop: String? = null,
    val tmdbId: String? = null,
    val crewPeople: List<Person> = emptyList(),
    val castPeople: List<Person> = emptyList(),
)

@Immutable
data class Person(
    val name: String,
    /** Role label: "Dirección", "Guion", or the character name for cast. */
    val role: String? = null,
    /** Full TMDB image URL (e.g. https://image.tmdb.org/t/p/w185/abc.jpg). */
    val photoUrl: String? = null,
)

/** Lightweight representation of a series episode that the UI can render
 *  without holding on to the heavy XtreamEpisodeInfo from the data layer. */
@Immutable
data class EpisodeRow(
    /** Xtream stream id, used to build the playable URL. */
    val id: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    /** Human-friendly title, with the show prefix stripped if possible. */
    val title: String,
    /** "01:03:56" — comes straight from Xtream. */
    val duration: String? = null,
    /** TMDB still image (16:9 thumbnail) once enriched, null beforehand. */
    val stillUrl: String? = null,
    /** Episode synopsis from TMDB; null if no key or no match. */
    val overview: String? = null,
)
