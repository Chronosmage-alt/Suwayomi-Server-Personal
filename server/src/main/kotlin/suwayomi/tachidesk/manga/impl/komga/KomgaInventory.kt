package suwayomi.tachidesk.manga.impl.komga

import kotlinx.serialization.json.JsonObject
import suwayomi.tachidesk.graphql.types.KomgaBookInfo
import suwayomi.tachidesk.graphql.types.KomgaMangaInfo
import java.math.BigDecimal
import java.net.URI

internal data class KomgaLocalChapter(
    val number: String?,
    val urls: List<String>,
    val downloaded: Boolean,
)

internal data class KomgaSnapshot(
    val series: JsonObject,
    val books: List<JsonObject>,
    val complete: Boolean,
    val fetchedAt: Long,
)

internal object KomgaInventory {
    fun number(value: String?): String? =
        value?.takeIf { it.matches(Regex("\\d+(?:\\.\\d+)?")) }?.let {
            runCatching { BigDecimal(it).stripTrailingZeros().toPlainString() }.getOrNull()
        }

    private fun url(value: String): String? =
        runCatching {
            val parsed = URI(value)
            val scheme = parsed.scheme?.lowercase()
            if (scheme !in listOf("http", "https") || parsed.host == null || parsed.rawUserInfo != null) {
                null
            } else {
                // Never decode reserved characters: /a%2Fb and /a/b identify different resources.
                val authority = URI(scheme, null, parsed.host.lowercase(), parsed.port, "", null, null).toASCIIString()
                val raw = authority + parsed.rawPath.ifEmpty { "/" } + (parsed.rawQuery?.let { "?$it" } ?: "")
                Regex("%[0-9a-fA-F]{2}").replace(raw) { it.value.uppercase() }
            }
        }.getOrNull()

    private fun sorted(values: Collection<String>) = values.distinct().sortedBy { BigDecimal(it) }

    fun compare(
        snapshot: KomgaSnapshot,
        chapters: List<KomgaLocalChapter>,
    ): KomgaMangaInfo {
        val source = chapters.mapNotNull { it.number }.toSet()
        val downloaded = chapters.filter { it.downloaded }.mapNotNull { it.number }.toSet()
        val urls = mutableMapOf<String, MutableSet<String>>()
        chapters.forEach { chapter ->
            chapter.number?.let { number ->
                chapter.urls.mapNotNull(::url).forEach { urls.getOrPut(it) { mutableSetOf() }.add(number) }
            }
        }
        val books =
            snapshot.books.filter { it.bool("deleted") != true }.distinctBy { it.text("id") }.map { book ->
                val metadata = book.obj("metadata")
                val confirmed =
                    metadata
                        .objects("links")
                        .flatMap { link ->
                            url(link.text("url").orEmpty())
                                ?.let {
                                    urls[it]?.takeIf { numbers ->
                                        numbers.size ==
                                            1
                                    }
                                }.orEmpty()
                        }.toSet()
                val progress = book.obj("readProgress")
                KomgaBookInfo(
                    id = book.text("id").orEmpty(),
                    title = metadata.text("title") ?: book.text("name") ?: "",
                    numberHint = number(metadata.text("number")),
                    confirmedNumbers = sorted(confirmed),
                    read = if (progress.isEmpty()) false else progress.bool("completed"),
                    page = progress.int("page"),
                    pages = book.obj("media").int("pagesCount"),
                    lastProgressAt = progress.text("lastModified"),
                )
            }
        val confirmed = books.flatMap { it.confirmedNumbers }.toSet()
        val hints = books.mapNotNull { it.numberHint }.toSet() - confirmed
        val ambiguous = books.count { it.confirmedNumbers.isEmpty() }
        val unknown = chapters.count { it.number == null }
        val complete = snapshot.complete && snapshot.series.int("booksCount")?.let { it == books.size } != false
        val missing = source - confirmed
        return KomgaMangaInfo(
            enabled = true,
            linked = true,
            code = "READY",
            message =
                if (complete && ambiguous == 0 && unknown == 0) {
                    "Compared distinct chapter numbers using stored chapter links."
                } else {
                    "Comparison is uncertain: book numbers may describe volumes or multiple chapters. Unmatched does not necessarily mean missing."
                },
            seriesId = snapshot.series.text("id"),
            title = snapshot.series.obj("metadata").text("title") ?: snapshot.series.text("name"),
            fetchedAt = snapshot.fetchedAt,
            inventoryComplete = complete,
            sourceEntries = chapters.size,
            suwayomiDownloaded = chapters.count { it.downloaded },
            bookCount = snapshot.series.int("booksCount") ?: books.size.takeIf { complete },
            readBooks = snapshot.series.int("booksReadCount") ?: books.count { it.read == true }.takeIf { complete },
            unreadBooks =
                snapshot.series.int(
                    "booksUnreadCount",
                ) ?: books.count { it.read == false && it.page == null }.takeIf { complete },
            inProgressBooks =
                snapshot.series.int("booksInProgressCount") ?: books.count { it.read == false && it.page != null }.takeIf { complete },
            lastProgressAt = books.mapNotNull { it.lastProgressAt }.maxOrNull(),
            confirmedNumbers = sorted(confirmed),
            numberHints = sorted(hints),
            unmatchedSourceNumbers = sorted(missing),
            extraNumberHints = sorted(hints - source),
            confirmedButNotDownloaded = sorted(confirmed - downloaded),
            downloadedButNotMatched = sorted(downloaded - confirmed),
            missingCount = missing.size.takeIf { complete && ambiguous == 0 && unknown == 0 },
            ambiguousBooks = ambiguous,
            unknownSourceEntries = unknown,
            duplicateSourceEntries = chapters.count { it.number != null } - source.size,
            books = books,
        )
    }
}
