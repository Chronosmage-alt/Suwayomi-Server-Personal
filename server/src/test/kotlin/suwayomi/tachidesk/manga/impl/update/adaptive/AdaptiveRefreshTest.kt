package suwayomi.tachidesk.manga.impl.update.adaptive

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.CategoryManga
import suwayomi.tachidesk.manga.impl.Manga
import suwayomi.tachidesk.manga.impl.backup.BackupFlags
import suwayomi.tachidesk.manga.impl.backup.proto.handlers.BackupMangaHandler
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupChapter
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupManga
import suwayomi.tachidesk.manga.impl.update.JobStatus
import suwayomi.tachidesk.manga.impl.update.Updater
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import suwayomi.tachidesk.manga.impl.util.source.StubSource
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.server.settings.userSettings
import suwayomi.tachidesk.server.settings.value
import suwayomi.tachidesk.test.ApplicationTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdaptiveRefreshTest : ApplicationTest() {
    private val source =
        object : StubSource(87654321L) {
            var calls = 0
            var fail = false
            var latency = 30L
            var entered: CompletableDeferred<Unit>? = null
            var release: CompletableDeferred<Unit>? = null

            override suspend fun getMangaUpdate(
                manga: SManga,
                chapters: List<SChapter>,
                fetchDetails: Boolean,
                fetchChapters: Boolean,
            ): SMangaUpdate {
                calls++
                entered?.complete(Unit)
                release?.await()
                delay(latency)
                if (fail) throw IOException("Source cooldown")
                return SMangaUpdate(
                    manga,
                    chapters.ifEmpty {
                        listOf(
                            SChapter.create().apply {
                                url = "/1"
                                name = "Chapter 1"
                                chapter_number = 1f
                            },
                        )
                    },
                )
            }
        }

    private fun manga(): Int {
        serverConfig.smartLibraryUpdates.value = true
        GetSource.registerSource(source.id to source)
        val id =
            transaction {
                MangaTable
                    .insertAndGetId {
                        it[url] = "/adaptive-${System.nanoTime()}"
                        it[title] = "Adaptive test"
                        it[sourceReference] = this@AdaptiveRefreshTest.source.id
                        it[initialized] = true
                    }.value
            }
        AdaptiveUpdates.setMode(id, AdaptiveOverride.SMART)
        return id
    }

    private fun futureState() =
        AdaptiveState(
            lastCheck = System.currentTimeMillis(),
            interval = 7 * AdaptiveSchedule.DAY,
            nextCheck = System.currentTimeMillis() + AdaptiveSchedule.DAY,
            phase = AdaptivePhase.SCHEDULED,
        )

    @AfterEach fun clean() {
        GetSource.unregisterSource(source.id)
        serverConfig.smartLibraryUpdates.value = false
    }

    private suspend fun awaitIdle(updater: Updater) =
        withTimeout(5000) {
            while (updater.getStatus().isRunning) delay(10)
        }

    @Test fun disabledAdaptivePreservesEveryQueuedAutomaticAndManualRequest() =
        runBlocking {
            val id = manga()
            Manga.updateMangaAndChapters(id, updateManga = false)
            AdaptiveUpdates.saveState(id, futureState())
            serverConfig.smartLibraryUpdates.value = false
            val updater = Updater()
            try {
                val title = Manga.getManga(id)
                updater.queueMangas(listOf(title), null, automated = true)
                updater.queueMangas(listOf(title), null, automated = true)
                updater.addMangasToQueue(listOf(title), null)
                awaitIdle(updater)
                assertEquals(4, source.calls)
                assertEquals(
                    JobStatus.COMPLETE,
                    updater
                        .getStatus()
                        .mangaUpdates
                        .single()
                        .status,
                )
            } finally {
                updater.reset()
            }
        }

    @Test fun runningAutomaticCannotLoseQueuedManualRefreshOrBecomeIdle() =
        runBlocking {
            val id = manga()
            Manga.updateMangaAndChapters(id, updateManga = false)
            source.entered = CompletableDeferred()
            source.release = CompletableDeferred()
            val updater = Updater()
            try {
                val title = Manga.getManga(id)
                updater.queueMangas(listOf(title), null, automated = true)
                withTimeout(5000) { source.entered!!.await() }
                updater.addMangasToQueue(listOf(title), null)
                assertTrue(updater.getStatus().isRunning)
                updater.autoUpdateTask()
                assertTrue(updater.getStatus().isRunning)
                updater.addCategoriesToUpdateQueue(1, emptyList(), clear = true, forceAll = false, automatedUpdate = false)
                assertEquals(
                    JobStatus.PENDING,
                    updater
                        .getStatus()
                        .mangaUpdates
                        .single()
                        .status,
                )
                source.release!!.complete(Unit)
                awaitIdle(updater)
                assertEquals(3, source.calls)
                assertEquals(
                    JobStatus.COMPLETE,
                    updater
                        .getStatus()
                        .mangaUpdates
                        .single()
                        .status,
                )
            } finally {
                source.release!!.complete(Unit)
                updater.reset()
            }
        }

    @Test fun fastCompletionNotificationsNeverRegressToPending() =
        runBlocking {
            val id = manga()
            source.latency = 0
            val updater = Updater()
            val events = java.util.concurrent.CopyOnWriteArrayList<JobStatus>()
            val collector =
                launch(start = CoroutineStart.UNDISPATCHED) {
                    updater.updates.collect { update -> events.addAll(update.mangaUpdates.filter { it.manga.id == id }.map { it.status }) }
                }
            try {
                updater.queueMangas(listOf(Manga.getManga(id)), null, automated = true)
                awaitIdle(updater)
                withTimeout(5000) { while (JobStatus.COMPLETE !in events) delay(10) }
                delay(1100) // Includes the sampled notification flush.
                assertFalse(events.drop(events.indexOf(JobStatus.COMPLETE)).contains(JobStatus.PENDING))
                assertFalse(updater.getStatus().isRunning)
            } finally {
                collector.cancel()
                updater.reset()
            }
        }

    @Test fun resetWaitsForInflightFetchAndWinsOverItsObservation() =
        runBlocking {
            val id = manga()
            Manga.updateMangaAndChapters(id, updateManga = false)
            AdaptiveUpdates.saveState(id, futureState())
            source.entered = CompletableDeferred()
            source.release = CompletableDeferred()
            val fetch = async { Manga.updateMangaAndChapters(id, updateManga = false) }
            source.entered!!.await()
            val reset = async(Dispatchers.IO) { AdaptiveUpdates.resetHistory(id) }
            delay(50)
            assertFalse(reset.isCompleted)
            source.release!!.complete(Unit)
            fetch.await()
            reset.await()
            assertEquals(AdaptiveEntry(AdaptiveOverride.SMART), AdaptiveUpdates.entries(listOf(id))[id])
        }

    @Test fun chapterRestoreWaitsForFetchThenResetsHistoryAndRetainsOverride() =
        runBlocking {
            val id = manga()
            transaction {
                MangaUserTable.upsert(MangaUserTable.user, MangaUserTable.manga) {
                    it[user] = 1
                    it[manga] = id
                    it[inLibrary] = true
                }
            }
            Manga.updateMangaAndChapters(id, updateManga = false)
            AdaptiveUpdates.saveState(id, futureState())
            source.entered = CompletableDeferred()
            source.release = CompletableDeferred()
            val fetch = async { Manga.updateMangaAndChapters(id, updateManga = false) }
            source.entered!!.await()
            val errors = mutableListOf<Pair<java.util.Date, String>>()
            val restore =
                async(Dispatchers.IO) {
                    BackupMangaHandler.restore(
                        1,
                        BackupManga(
                            source = source.id,
                            url =
                                transaction {
                                    MangaTable.selectAll().where { MangaTable.id eq id }.single()[MangaTable.url]
                                },
                            title = "Restored",
                            chapters = listOf(BackupChapter(url = "/42", name = "Chapter 42", chapterNumber = 42f)),
                        ),
                        emptyMap(),
                        emptyMap(),
                        errors,
                        BackupFlags.DEFAULT.copy(includeTracking = false),
                    )
                }
            delay(50)
            assertFalse(restore.isCompleted)
            source.release!!.complete(Unit)
            fetch.await()
            restore.await()
            assertTrue(errors.isEmpty(), errors.toString())
            assertEquals(AdaptiveEntry(AdaptiveOverride.SMART), AdaptiveUpdates.entries(listOf(id))[id])
            assertTrue(
                transaction { ChapterTable.selectAll().where { ChapterTable.manga eq id }.any { it[ChapterTable.chapter_number] == 42f } },
            )
        }

    @Test fun completedExclusionDominatesEveryAdaptiveModeButDirectManualRuns() =
        runBlocking {
            val id = manga()
            transaction {
                MangaTable.update({ MangaTable.id eq id }) { it[status] = SManga.COMPLETED }
                MangaUserTable.upsert(MangaUserTable.user, MangaUserTable.manga) {
                    it[user] = 1
                    it[manga] = id
                    it[inLibrary] = true
                }
            }
            val oldCompleted = userConfig.excludeCompleted.value(1)
            val oldUnread = userConfig.excludeUnreadChapters.value(1)
            val oldStarted = userConfig.excludeNotStarted.value(1)
            userSettings.set(1, userConfig.excludeCompleted, true)
            userSettings.set(1, userConfig.excludeUnreadChapters, false)
            userSettings.set(1, userConfig.excludeNotStarted, false)
            val categoryId = Category.createCategory(1, "Adaptive completed ${System.nanoTime()}")
            CategoryManga.addMangaToCategory(1, id, categoryId)
            val categories = Category.getCategoryList(1).filter { it.id == categoryId }
            val updater = Updater()
            try {
                for (enabled in listOf(false, true)) {
                    serverConfig.smartLibraryUpdates.value = enabled
                    for (mode in AdaptiveOverride.entries) {
                        AdaptiveUpdates.setMode(id, mode)
                        updater.addCategoriesToUpdateQueue(
                            1,
                            categories,
                            clear = true,
                            forceAll = false,
                            automatedUpdate = true,
                        )
                        awaitIdle(updater)
                        assertEquals(0, source.calls)
                        assertEquals(
                            JobStatus.SKIPPED,
                            updater
                                .getStatus()
                                .mangaUpdates
                                .first { it.manga.id == id }
                                .status,
                        )
                    }
                }
                assertTrue(Manga.updateMangaAndChapters(id, updateManga = false))
                assertEquals(1, source.calls)
                userSettings.set(1, userConfig.excludeCompleted, false)
                updater.addCategoriesToUpdateQueue(1, categories, clear = true, forceAll = false, automatedUpdate = true)
                awaitIdle(updater)
                assertEquals(2, source.calls)
            } finally {
                updater.reset()
                userSettings.set(1, userConfig.excludeCompleted, oldCompleted)
                userSettings.set(1, userConfig.excludeUnreadChapters, oldUnread)
                userSettings.set(1, userConfig.excludeNotStarted, oldStarted)
            }
        }

    @Test fun initialFetchRunsThenAutomaticSkipPreservesDataAndManualBypasses() =
        runBlocking {
            val id = manga()
            AdaptiveUpdates.saveState(id, futureState())
            assertTrue(Manga.updateMangaAndChapters(id, updateManga = false, automated = true))
            val state = futureState()
            AdaptiveUpdates.saveState(id, state)
            val timestamp = transaction { MangaTable.selectAll().where { MangaTable.id eq id }.single()[MangaTable.chaptersLastFetchedAt] }
            assertFalse(Manga.updateMangaAndChapters(id, automated = true))
            assertEquals(1, source.calls)
            assertEquals(state, AdaptiveUpdates.entries(listOf(id))[id]!!.state)
            assertEquals(
                timestamp,
                transaction { MangaTable.selectAll().where { MangaTable.id eq id }.single()[MangaTable.chaptersLastFetchedAt] },
            )
            assertTrue(Manga.updateMangaAndChapters(id, updateManga = false))
            assertEquals(2, source.calls)
        }

    @Test fun simultaneousAutomaticJobsCoalesceEvenWhileLearning() =
        runBlocking {
            val id = manga()
            Manga.updateMangaAndChapters(id, updateManga = false)
            val queuedAt = System.nanoTime()
            val first = async { Manga.updateMangaAndChapters(id, updateManga = false, automated = true, queuedAt = queuedAt) }
            val second = async { Manga.updateMangaAndChapters(id, updateManga = false, automated = true, queuedAt = queuedAt) }
            assertEquals(setOf(true, false), setOf(first.await(), second.await()))
            assertEquals(2, source.calls)
        }

    @Test fun sourceFailureDoesNotAdvanceLearningOrBypassCooldown() =
        runBlocking {
            val id = manga()
            val state = futureState()
            AdaptiveUpdates.saveState(id, state)
            source.fail = true
            assertFailsWith<IOException> { Manga.updateMangaAndChapters(id) }
            assertEquals(state, AdaptiveUpdates.entries(listOf(id))[id]!!.state)
            assertEquals(1, source.calls)
        }

    @Test fun automaticQueueSkipsAndDeduplicatesAcrossUsersButManualQueueRuns() =
        runBlocking {
            val id = manga()
            Manga.updateMangaAndChapters(id, updateManga = false)
            AdaptiveUpdates.saveState(id, futureState())
            val updater = Updater()
            try {
                val manga = Manga.getManga(id)
                updater.queueMangas(listOf(manga), null, automated = true)
                updater.queueMangas(listOf(manga), null, automated = true)
                val status = updater.getStatus()
                assertEquals(0, status.totalJobs)
                assertEquals(1, status.skippedMangasCount)
                assertEquals(listOf(JobStatus.SKIPPED), status.mangaUpdates.map { it.status })
                assertEquals(1, source.calls)
                updater.addMangasToQueue(listOf(manga), null)
                repeat(100) {
                    if (updater.getStatus().finishedJobs == 1) return@repeat
                    delay(20)
                }
                assertEquals(1, updater.getStatus().finishedJobs)
                assertEquals(2, source.calls)
            } finally {
                updater.reset()
            }
        }
}
