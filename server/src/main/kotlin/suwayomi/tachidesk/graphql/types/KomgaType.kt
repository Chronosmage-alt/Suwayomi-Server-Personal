package suwayomi.tachidesk.graphql.types

data class KomgaSettings(
    val enabled: Boolean,
    val apiUrl: String,
    val browserUrl: String,
    val credentialConfigured: Boolean,
    val canManage: Boolean,
)

data class KomgaConnectionResult(
    val success: Boolean,
    val code: String,
    val message: String,
)

data class KomgaSeriesOption(
    val id: String,
    val title: String,
    val alternateTitles: List<String>,
    val bookCount: Int?,
)

data class KomgaSearchPage(
    val items: List<KomgaSeriesOption>,
    val page: Int,
    val hasNextPage: Boolean,
    val code: String,
    val message: String,
)

data class KomgaBookInfo(
    val id: String,
    val title: String,
    val numberHint: String?,
    val confirmedNumbers: List<String>,
    val read: Boolean?,
    val page: Int?,
    val pages: Int?,
    val lastProgressAt: String?,
)

data class KomgaMangaInfo(
    val enabled: Boolean,
    val linked: Boolean,
    val code: String,
    val message: String,
    val seriesId: String? = null,
    val title: String? = null,
    val openUrl: String? = null,
    val fetchedAt: Long? = null,
    val stale: Boolean = false,
    val inventoryComplete: Boolean = false,
    val sourceEntries: Int = 0,
    val suwayomiDownloaded: Int = 0,
    val bookCount: Int? = null,
    val readBooks: Int? = null,
    val unreadBooks: Int? = null,
    val inProgressBooks: Int? = null,
    val lastProgressAt: String? = null,
    val confirmedNumbers: List<String> = emptyList(),
    val numberHints: List<String> = emptyList(),
    val unmatchedSourceNumbers: List<String> = emptyList(),
    val extraNumberHints: List<String> = emptyList(),
    val confirmedButNotDownloaded: List<String> = emptyList(),
    val downloadedButNotMatched: List<String> = emptyList(),
    val missingCount: Int? = null,
    val ambiguousBooks: Int = 0,
    val unknownSourceEntries: Int = 0,
    val duplicateSourceEntries: Int = 0,
    val books: List<KomgaBookInfo> = emptyList(),
)
