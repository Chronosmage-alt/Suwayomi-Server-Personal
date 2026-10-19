package suwayomi.tachidesk.manga.impl.komga

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupExport
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.KomgaConnectionTable
import suwayomi.tachidesk.manga.model.table.KomgaIndexTable
import suwayomi.tachidesk.manga.model.table.KomgaLinkTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.test.ApplicationTest
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class KomgaTest : ApplicationTest() {
    private lateinit var server: HttpServer
    private lateinit var secret: java.nio.file.Path
    private var oldSecret: String? = null
    private var mangaId = 0
    private var chapterId = 0
    private val requests = CopyOnWriteArrayList<String>()
    private var status = 200
    private var malformed = false
    private var legacy = false
    private var postOnly = false
    private var secondPage = false
    private var invalidBooks = false
    private var dropNextRequest = false
    private var changingTotals = false
    private var repeatedBook = false
    private var indexInvalid = false
    private var invalidIndexId = false
    private var duplicateTitle = false
    private var deletedSeries = false
    private var largeIndex = false
    private val extraManga = mutableListOf<Int>()
    private val api get() = "http://127.0.0.1:${server.address.port}/komga"

    @BeforeEach fun setup() {
        oldSecret = System.getProperty("suwayomi.komga.apiKeyFile")
        secret = Files.createTempFile("komga-fixture-", ".txt")
        Files.writeString(secret, "test-only-key")
        System.setProperty("suwayomi.komga.apiKeyFile", secret.toString())
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/komga/") { exchange ->
            requests.add("${exchange.requestMethod} ${exchange.requestURI}")
            if (dropNextRequest) {
                dropNextRequest = false
                exchange.close()
                return@createContext
            }
            val path = exchange.requestURI.path
            val page =
                exchange.requestURI.query
                    .orEmpty()
                    .contains("page=1")
            var code = status
            if (exchange.requestURI.query
                    .orEmpty()
                    .contains("size=200") &&
                (path.endsWith("/series") || path.endsWith("/series/list")) &&
                !exchange.requestURI.query
                    .orEmpty()
                    .contains("sort=name,asc")
            ) {
                code = 400
            }
            if (exchange.requestHeaders.getFirst("X-API-Key") != Files.readString(secret).trim()) code = 401
            if (legacy && path == "/komga/api/v2/users/me") code = 404
            if (postOnly && exchange.requestMethod == "GET" && (path.endsWith("/books") || path.endsWith("/series"))) code = 405
            val content =
                when {
                    malformed -> {
                        "<html>not an API</html>"
                    }

                    invalidBooks && path.endsWith("/books") -> {
                        """{"content":[null],"last":true}"""
                    }

                    code != 200 -> {
                        """{"error":"test-only-key must not be echoed"}"""
                    }

                    exchange.requestURI.query
                        .orEmpty()
                        .contains("size=200") && path.contains("/series") && !path.endsWith("/books") -> {
                        if (largeIndex) {
                            val content =
                                (if (page) 200..200 else 0..199).joinToString(",") {
                                    """{"id":"series-$it","name":"Item $it","booksCount":$it}"""
                                }
                            """{"content":[$content],"last":$page,"totalPages":2,"totalElements":201,"size":200,"number":${if (page) 1 else 0}}"""
                        } else {
                            val count = if (duplicateTitle) 2 else 1
                            """{"content":[{"id":"${if (invalidIndexId) " " else "s"}","name":"Fixture","booksCount":7,"deleted":$deletedSeries}
                        ${if (duplicateTitle) ",{\"id\":\"other\",\"name\":\"Fixture\",\"booksCount\":2}" else ""}],
                        "last":true,"totalPages":1,"totalElements":${if (indexInvalid) 99 else count},"size":200,"number":0}"""
                        }
                    }

                    path.endsWith("/me") -> {
                        """{"id":"reader","email":"private@example.invalid"}"""
                    }

                    path.endsWith(
                        "/series/s",
                    ) -> {
                        """{
                        "id":"s","name":"Renamed fixture","booksCount":${if (secondPage) 2 else 1},
                        "booksReadCount":1,"booksUnreadCount":0,"booksInProgressCount":0
                    }"""
                    }

                    path.endsWith(
                        "/books",
                    ) ||
                        path.endsWith(
                            "/books/list",
                        )
                    -> {
                        """{
                        "content":[{"id":"b${if (page && !repeatedBook) 2 else 1}","seriesId":"s",
                        "metadata":{"title":"Chapter","number":"${if (page) 2 else 1}",
                        "links":[{"url":"https://source.invalid/${if (page) 2 else 1}"}]},
                        "readProgress":{"completed":true,"page":20}}],
                        "last":${!secondPage || page},"totalPages":${if (secondPage) 2 else 1}
                        ${if (changingTotals) ",\"totalElements\":${if (page) 3 else 2}" else ""}
                    }"""
                    }

                    else -> {
                        """{
                        "content":[{"id":"s","name":"Fixture","booksCount":1,
                        "metadata":{"alternateTitles":[{"title":"Alias"}]}}],"last":true,"totalPages":1
                    }"""
                    }
                }
            exchange.requestBody.use { it.readBytes() }
            val bytes = content.toByteArray()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(code, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        mangaId =
            transaction {
                MangaTable
                    .insertAndGetId {
                        it[url] = "/komga-${System.nanoTime()}"
                        it[title] = "Komga fixture"
                        it[sourceReference] = 0
                    }.value
            }
        chapterId =
            transaction {
                val id =
                    ChapterTable
                        .insertAndGetId {
                            it[manga] = mangaId
                            it[url] = "/chapter/1"
                            it[realUrl] = "https://source.invalid/1"
                            it[name] = "Chapter 1"
                            it[sourceOrder] = 0
                            it[chapter_number] = 1f
                        }.value
                ChapterUserTable.upsert(ChapterUserTable.chapter, ChapterUserTable.user) {
                    it[chapter] = id
                    it[user] = 1
                    it[isDownloaded] = false
                    it[isRead] = false
                    it[isBookmarked] = true
                    it[lastPageRead] = 7
                }
                id
            }
    }

    @AfterEach fun clean() {
        server.stop(0)
        transaction {
            KomgaConnectionTable.deleteWhere { user eq 1 }
            MangaTable.deleteWhere { MangaTable.id eq mangaId }
            MangaTable.deleteWhere { MangaTable.id inList extraManga }
        }
        if (oldSecret ==
            null
        ) {
            System.clearProperty("suwayomi.komga.apiKeyFile")
        } else {
            System.setProperty("suwayomi.komga.apiKeyFile", oldSecret!!)
        }
        Files.deleteIfExists(secret)
    }

    private suspend fun enable() = Komga.configure(1, true, api, "http://nas.invalid:8080/reader")

    private fun libraryFixture() =
        transaction {
            MangaTable.update({ MangaTable.id eq mangaId }) { it[title] = "  FIXTURE  " }
            MangaUserTable.upsert(MangaUserTable.manga, MangaUserTable.user) {
                it[manga] = mangaId
                it[user] = 1
                it[inLibrary] = true
            }
        }

    private suspend fun scan(): suwayomi.tachidesk.graphql.types.KomgaScanStatus {
        KomgaIndex.start(1)
        withTimeout(10_000) { while (KomgaIndex.status(1).running) delay(10) }
        return KomgaIndex.status(1)
    }

    @Test fun sharedIndexScanUsesOneRequestAndSummariesNeverFetchInventories() =
        runBlocking<Unit> {
            enable()
            libraryFixture()
            assertFalse(Komga.settings(1).autoLink)
            assertTrue(Komga.settings(1).showCounter)
            (1..20).map { async(kotlinx.coroutines.Dispatchers.IO) { KomgaIndex.start(1) } }.awaitAll()
            val result = scan()
            assertEquals("COMPLETE", result.code)
            assertEquals(1, result.automaticallyLinked)
            assertEquals("AUTOMATIC", Komga.binding(1, mangaId)?.provenance)
            val count = requests.size
            repeat(20) {
                val summary = KomgaIndex.summaries(1, List(200) { mangaId }).single()
                assertEquals(7, summary.bookCount)
                assertFalse(summary.stale)
            }
            assertEquals(count, requests.size)
            assertEquals(1, count)
            assertFailsWith<IllegalArgumentException> { KomgaIndex.summaries(1, List(201) { mangaId }) }
        }

    @Test fun duplicateOrInvalidIndexNeverCreatesLinks() =
        runBlocking {
            enable()
            libraryFixture()
            duplicateTitle = true
            assertEquals(1, scan().ambiguous)
            assertEquals(null, Komga.binding(1, mangaId))
            transaction { KomgaIndexTable.deleteWhere { user eq 1 } }
            KomgaIndex.invalidate(1)
            indexInvalid = true
            assertEquals("INVALID_RESPONSE", scan().code)
            assertEquals(null, Komga.binding(1, mangaId))
        }

    @Test fun permanentUnlinkOptOutSurvivesRestoreAndFreshScans() =
        runBlocking {
            enable()
            libraryFixture()
            Komga.unlink(1, mangaId)
            val backup = assertNotNull(Komga.backupLink(1, mangaId))
            assertEquals("OPTED_OUT", backup.provenance)
            Komga.restoreLink(1, mangaId, backup.copy(seriesId = "s", provenance = "AUTOMATIC"))
            assertEquals(1, scan().optedOut)
            assertEquals(null, Komga.binding(1, mangaId))
            KomgaIndex.invalidate(1)
            assertEquals(1, scan().optedOut)
            Komga.link(1, mangaId, "s")
            assertEquals("MANUAL", Komga.binding(1, mangaId)?.provenance)
            Komga.restoreLink(1, mangaId, backup)
            assertEquals("MANUAL", Komga.binding(1, mangaId)?.provenance)
        }

    @Test fun malformedSeriesIdentityCannotBeAutomaticallyLinked() =
        runBlocking {
            enable()
            libraryFixture()
            invalidIndexId = true
            assertEquals("INVALID_RESPONSE", scan().code)
            assertEquals(null, Komga.binding(1, mangaId))
        }

    @Test fun staleOutagesRetainLinksAndCredentialChangesHideCachedCounts() =
        runBlocking {
            enable()
            libraryFixture()
            scan()
            transaction { KomgaIndexTable.update({ KomgaIndexTable.user eq 1 }) { it[fetchedAt] = 0 } }
            KomgaIndex.invalidate(1)
            status = 503
            val rejectedCorrection = Komga.link(1, mangaId, "s")
            assertEquals("AUTOMATIC", rejectedCorrection.provenance)
            assertEquals("s", rejectedCorrection.seriesId)
            assertEquals("REMOTE_ERROR", scan().code)
            val stale = KomgaIndex.summaries(1, listOf(mangaId)).single()
            assertTrue(stale.stale)
            assertEquals(7, stale.bookCount)
            assertNotNull(Komga.binding(1, mangaId))
            Files.writeString(secret, "rotated-key")
            assertEquals(null, KomgaIndex.summaries(1, listOf(mangaId)).single().bookCount)
        }

    @Test fun deletedSeriesHideCountsWithoutRemovingManualLinks() =
        runBlocking {
            enable()
            libraryFixture()
            Komga.link(1, mangaId, "s")
            deletedSeries = true
            assertEquals(1, scan().alreadyLinked)
            val summary = KomgaIndex.summaries(1, listOf(mangaId)).single()
            assertEquals("REVIEW", summary.code)
            assertEquals(null, summary.bookCount)
            assertEquals("MANUAL", Komga.binding(1, mangaId)?.provenance)
        }

    @Test fun largeLibraryUsesPagedSharedIndexAndBoundedLocalBatches() =
        runBlocking {
            enable()
            largeIndex = true
            val baseline =
                transaction {
                    MangaUserTable
                        .selectAll()
                        .where {
                            (MangaUserTable.user eq 1) and
                                (MangaUserTable.inLibrary eq true)
                        }.count()
                        .toInt()
                }
            transaction {
                repeat(401) { number ->
                    val id =
                        MangaTable
                            .insertAndGetId {
                                it[url] = "/large-index-$number-${System.nanoTime()}"
                                it[title] = "Item $number"
                                it[sourceReference] = 0
                            }.value
                    extraManga.add(id)
                    MangaUserTable.upsert(MangaUserTable.manga, MangaUserTable.user) {
                        it[manga] = id
                        it[user] = 1
                        it[inLibrary] = true
                    }
                }
            }
            val result = scan()
            assertEquals("COMPLETE", result.code)
            assertEquals(401 + baseline, result.processed)
            assertEquals(201, result.automaticallyLinked)
            assertEquals(200 + baseline, result.unmatched)
            assertEquals(2, requests.size)
            assertEquals(0, KomgaIndex.summaries(1, extraManga.take(200)).first().bookCount)
            assertEquals(2, requests.size)
        }

    @Test fun metadataEventsUseOnlyChangedMangaAndDoNotRefetchFreshIndex() =
        runBlocking {
            Komga.configure(1, true, api, "", autoLink = true)
            libraryFixture()
            scan()
            val count = requests.size
            val added =
                transaction {
                    val id =
                        MangaTable
                            .insertAndGetId {
                                it[url] = "/incremental-${System.nanoTime()}"
                                it[title] = "No match"
                                it[sourceReference] =
                                    0
                            }.value
                    extraManga.add(id)
                    MangaUserTable.upsert(MangaUserTable.manga, MangaUserTable.user) {
                        it[manga] = id
                        it[user] = 1
                        it[inLibrary] = true
                    }
                    id
                }
            // Simulate a later demand without a real 30-second wait; keep the persisted fresh index.
            KomgaIndex.invalidate(1)
            KomgaIndex.localLibraryChanged(1, added)
            withTimeout(10_000) { while (KomgaIndex.status(1).running) delay(10) }
            assertEquals("COMPLETE", KomgaIndex.status(1).code)
            assertEquals(1, KomgaIndex.status(1).processed)
            assertEquals(1, KomgaIndex.status(1).unmatched)
            assertEquals(count, requests.size)
        }

    @Test fun restoredDeliberateChoicesOutrankOnlyUnconfirmedAutomaticLinks() =
        runBlocking<Unit> {
            enable()
            libraryFixture()
            scan()
            val manualBackup =
                suwayomi.tachidesk.manga.impl.backup.proto.models.BackupKomgaLink(
                    "backup-series",
                    "Backup",
                    provenance = "MANUAL",
                )
            Komga.restoreLink(1, mangaId, manualBackup)
            assertEquals("backup-series", Komga.binding(1, mangaId)?.series)
            assertEquals("MANUAL", Komga.binding(1, mangaId)?.provenance)
            Komga.restoreLink(1, mangaId, manualBackup.copy(seriesId = "automatic", provenance = "AUTOMATIC"))
            assertEquals("backup-series", Komga.binding(1, mangaId)?.series)
            val second =
                transaction {
                    MangaTable
                        .insertAndGetId {
                            it[url] = "/restore-choice-${System.nanoTime()}"
                            it[title] = "Fixture"
                            it[sourceReference] = 0
                        }.value
                }
            extraManga.add(second)
            Komga.restoreLink(1, second, manualBackup.copy(seriesId = "s", provenance = "AUTOMATIC"), Komga.config(1).identity)
            val optOut = manualBackup.copy(seriesId = "", provenance = "OPTED_OUT")
            Komga.restoreLink(1, second, optOut)
            assertEquals(null, Komga.binding(1, second))
            assertEquals("OPTED_OUT", Komga.backupLink(1, second)?.provenance)
            Komga.link(1, second, "s")
            Komga.restoreLink(1, second, optOut)
            assertEquals("MANUAL", Komga.binding(1, second)?.provenance)
            assertEquals("s", Komga.binding(1, second)?.series)
        }

    @Test fun restoredOptOutNeverOverwritesConcurrentManualConfirmationOfAutomaticLink() =
        runBlocking<Unit> {
            enable()
            val auto =
                suwayomi.tachidesk.manga.impl.backup.proto.models
                    .BackupKomgaLink("s", "Fixture", provenance = "AUTOMATIC")
            repeat(20) {
                transaction { KomgaLinkTable.deleteWhere { manga eq mangaId } }
                Komga.restoreLink(1, mangaId, auto, Komga.config(1).identity)
                val ready = CountDownLatch(2)
                val start = CountDownLatch(1)
                val tasks =
                    listOf(
                        async(kotlinx.coroutines.Dispatchers.IO) {
                            ready.countDown()
                            check(start.await(5, TimeUnit.SECONDS))
                            Komga.link(1, mangaId, "s")
                        },
                        async(kotlinx.coroutines.Dispatchers.IO) {
                            ready.countDown()
                            check(start.await(5, TimeUnit.SECONDS))
                            Komga.restoreLink(1, mangaId, auto.copy(seriesId = "", provenance = "OPTED_OUT"))
                        },
                    )
                check(ready.await(5, TimeUnit.SECONDS))
                start.countDown()
                tasks.awaitAll()
                assertEquals("MANUAL", Komga.binding(1, mangaId)?.provenance)
                assertEquals("s", Komga.binding(1, mangaId)?.series)
            }
        }

    @Test fun unlinkedSuppressedAndEmptySummaryBatchesDoNotFetchAnUnneededIndex() =
        runBlocking<Unit> {
            enable()
            libraryFixture()
            val unlinked = KomgaIndex.summaries(1, listOf(mangaId)).single()
            assertEquals("UNLINKED", unlinked.code)
            assertEquals(null, unlinked.bookCount)
            assertFalse(unlinked.stale)
            Komga.unlink(1, mangaId)
            assertEquals("OPTED_OUT", KomgaIndex.summaries(1, listOf(mangaId)).single().code)
            assertTrue(KomgaIndex.summaries(1, emptyList()).isEmpty())
            assertTrue(requests.isEmpty())
            assertFalse(KomgaIndex.status(1).running)
        }

    @Test fun changedTotalsAndRepeatedBooksCannotProduceCompleteInventory() =
        runBlocking {
            secondPage = true
            for (repeat in listOf(false, true)) {
                changingTotals = !repeat
                repeatedBook = repeat
                enable()
                val value = Komga.link(1, mangaId, "s")
                assertEquals("INVALID_RESPONSE", value.code)
                assertFalse(value.inventoryComplete)
                assertEquals(null, value.missingCount)
            }
        }

    @Test fun concurrentRestoreNeverOverwritesManualConfirmation() =
        runBlocking {
            enable()
            val executor = Executors.newFixedThreadPool(2)
            val backup =
                suwayomi.tachidesk.manga.impl.backup.proto.models
                    .BackupKomgaLink("imported", "Imported")
            try {
                repeat(20) {
                    Komga.unlink(1, mangaId)
                    val start = CountDownLatch(1)
                    val restore =
                        executor.submit {
                            check(start.await(10, TimeUnit.SECONDS))
                            Komga.restoreLink(1, mangaId, backup)
                        }
                    val manual =
                        executor.submit {
                            check(start.await(10, TimeUnit.SECONDS))
                            runBlocking { Komga.link(1, mangaId, "s") }
                        }
                    start.countDown()
                    restore.get(30, TimeUnit.SECONDS)
                    manual.get(30, TimeUnit.SECONDS)
                    val binding = assertNotNull(Komga.binding(1, mangaId))
                    assertEquals("s", binding.series)
                    assertEquals(Komga.config(1).identity, binding.identity)
                }
            } finally {
                executor.shutdownNow()
            }
        }

    @Test fun closedPooledConnectionIsRecoveredForReadOnlyRequests() =
        runBlocking {
            enable()
            assertTrue(Komga.testConnection(1).success)
            dropNextRequest = true
            assertTrue(Komga.testConnection(1).success)
            assertEquals(3, requests.size)
        }

    @Test fun malformedBookPagesCannotProduceDefinitiveInventory() =
        runBlocking {
            enable()
            invalidBooks = true
            val result = Komga.link(1, mangaId, "s")
            assertEquals("INVALID_RESPONSE", result.code)
            assertEquals(null, result.missingCount)
            assertFalse(result.inventoryComplete)
        }

    @Test fun portableBackupRoundTripIncludesOnlyNonSecretSettingsAndConfirmedLinksWhenRequested() =
        runBlocking {
            enable()
            Komga.link(1, mangaId, "s")
            transaction {
                MangaUserTable.upsert(MangaUserTable.manga, MangaUserTable.user) {
                    it[manga] = mangaId
                    it[user] = 1
                    it[inLibrary] = true
                }
            }

            fun decode(include: Boolean): Backup {
                val input = ProtoBackupExport.createBackup(1, BackupFlags.DEFAULT.copy(includeUserSettings = include))
                val bytes = GZIPInputStream(input).use { it.readBytes() }
                assertFalse(bytes.toString(Charsets.UTF_8).contains("test-only-key"))
                return ProtoBackupExport.parser.decodeFromByteArray(Backup.serializer(), bytes)
            }
            val included = decode(true)
            assertEquals(api, included.komgaConnection?.apiUrl)
            assertEquals(
                "s",
                included.backupManga
                    .single { it.title == "Komga fixture" }
                    .komgaLink
                    ?.seriesId,
            )
            val excluded = decode(false)
            assertEquals(null, excluded.komgaConnection)
            assertEquals(null, excluded.backupManga.single { it.title == "Komga fixture" }.komgaLink)
            Komga.configure(1, true, api, "", autoLink = true, showCounter = false)
            Komga.unlink(1, mangaId)
            val optedOut = decode(true)
            assertEquals(true, optedOut.komgaConnection?.autoLink)
            assertEquals(false, optedOut.komgaConnection?.showCounter)
            assertEquals(
                "OPTED_OUT",
                optedOut.backupManga
                    .single { it.title == "Komga fixture" }
                    .komgaLink
                    ?.provenance,
            )
        }

    @Test fun disabledAndUnlinkedDoNotContactKomgaAndCredentialsNeverAppearInSettings() =
        runBlocking<Unit> {
            assertEquals("DISABLED", Komga.manga(1, mangaId).code)
            assertTrue(requests.isEmpty())
            enable()
            assertEquals("UNLINKED", Komga.manga(1, mangaId).code)
            assertTrue(requests.isEmpty())
            assertFalse(Komga.settings(1).toString().contains("test-only-key"))
            assertFailsWith<IllegalArgumentException> { Komga.configure(1, true, "http://user:password@localhost", "") }
        }

    @Test fun connectionFallbackSearchLinkPersistenceAndUnlinkAreReadOnly() =
        runBlocking {
            enable()
            legacy = true
            assertTrue(Komga.testConnection(1).success)
            assertEquals(
                "Alias",
                Komga
                    .search(1, "Fixture", 0)
                    .items
                    .single()
                    .alternateTitles
                    .single(),
            )
            val value = Komga.link(1, mangaId, "s")
            assertEquals("READY", value.code)
            assertEquals(listOf("1"), value.confirmedButNotDownloaded)
            assertEquals("http://nas.invalid:8080/reader/series/s", value.openUrl)
            assertEquals(1, value.readBooks)
            assertEquals("s", Komga.binding(1, mangaId)?.series)
            assertEquals("Renamed fixture", Komga.manga(1, mangaId).title)
            val row = transaction { ChapterUserTable.selectAll().where { ChapterUserTable.chapter eq chapterId }.single() }
            assertFalse(row[ChapterUserTable.isRead])
            assertFalse(row[ChapterUserTable.isDownloaded])
            assertTrue(row[ChapterUserTable.isBookmarked])
            assertEquals(7, row[ChapterUserTable.lastPageRead])
            assertTrue(requests.all { it.startsWith("GET ") })
            val count = requests.size
            assertEquals("UNLINKED", Komga.unlink(1, mangaId).code)
            assertEquals(count, requests.size)
        }

    @Test fun cacheCoalescesAndLocalDownloadChangesAreComparedWithoutRefetchingKomga() =
        runBlocking {
            enable()
            Komga.link(1, mangaId, "s")
            val count = requests.size
            (1..6).map { async { Komga.manga(1, mangaId) } }.awaitAll()
            assertEquals(count, requests.size)
            transaction {
                ChapterUserTable.upsert(ChapterUserTable.chapter, ChapterUserTable.user) {
                    it[chapter] = chapterId
                    it[user] = 1
                    it[isDownloaded] =
                        true
                }
            }
            assertEquals(1, Komga.manga(1, mangaId).suwayomiDownloaded)
            assertEquals(count, requests.size)
            Thread.sleep(2100)
            status = 503
            assertEquals("STALE", Komga.manga(1, mangaId, true).code)
            assertEquals(listOf("1"), Komga.manga(1, mangaId).confirmedNumbers)
        }

    @Test fun settingsAndSecretChangesInvalidateCachedDataAndDestinationChangesRequireConfirmation() =
        runBlocking {
            enable()
            Komga.link(1, mangaId, "s")
            val count = requests.size
            Files.writeString(secret, "rotated-test-key")
            assertEquals("READY", Komga.manga(1, mangaId).code)
            assertTrue(requests.size > count)
            val before = requests.size
            Komga.configure(1, true, "$api/another", "")
            assertEquals("CONNECTION_CHANGED", Komga.manga(1, mangaId).code)
            assertEquals(before, requests.size)
            assertEquals("s", Komga.binding(1, mangaId)?.series)
        }

    @Test fun authenticationRedirectMissingAndMalformedResponsesAreSanitized() =
        runBlocking {
            enable()
            for ((code, expected) in listOf(401 to "AUTH_FAILED", 403 to "ACCESS_DENIED", 302 to "REDIRECT", 404 to "NOT_FOUND")) {
                status = code
                val result = Komga.testConnection(1)
                assertEquals(expected, result.code)
                assertFalse(result.toString().contains("test-only-key"))
            }
            status = 200
            malformed = true
            assertEquals("INVALID_RESPONSE", Komga.testConnection(1).code)
            val count = requests.size
            Files.writeString(secret, "invalid-\u00e9-key")
            assertEquals("SECRET_UNAVAILABLE", Komga.testConnection(1).code)
            assertEquals(count, requests.size)
            Files.delete(secret)
            assertEquals("SECRET_UNAVAILABLE", Komga.testConnection(1).code)
            assertFalse(Komga.settings(1).credentialConfigured)
        }

    @Test fun modernReadOnlySearchFallbackAndPaginationWork() =
        runBlocking {
            enable()
            postOnly = true
            secondPage = true
            assertEquals(1, Komga.search(1, "Fixture", 0).items.size)
            val result = Komga.link(1, mangaId, "s")
            assertEquals(2, result.books.size)
            assertTrue(result.inventoryComplete)
            assertTrue(requests.any { it.startsWith("POST /komga/api/v1/books/list") })
            assertTrue(requests.none { it.startsWith("DELETE") || it.startsWith("PATCH") || it.startsWith("PUT") })
        }

    @Test fun backupRestorePreservesExistingManualLinksAndRequiresReconfirmationOfImportedOnes() =
        runBlocking {
            enable()
            Komga.link(1, mangaId, "s")
            val connection = assertNotNull(Komga.backupConnection(1))
            val link = assertNotNull(Komga.backupLink(1, mangaId))
            assertFalse(connection.toString().contains("test-only-key"))
            Komga.restoreConnection(1, connection)
            assertFalse(Komga.settings(1).enabled)
            Komga.restoreLink(1, mangaId, link.copy(seriesId = "other"))
            assertEquals("s", Komga.binding(1, mangaId)?.series)
            Komga.unlink(1, mangaId)
            Komga.restoreLink(1, mangaId, link)
            assertEquals(null, Komga.binding(1, mangaId))
            assertEquals("OPTED_OUT", Komga.backupLink(1, mangaId)?.provenance)
            // Simulate a fresh installation with no decision, rather than undoing a deliberate unlink.
            transaction { KomgaLinkTable.deleteWhere { manga eq mangaId } }
            Komga.restoreLink(1, mangaId, link)
            assertEquals("restored", Komga.binding(1, mangaId)?.identity)
            enable()
            assertEquals("CONNECTION_CHANGED", Komga.manga(1, mangaId).code)
        }
}
