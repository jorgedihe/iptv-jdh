package com.m3u.smartphone.ui.business.setting.components

/**
 * Hand-curated subset of the iptv-org public playlists. Shared between the
 * Lists tab and the Add-list screen so both surfaces show the same set.
 */
internal data class CuratedList(
    val title: String,
    val url: String,
    val description: String,
)

internal val CURATED_LISTS: List<CuratedList> = listOf(
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
