package suwayomi.tachidesk.manga.impl.komga

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.yield
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greater
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.graphql.types.KomgaLibrarySummary
import suwayomi.tachidesk.graphql.types.KomgaScanStatus
import suwayomi.tachidesk.manga.model.table.KomgaIndexTable
import suwayomi.tachidesk.manga.model.table.KomgaLinkTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** Demand-driven jobs only. No source clients, scheduler, downloader or remote write operations. */
object KomgaIndex {
    internal const val FRESH_MS = 15 * 60 * 1000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val remotePermit = Semaphore(1)
    private val states = ConcurrentHashMap<Int, KomgaScanStatus>()
    private val attempted = ConcurrentHashMap<Int, Long>()
    private val errors = ConcurrentHashMap<Int, String>()
    private val requestedScans = ConcurrentHashMap<Int, Boolean>()
    private val changedManga = ConcurrentHashMap<Int, MutableSet<Int>>()
    private val overflowedChanges = ConcurrentHashMap.newKeySet<Int>()

    private data class Snapshot(
        val identity: String,
        val credential: String,
        val at: Long,
        val series: List<KomgaIndexedSeries>,
    ) {
        val byId by lazy { series.associateBy { it.id } }
        val byTitle by lazy {
            val result = mutableMapOf<String, MutableList<KomgaIndexedSeries>>()
            series.forEach { entry -> KomgaMatching.titles(entry).forEach { result.getOrPut(it) { mutableListOf() }.add(entry) } }
            result
        }
    }

    // Bounded in-memory hot cache; the database retains one compact snapshot per configured user.
    private val snapshots =
        object : LinkedHashMap<Int, Snapshot>(4, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Snapshot>?): Boolean = size > 4
        }

    private fun credential(): Pair<String, String> {
        val secret = KomgaSecret.read()
        return secret to MessageDigest.getInstance("SHA-256").digest(secret.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun snapshot(userId: Int): Snapshot? =
        synchronized(snapshots) {
            snapshots[userId] ?: transaction {
                KomgaIndexTable.selectAll().where { KomgaIndexTable.user eq userId }.firstOrNull()?.let {
                    runCatching {
                        Snapshot(
                            it[KomgaIndexTable.identity],
                            it[KomgaIndexTable.credential],
                            it[KomgaIndexTable.fetchedAt],
                            Json.decodeFromString<List<KomgaIndexedSeries>>(it[KomgaIndexTable.payload]),
                        )
                    }.getOrNull()
                }
            }?.also { snapshots[userId] = it }
        }

    fun status(userId: Int): KomgaScanStatus = states[userId] ?: KomgaScanStatus()

    internal fun invalidate(userId: Int) {
        synchronized(snapshots) { snapshots.remove(userId) }
        if (!status(userId).running) {
            states.remove(userId)
            attempted.remove(userId)
            errors.remove(userId)
        }
    }

    fun localLibraryChanged(
        userId: Int,
        mangaId: Int,
    ) {
        val config = Komga.config(userId)
        if (config.enabled && config.autoLink) {
            synchronized(this) {
                val pending = changedManga.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }
                if (pending.size < 2000) pending.add(mangaId) else overflowedChanges.add(userId)
                start(userId, scan = false)
            }
        }
    }

    /** Returns immediately; repeated callers join the same bounded job. */
    @Synchronized
    fun start(
        userId: Int,
        scan: Boolean = true,
    ): KomgaScanStatus {
        val config = Komga.config(userId)
        if (!config.enabled) return KomgaScanStatus(code = "DISABLED")
        if (status(userId).running) {
            if (scan) requestedScans[userId] = true
            return status(userId)
        }
        val now = System.currentTimeMillis()
        if (now - (attempted[userId] ?: 0) < 30_000) return status(userId).copy(code = "COOLDOWN")
        if (states.values.count { it.running } >= 4) return KomgaScanStatus(code = "BUSY")
        attempted[userId] = now
        states[userId] = KomgaScanStatus(running = true, code = "INDEXING")
        requestedScans[userId] = scan
        scope.launch {
            try {
                val (secret, fingerprint) = credential()
                var index =
                    snapshot(userId)?.takeIf {
                        it.identity == config.identity && it.credential == fingerprint && now - it.at in 0 until FRESH_MS &&
                            errors[userId] == null
                    }
                val hadFreshIndex = index != null
                if (index == null) {
                    index = remotePermit.withPermit { fetch(userId, config, secret, fingerprint) }
                }
                errors.remove(userId)
                val work =
                    synchronized(this@KomgaIndex) {
                        val pending = changedManga.remove(userId)?.toList().orEmpty()
                        val overflow = overflowedChanges.remove(userId)
                        val full =
                            requestedScans.remove(userId) == true ||
                                (config.autoLink && (!hadFreshIndex || overflow))
                        val requested = full || (config.autoLink && pending.isNotEmpty())
                        states.computeIfPresent(
                            userId,
                        ) { _, state -> state.copy(running = requested, code = if (requested) "MATCHING" else "COMPLETE") }
                        requested to (if (full) null else pending)
                    }
                if (work.first) scan(userId, config, index, work.second)
                val completeLibrary =
                    synchronized(this@KomgaIndex) {
                        val requested = requestedScans.remove(userId) == true && work.second != null
                        if (!requested) states.computeIfPresent(userId) { _, state -> state.copy(running = false, code = "COMPLETE") }
                        requested
                    }
                // A manual request joining an incremental job still gets a complete library scan.
                if (completeLibrary) {
                    scan(userId, config, index, null)
                    states.computeIfPresent(userId) { _, state -> state.copy(running = false, code = "COMPLETE") }
                }
            } catch (failure: Exception) {
                val code = (failure as? KomgaFailure)?.code ?: "INDEX_FAILED"
                errors[userId] = code
                attempted[userId] =
                    System.currentTimeMillis() + ((failure as? KomgaFailure)?.retryAfterMs ?: 30_000).coerceAtLeast(30_000) - 30_000
                states.computeIfPresent(userId) { _, state -> state.copy(running = false, code = code) }
            }
        }
        return status(userId)
    }

    private suspend fun fetch(
        userId: Int,
        config: KomgaConfig,
        secret: String,
        fingerprint: String,
    ): Snapshot {
        val client = KomgaClient(config.apiUrl, secret)
        val started = System.currentTimeMillis()
        val entries = mutableListOf<KomgaIndexedSeries>()
        val ids = mutableSetOf<String>()
        var first: KomgaPage? = null
        var bytes = 0
        for (page in 0 until 250) {
            if (page % 5 == 0 &&
                (Komga.config(userId) != config || credential().second != fingerprint)
            ) {
                throw KomgaFailure("CONFIG_CHANGED")
            }
            if (System.currentTimeMillis() - started > 120_000) throw KomgaFailure("INDEX_LIMIT")
            val response = client.indexPage(page)
            bytes += response.toString().toByteArray().size
            if (bytes > 32 * 1024 * 1024) throw KomgaFailure("INDEX_LIMIT")
            val parsed = KomgaPagination.read(response, page, 200, requireSorted = true)
            // An authoritative total is necessary to establish completeness for automatic matches.
            if (parsed.totalElements == null || parsed.totalPages == null || parsed.size != 200) throw KomgaFailure("INVALID_RESPONSE")
            if (first == null) first = parsed
            if (parsed.totalElements != first.totalElements || parsed.totalPages != first.totalPages) throw KomgaFailure("INVALID_RESPONSE")
            parsed.items.forEach { series ->
                val id =
                    series.text("id")?.takeIf { it.length in 1..256 && it.isNotBlank() && it.none { char -> char.code < 32 } }
                        ?: throw KomgaFailure("INVALID_RESPONSE")
                if (!ids.add(id)) throw KomgaFailure("INVALID_RESPONSE")
                if (series.bool("deleted") != true) {
                    if (series["metadata"] != null && series["metadata"] !is JsonObject) throw KomgaFailure("INVALID_RESPONSE")
                    val metadata = series.obj("metadata")
                    if (series["booksMetadata"] != null && series["booksMetadata"] !is JsonObject) throw KomgaFailure("INVALID_RESPONSE")
                    val title = metadata.text("title") ?: series.text("name") ?: throw KomgaFailure("INVALID_RESPONSE")
                    if (title.isBlank() || title.length > 2048) throw KomgaFailure("INVALID_RESPONSE")

                    fun strings(
                        container: JsonObject,
                        key: String,
                        field: String,
                    ): List<String> {
                        val values = container[key] ?: return emptyList()
                        if (values !is JsonArray || values.size > 200) throw KomgaFailure("INVALID_RESPONSE")
                        return values.map {
                            val value = (it as? JsonObject)?.text(field) ?: throw KomgaFailure("INVALID_RESPONSE")
                            if (value.isBlank() || value.length > 2048) throw KomgaFailure("INVALID_RESPONSE")
                            value
                        }
                    }
                    val count = series.int("booksCount")
                    if (count != null && count < 0) throw KomgaFailure("INVALID_RESPONSE")
                    entries.add(
                        KomgaIndexedSeries(
                            id,
                            title,
                            strings(metadata, "alternateTitles", "title"),
                            strings(metadata, "authors", "name") + strings(series.obj("booksMetadata"), "authors", "name"),
                            strings(metadata, "links", "url"),
                            count,
                        ),
                    )
                }
            }
            states.computeIfPresent(userId) { _, state -> state.copy(indexedSeries = ids.size) }
            if (!parsed.hasNext) {
                if (ids.size != parsed.totalElements) throw KomgaFailure("INVALID_RESPONSE")
                val snapshot = Snapshot(config.identity, fingerprint, System.currentTimeMillis(), entries)
                Komga.mutex.withLock {
                    if (Komga.config(userId) != config || credential().second != fingerprint) throw KomgaFailure("CONFIG_CHANGED")
                    transaction {
                        KomgaIndexTable.upsert(KomgaIndexTable.user) {
                            it[user] = EntityID(userId, UserAccountTable)
                            it[identity] = config.identity
                            it[credential] = fingerprint
                            it[fetchedAt] = snapshot.at
                            it[payload] = Json.encodeToString(entries)
                        }
                    }
                    synchronized(snapshots) { snapshots[userId] = snapshot }
                }
                return snapshot
            }
            if (page % 5 == 4) yield()
        }
        throw KomgaFailure("INDEX_LIMIT")
    }

    private suspend fun scan(
        userId: Int,
        config: KomgaConfig,
        index: Snapshot,
        mangaIds: List<Int>?,
    ) {
        val total =
            transaction {
                MangaUserTable
                    .selectAll()
                    .where {
                        (MangaUserTable.user eq userId) and (MangaUserTable.inLibrary eq true) and
                            (mangaIds?.let { MangaUserTable.manga inList it } ?: Op.TRUE)
                    }.count()
                    .toInt()
            }
        states[userId] = KomgaScanStatus(running = true, code = "MATCHING", total = total, indexedSeries = index.series.size)
        var lastId = 0
        while (status(userId).processed < total) {
            val rows =
                transaction {
                    (MangaTable innerJoin MangaUserTable)
                        .selectAll()
                        .where {
                            (MangaUserTable.user eq userId) and (MangaUserTable.inLibrary eq true) and
                                (MangaTable.id greater lastId) and (mangaIds?.let { MangaTable.id inList it } ?: Op.TRUE)
                        }.orderBy(MangaTable.id)
                        .limit(minOf(200, total - status(userId).processed))
                        .toList()
                }
            if (rows.isEmpty()) break
            Komga.mutex.withLock {
                if (Komga.config(userId) != config || credential().second != index.credential ||
                    System.currentTimeMillis() - index.at !in 0 until FRESH_MS
                ) {
                    throw KomgaFailure("CONFIG_CHANGED")
                }
                transaction {
                    val decisions =
                        KomgaLinkTable
                            .selectAll()
                            .where {
                                (KomgaLinkTable.user eq userId) and (KomgaLinkTable.manga inList rows.map { it[MangaTable.id].value })
                            }.associateBy { it[KomgaLinkTable.manga].value }
                    rows.forEach { row ->
                        val mangaId = row[MangaTable.id].value
                        val existing = decisions[mangaId]
                        val outcome =
                            if (existing != null) {
                                if (existing[KomgaLinkTable.provenance] == "OPTED_OUT") "OPTED_OUT" else "LINKED"
                            } else {
                                val result =
                                    KomgaMatching.match(
                                        row[MangaTable.title],
                                        listOfNotNull(row[MangaTable.author], row[MangaTable.artist]),
                                        row[MangaTable.realUrl],
                                        index.byTitle[KomgaMatching.normalize(row[MangaTable.title])].orEmpty(),
                                    )
                                result.second?.let { candidate ->
                                    // Insert-if-absent shares restore's atomic precedence; never overwrite a decision.
                                    Komga.restoreLink(
                                        userId,
                                        mangaId,
                                        suwayomi.tachidesk.manga.impl.backup.proto.models.BackupKomgaLink(
                                            candidate.id,
                                            candidate.title,
                                            config.identity,
                                            "AUTOMATIC",
                                        ),
                                        confirmedIdentity = config.identity,
                                    )
                                }
                                result.first
                            }
                        states.computeIfPresent(userId) { _, state ->
                            state.copy(
                                processed = state.processed + 1,
                                automaticallyLinked = state.automaticallyLinked + if (outcome == "MATCH") 1 else 0,
                                alreadyLinked = state.alreadyLinked + if (outcome == "LINKED") 1 else 0,
                                ambiguous = state.ambiguous + if (outcome in setOf("AMBIGUOUS", "REVIEW")) 1 else 0,
                                optedOut = state.optedOut + if (outcome == "OPTED_OUT") 1 else 0,
                                unmatched = state.unmatched + if (outcome == "UNMATCHED") 1 else 0,
                            )
                        }
                    }
                }
            }
            lastId = rows.last()[MangaTable.id].value
            yield()
        }
    }

    fun summaries(
        userId: Int,
        mangaIds: List<Int>,
    ): List<KomgaLibrarySummary> {
        require(mangaIds.size <= 200) { "At most 200 manga per summary batch." }
        if (mangaIds.isEmpty()) return emptyList()
        val config = Komga.config(userId)
        if (!config.enabled || (!config.showCounter && !config.autoLink)) return emptyList()
        val fingerprint = runCatching { credential().second }.getOrNull() ?: return emptyList()
        val index = snapshot(userId)?.takeIf { it.identity == config.identity && it.credential == fingerprint }
        val stale = index == null || System.currentTimeMillis() - index.at !in 0 until FRESH_MS || errors[userId] != null
        val links =
            transaction {
                KomgaLinkTable
                    .selectAll()
                    .where { (KomgaLinkTable.user eq userId) and (KomgaLinkTable.manga inList mangaIds.distinct()) }
                    .associateBy { it[KomgaLinkTable.manga].value }
            }
        val needsIndex =
            config.autoLink ||
                links.values.any {
                    it[KomgaLinkTable.identity] == config.identity && it[KomgaLinkTable.provenance] != "OPTED_OUT"
                }
        if ((stale && needsIndex) || (config.autoLink && changedManga[userId]?.isNotEmpty() == true)) start(userId, scan = false)
        if (!config.showCounter) return emptyList()
        val local =
            transaction {
                MangaTable.selectAll().where { MangaTable.id inList mangaIds.distinct() }.associateBy { it[MangaTable.id].value }
            }
        return mangaIds.distinct().map { id ->
            val link = links[id]
            val provenance = link?.get(KomgaLinkTable.provenance) ?: "UNLINKED"
            val valid = link != null && link[KomgaLinkTable.identity] == config.identity && provenance != "OPTED_OUT"
            val series = if (valid) index?.byId?.get(link[KomgaLinkTable.series]) else null
            val hidden = errors[userId] in setOf("AUTH_FAILED", "ACCESS_DENIED")
            val row = local[id]
            val match =
                if (row != null && index != null && !stale) {
                    KomgaMatching.match(
                        row[MangaTable.title],
                        listOfNotNull(row[MangaTable.author], row[MangaTable.artist]),
                        row[MangaTable.realUrl],
                        index.byTitle[KomgaMatching.normalize(row[MangaTable.title])].orEmpty(),
                    )
                } else {
                    null
                }
            val review = provenance == "AUTOMATIC" && match != null && (match.first != "MATCH" || match.second?.id != series?.id)
            val code =
                when {
                    link == null -> {
                        when {
                            match?.first in setOf("AMBIGUOUS", "REVIEW") -> "REVIEW"
                            status(userId).running -> "LOADING"
                            else -> "UNLINKED"
                        }
                    }

                    provenance == "OPTED_OUT" -> {
                        "OPTED_OUT"
                    }

                    !valid -> {
                        "REVIEW"
                    }

                    hidden -> {
                        "ACCESS_DENIED"
                    }

                    review -> {
                        "REVIEW"
                    }

                    series == null -> {
                        if (index == null) "LOADING" else "REVIEW"
                    }

                    stale -> {
                        "STALE"
                    }

                    else -> {
                        "READY"
                    }
                }
            KomgaLibrarySummary(
                id,
                provenance,
                code,
                if (hidden ||
                    review
                ) {
                    null
                } else {
                    series?.books
                },
                index?.at,
                stale && (valid || config.autoLink),
            )
        }
    }
}
