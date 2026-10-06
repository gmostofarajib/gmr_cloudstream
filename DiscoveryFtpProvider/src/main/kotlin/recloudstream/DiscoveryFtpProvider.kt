package recloudstream

import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.net.URLEncoder

class DiscoveryFtpProvider : MainAPI() {
    override var mainUrl = "https://discoveryftp.net"
    override var name = "DiscoveryFTP"
    override val supportedTypes = setOf(TvType.Movie)
    override var lang = "en"
    override val hasMainPage = true

    private val searchBase = "https://movies.discoveryftp.net/m/find/"
    private val cdn = "http://cdn1.discoveryftp.net/"
    private val posterBase = "https://images1.discoveryftp.net/media/300/"
    private val videoExt = Regex("""\.(mkv|mp4|avi|webm|m4v)$""", RegexOption.IGNORE_CASE)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")
    private fun dec(s: String) = URLDecoder.decode(s, "UTF-8")

    // Poster path mirrors the CDN folder: .../media/300/Movies/Hindi/2026/Title/poster.jpg
    private fun folderFromPoster(poster: String): String? {
        if (!poster.startsWith(posterBase)) return null
        val path = poster.removePrefix(posterBase).removeSuffix("/poster.jpg")
        return cdn + path.split("/").joinToString("/") { enc(dec(it)) }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = selectFirst("h3")?.text()?.takeIf { it.isNotBlank() } ?: return null
        val poster = selectFirst("img")?.attr("src") ?: return null
        val folder = folderFromPoster(poster) ?: return null
        val year = selectFirst("span.movie_details_span")?.text()?.trim()?.toIntOrNull()
        return newMovieSearchResponse(title, folder, TvType.Movie) {
            this.posterUrl = poster
            this.year = year
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get(mainUrl).document
        val rows = doc.select("div.moviegrid").mapIndexedNotNull { i, grid ->
            val items = grid.select("div.card").mapNotNull { it.toSearchResult() }
            if (items.isEmpty()) null else HomePageList("Movies ${i + 1}", items)
        }
        return newHomePageResponse(rows, hasNext = false)
    }

    override suspend fun search(query: String): List<SearchResponse>? {
        val doc = app.get(searchBase + enc(query.trim())).document
        return doc.select("div.card").mapNotNull { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
        val folder = url.trimEnd('/')
        val parts = dec(folder.removePrefix(cdn)).split("/")   // Movies / Hindi / 2026 / Title
        val title = parts.last()
        val year = parts.getOrNull(parts.size - 2)?.toIntOrNull()
        val language = parts.getOrNull(1)
        val poster = posterBase + parts.joinToString("/") { enc(it) } + "/poster.jpg"
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = poster
            this.year = year
            if (language != null) this.tags = listOf(language)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data.trimEnd('/') + "/").document
        val files = doc.select("a[href]")
            .map { it.attr("abs:href") }
            .filter { videoExt.containsMatchIn(it) }
            .distinct()

        files.forEach { file ->
            val fileName = dec(file.substringAfterLast('/'))
            callback(
                newExtractorLink(name, fileName, file) {
                    quality = getQualityFromName(fileName)
                }
            )
        }
        return files.isNotEmpty()
    }
}
