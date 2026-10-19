package suwayomi.tachidesk.manga.impl.komga

import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
internal data class KomgaIndexedSeries(
    val id: String,
    val title: String,
    val alternateTitles: List<String> = emptyList(),
    val creators: List<String> = emptyList(),
    val urls: List<String> = emptyList(),
    val books: Int? = null,
)

internal object KomgaMatching {
    private val whitespace = Regex("[\\s\\p{Z}]+")

    fun normalize(value: String): String = value.replace(whitespace, " ").trim().lowercase(Locale.ROOT)

    fun titles(series: KomgaIndexedSeries): Set<String> =
        (series.alternateTitles + series.title).map(::normalize).filter { it.isNotEmpty() }.toSet()

    fun match(
        title: String,
        creators: List<String>,
        url: String?,
        candidates: List<KomgaIndexedSeries>,
    ): Pair<String, KomgaIndexedSeries?> {
        if (candidates.isEmpty()) return "UNMATCHED" to null
        if (candidates.size != 1) return "AMBIGUOUS" to null
        val candidate = candidates.single()
        val localCreators =
            creators
                .flatMap { it.split(',', ';') }
                .map(::normalize)
                .filter { it.isNotEmpty() }
                .toSet()
        val remoteCreators =
            candidate.creators
                .map(::normalize)
                .filter { it.isNotEmpty() }
                .toSet()
        val creatorMatch = localCreators.intersect(remoteCreators).isNotEmpty()

        fun authoritative(value: String?): java.net.URI? =
            value
                ?.let { runCatching { java.net.URI(it.substringBefore('#')) }.getOrNull() }
                ?.takeIf { it.isAbsolute && it.host != null && it.scheme.lowercase(Locale.ROOT) in setOf("http", "https") }
        val localUrl = authoritative(url)
        val remoteUrls = candidate.urls.mapNotNull(::authoritative)
        val urlMatch = localUrl != null && localUrl in remoteUrls
        // A shared artist must not hide a contradictory known author (or vice versa).
        val creatorConflict = localCreators.isNotEmpty() && remoteCreators.isNotEmpty() && !remoteCreators.containsAll(localCreators)
        // Only compare authoritative URLs on the same host; unrelated catalog links are not contradictions.
        val urlConflict =
            localUrl != null && remoteUrls.any { it.host.equals(localUrl.host, ignoreCase = true) && it != localUrl }
        if (creatorConflict || urlConflict) return "REVIEW" to null
        if (normalize(title) != normalize(candidate.title) && !creatorMatch && !urlMatch) return "REVIEW" to null
        return "MATCH" to candidate
    }
}
