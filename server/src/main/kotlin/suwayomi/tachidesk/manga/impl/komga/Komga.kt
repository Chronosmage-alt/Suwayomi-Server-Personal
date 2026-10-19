package suwayomi.tachidesk.manga.impl.komga

import io.github.reactivecircus.cache4k.Cache
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.statements.UpdateBuilder
import org.jetbrains.exposed.v1.core.vendors.H2Dialect
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertIgnore
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.graphql.types.KomgaConnectionResult
import suwayomi.tachidesk.graphql.types.KomgaMangaInfo
import suwayomi.tachidesk.graphql.types.KomgaSearchPage
import suwayomi.tachidesk.graphql.types.KomgaSeriesOption
import suwayomi.tachidesk.graphql.types.KomgaSettings
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.KomgaConnectionTable
import suwayomi.tachidesk.manga.model.table.KomgaLinkTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.getWithUserData
import suwayomi.tachidesk.server.user.UserPermission
import suwayomi.tachidesk.server.user.hasPermission
import java.security.MessageDigest
import java.util.UUID
import kotlin.time.Duration.Companion.minutes

internal data class KomgaConfig(
    val enabled: Boolean = false,
    val apiUrl: String = "",
    val browserUrl: String = "",
    val identity: String = "",
)

internal data class KomgaBinding(
    val identity: String,
    val series: String,
    val title: String,
)

/** Database writes are limited to these integration tables. No source, download or tracker updater dependencies. */
object Komga {
    private val mutex = Mutex()

    private data class Cached(
        val snapshot: KomgaSnapshot?,
        val attemptedAt: Long,
        val error: String? = null,
    )

    private val cache =
        Cache
            .Builder<String, Cached>()
            .maximumCacheSize(16)
            .expireAfterAccess(15.minutes)
            .build()

    internal fun config(userId: Int): KomgaConfig =
        transaction {
            KomgaConnectionTable.selectAll().where { KomgaConnectionTable.user eq userId }.firstOrNull()?.let {
                KomgaConfig(
                    it[KomgaConnectionTable.enabled],
                    it[KomgaConnectionTable.apiUrl],
                    it[KomgaConnectionTable.browserUrl],
                    it[KomgaConnectionTable.identity],
                )
            } ?: KomgaConfig()
        }

    internal fun binding(
        userId: Int,
        mangaId: Int,
    ): KomgaBinding? =
        transaction {
            KomgaLinkTable.selectAll().where { (KomgaLinkTable.user eq userId) and (KomgaLinkTable.manga eq mangaId) }.firstOrNull()?.let {
                KomgaBinding(it[KomgaLinkTable.identity], it[KomgaLinkTable.series], it[KomgaLinkTable.title])
            }
        }

    fun settings(userId: Int): KomgaSettings {
        val config = config(userId)
        return KomgaSettings(
            config.enabled,
            config.apiUrl,
            config.browserUrl,
            runCatching {
                KomgaSecret.read()
            }.isSuccess,
            hasPermission(userId, UserPermission.MANAGE_SETTINGS),
        )
    }

    suspend fun configure(
        userId: Int,
        enabled: Boolean,
        apiUrl: String,
        browserUrl: String,
    ): KomgaSettings =
        mutex.withLock {
            val api = KomgaClient.normalizeUrl(apiUrl, !enabled)
            val browser = KomgaClient.normalizeUrl(browserUrl, true)
            val old = config(userId)
            transaction {
                KomgaConnectionTable.upsert(KomgaConnectionTable.user) {
                    it[user] = EntityID(userId, UserAccountTable)
                    it[KomgaConnectionTable.enabled] = enabled
                    it[KomgaConnectionTable.apiUrl] = api
                    it[KomgaConnectionTable.browserUrl] = browser
                    it[identity] = if (old.apiUrl == api && old.identity.isNotEmpty()) old.identity else UUID.randomUUID().toString()
                }
            }
            cache.invalidateAll()
            settings(userId)
        }

    private fun failure(code: String) =
        KomgaConnectionResult(
            false,
            code,
            when (code) {
                "SECRET_UNAVAILABLE" -> {
                    "The mounted API-key secret is missing, unreadable or invalid."
                }

                "AUTH_FAILED" -> {
                    "Komga rejected the API key (401)."
                }

                "ACCESS_DENIED" -> {
                    "The Komga user cannot access this resource (403)."
                }

                "NOT_FOUND" -> {
                    "Komga API or linked series not found (404/405). Check the base URL or correct the link."
                }

                "REDIRECT" -> {
                    "Komga redirected the request. Configure its final API base URL. Credentials were not forwarded."
                }

                "RATE_LIMITED" -> {
                    "Komga is rate limiting requests. Try again later."
                }

                "UNREACHABLE" -> {
                    "Komga could not be reached. Check Docker DNS, routing/firewall, TLS and port " +
                        "from Suwayomi's network namespace."
                }

                "CONFIG_CHANGED" -> {
                    "Settings changed during the request. Retry using the current connection."
                }

                else -> {
                    "Komga returned an unsupported, oversized or unsuccessful response."
                }
            },
        )

    suspend fun testConnection(userId: Int): KomgaConnectionResult =
        mutex.withLock {
            val config = config(userId)
            if (config.apiUrl.isBlank()) {
                return@withLock KomgaConnectionResult(
                    false,
                    "NOT_CONFIGURED",
                    "Save an internal Komga API URL first.",
                )
            }
            try {
                KomgaClient(config.apiUrl, KomgaSecret.read()).test()
                KomgaConnectionResult(true, "CONNECTED", "Authenticated from the Suwayomi server network context.")
            } catch (error: KomgaFailure) {
                failure(error.code)
            }
        }

    suspend fun search(
        userId: Int,
        query: String,
        page: Int,
    ): KomgaSearchPage =
        mutex.withLock {
            require(query.trim().length in 2..200 && page in 0..1000) { "Use a search of 2–200 characters and a valid page." }
            val config = config(userId)
            if (!config.enabled) return@withLock KomgaSearchPage(emptyList(), page, false, "DISABLED", "Komga integration is disabled.")
            try {
                val result = KomgaClient(config.apiUrl, KomgaSecret.read()).search(query.trim(), page)
                val parsed = KomgaPagination.read(result, page, 20)
                val items =
                    parsed.items.filter { it.bool("deleted") != true }.map { series ->
                        KomgaSeriesOption(
                            series.text("id") ?: throw KomgaFailure("INVALID_RESPONSE"),
                            series.obj("metadata").text("title") ?: series.text("name").orEmpty(),
                            series.obj("metadata").objects("alternateTitles").mapNotNull { it.text("title") },
                            series.int("booksCount"),
                        )
                    }
                KomgaSearchPage(items, page, parsed.hasNext, "READY", "Suggestions require manual confirmation.")
            } catch (
                error: KomgaFailure,
            ) {
                val result = failure(error.code)
                KomgaSearchPage(emptyList(), page, false, result.code, result.message)
            }
        }

    suspend fun link(
        userId: Int,
        mangaId: Int,
        seriesId: String,
    ): KomgaMangaInfo {
        require(seriesId.length in 1..256 && seriesId.none { it.code < 32 }) { "Invalid series ID." }
        mutex.withLock {
            requireManga(mangaId)
            val config = config(userId)
            require(config.enabled) { "Enable Komga first." }
            val series =
                try {
                    KomgaClient(config.apiUrl, KomgaSecret.read()).series(seriesId)
                } catch (error: KomgaFailure) {
                    return KomgaMangaInfo(true, binding(userId, mangaId) != null, error.code, failure(error.code).message)
                }
            transaction {
                KomgaLinkTable.upsert(KomgaLinkTable.user, KomgaLinkTable.manga) {
                    it[user] = EntityID(userId, UserAccountTable)
                    it[manga] = EntityID(mangaId, MangaTable)
                    it[identity] = config.identity
                    it[KomgaLinkTable.series] = seriesId
                    it[title] = series.obj("metadata").text("title") ?: series.text("name").orEmpty()
                }
            }
        }
        return manga(userId, mangaId)
    }

    suspend fun unlink(
        userId: Int,
        mangaId: Int,
    ): KomgaMangaInfo {
        mutex.withLock { transaction { KomgaLinkTable.deleteWhere { (user eq userId) and (manga eq mangaId) } } }
        return manga(userId, mangaId)
    }

    fun backupConnection(userId: Int): suwayomi.tachidesk.manga.impl.backup.proto.models.BackupKomgaConnection? =
        config(userId).takeIf { it.apiUrl.isNotEmpty() }?.let {
            suwayomi.tachidesk.manga.impl.backup.proto.models
                .BackupKomgaConnection(it.apiUrl, it.browserUrl, it.identity)
        }

    fun backupLink(
        userId: Int,
        mangaId: Int,
    ): suwayomi.tachidesk.manga.impl.backup.proto.models.BackupKomgaLink? =
        binding(userId, mangaId)?.let {
            suwayomi.tachidesk.manga.impl.backup.proto.models
                .BackupKomgaLink(it.series, it.title, it.identity)
        }

    fun restoreConnection(
        userId: Int,
        backup: suwayomi.tachidesk.manga.impl.backup.proto.models.BackupKomgaConnection,
    ) {
        val api = runCatching { KomgaClient.normalizeUrl(backup.apiUrl) }.getOrNull() ?: return
        val browser = runCatching { KomgaClient.normalizeUrl(backup.browserUrl, true) }.getOrNull() ?: return
        transaction {
            KomgaConnectionTable.upsert(KomgaConnectionTable.user) {
                it[user] = EntityID(userId, UserAccountTable)
                it[enabled] = false
                it[apiUrl] = api
                it[browserUrl] = browser
                it[identity] = UUID.randomUUID().toString()
            }
        }
        cache.invalidateAll()
    }

    fun restoreLink(
        userId: Int,
        mangaId: Int,
        backup: suwayomi.tachidesk.manga.impl.backup.proto.models.BackupKomgaLink,
    ) {
        if (backup.seriesId.length !in 1..256) return
        transaction {
            // The unique primary key decides atomically; restoration must never update a confirmed link.
            val values: (UpdateBuilder<*>) -> Unit = {
                it[KomgaLinkTable.user] = EntityID(userId, UserAccountTable)
                it[KomgaLinkTable.manga] = EntityID(mangaId, MangaTable)
                it[KomgaLinkTable.series] = backup.seriesId
                it[KomgaLinkTable.title] = backup.title.take(2048)
                it[KomgaLinkTable.identity] = "restored"
            }
            if (db.dialect is H2Dialect) {
                // H2 has no INSERT IGNORE. Excluding every update column emits only WHEN NOT MATCHED.
                KomgaLinkTable.upsert(
                    KomgaLinkTable.user,
                    KomgaLinkTable.manga,
                    onUpdateExclude = KomgaLinkTable.columns,
                ) { values(it) }
            } else {
                // PostgreSQL emits INSERT ... ON CONFLICT DO NOTHING.
                KomgaLinkTable.insertIgnore { values(it) }
            }
        }
    }

    private fun requireManga(mangaId: Int) =
        transaction {
            require(MangaTable.selectAll().where { MangaTable.id eq mangaId }.any()) { "Manga not found." }
        }

    private fun fetch(
        client: KomgaClient,
        seriesId: String,
    ): KomgaSnapshot {
        val started = System.currentTimeMillis()
        val series = client.series(seriesId)
        if (series.toString().length > 65_536) throw KomgaFailure("RESPONSE_TOO_LARGE")
        val books = mutableListOf<kotlinx.serialization.json.JsonObject>()
        var characters = 0
        val seen = mutableSetOf<String>()
        var complete = false
        var firstPage: KomgaPage? = null
        for (page in 0 until 10) {
            if (System.currentTimeMillis() - started > 20_000) break
            val result = client.books(seriesId, page)
            val parsed = KomgaPagination.read(result, page, 200, requireSorted = true)
            val items = parsed.items
            if (firstPage == null) {
                firstPage = parsed
            } else if (parsed.totalElements != firstPage.totalElements ||
                parsed.totalPages != firstPage.totalPages || parsed.size != firstPage.size
            ) {
                throw KomgaFailure("INVALID_RESPONSE")
            }
            items.forEach { book ->
                val id = book.text("id") ?: throw KomgaFailure("INVALID_RESPONSE")
                if (id.isBlank() || !seen.add(id) || book.text("seriesId") != seriesId) throw KomgaFailure("INVALID_RESPONSE")
                characters += book.toString().length
                if (characters > 2_000_000) return KomgaSnapshot(series, books, false, System.currentTimeMillis())
                books.add(book)
            }
            if (!parsed.hasNext) {
                if (parsed.totalElements != null && parsed.totalElements != books.size) throw KomgaFailure("INVALID_RESPONSE")
                complete = true
                break
            }
            if (items.isEmpty()) break
        }
        return KomgaSnapshot(series, books, complete, System.currentTimeMillis())
    }

    suspend fun manga(
        userId: Int,
        mangaId: Int,
        refresh: Boolean = false,
    ): KomgaMangaInfo =
        mutex.withLock {
            requireManga(mangaId)
            val config = config(userId)
            val binding = binding(userId, mangaId)
            if (!config.enabled || binding == null || binding.identity != config.identity) {
                return@withLock KomgaMangaInfo(
                    config.enabled,
                    binding != null,
                    if (!config.enabled) {
                        "DISABLED"
                    } else if (binding == null) {
                        "UNLINKED"
                    } else {
                        "CONNECTION_CHANGED"
                    },
                    if (!config.enabled) {
                        "Komga integration is disabled."
                    } else if (binding ==
                        null
                    ) {
                        "Link a Komga series to compare inventory."
                    } else {
                        "The API destination changed. Confirm the series again."
                    },
                    seriesId = binding?.series,
                    title = binding?.title,
                )
            }
            val now = System.currentTimeMillis()
            var cached: Cached? = null
            var error: String? = null
            try {
                val key = KomgaSecret.read()
                val fingerprint = MessageDigest.getInstance("SHA-256").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }
                val cacheKey = "$userId:${config.identity}:$fingerprint:${binding.series}"
                cached = cache.get(cacheKey)
                val age = now - (cached?.attemptedAt ?: 0)
                if (cached == null || age >= (
                        if (cached.error != null) {
                            10_000
                        } else if (refresh) {
                            2_000
                        } else {
                            60_000
                        }
                    )
                ) {
                    try {
                        cached = Cached(fetch(KomgaClient(config.apiUrl, key), binding.series), now)
                    } catch (failure: KomgaFailure) {
                        cached = Cached(cached?.snapshot, now, failure.code)
                    }
                    cache.put(cacheKey, cached)
                }
                error = cached.error
            } catch (failure: KomgaFailure) {
                error = failure.code
            }
            val snapshot = cached?.snapshot
            if (snapshot == null || error in listOf("AUTH_FAILED", "ACCESS_DENIED", "NOT_FOUND")) {
                return@withLock KomgaMangaInfo(
                    true,
                    true,
                    error ?: "REMOTE_ERROR",
                    failure(error ?: "REMOTE_ERROR").message,
                    binding.series,
                    binding.title,
                )
            }
            val chapters =
                transaction {
                    ChapterTable.getWithUserData(userId).selectAll().where { ChapterTable.manga eq mangaId }.map {
                        KomgaLocalChapter(
                            KomgaInventory.number(it[ChapterTable.chapter_number].toString()),
                            listOfNotNull(it[ChapterTable.realUrl], it[ChapterTable.url]),
                            it.getOrNull(ChapterUserTable.isDownloaded) == true,
                        )
                    }
                }
            val comparison = KomgaInventory.compare(snapshot, chapters)
            comparison.copy(
                openUrl =
                    config.browserUrl.takeIf { it.isNotBlank() }?.let {
                        "$it/series/${java.net.URLEncoder.encode(
                            binding.series,
                            Charsets.UTF_8,
                        )}"
                    },
                stale = error != null,
                code = if (error == null) "READY" else "STALE",
                message = if (error == null) comparison.message else "${failure(error).message} Showing cached inventory.",
            )
        }
}
