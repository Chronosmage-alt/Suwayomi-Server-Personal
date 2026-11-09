package suwayomi.tachidesk.graphql

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import suwayomi.tachidesk.graphql.mutations.ChapterMutation
import suwayomi.tachidesk.manga.impl.Chapter
import suwayomi.tachidesk.manga.impl.sync.KoreaderSyncService
import suwayomi.tachidesk.manga.impl.track.Track
import suwayomi.tachidesk.manga.model.table.ChapterTable
import suwayomi.tachidesk.manga.model.table.ChapterUserTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.test.ApplicationTest
import suwayomi.tachidesk.test.clearTables
import suwayomi.tachidesk.test.createChapters
import suwayomi.tachidesk.test.createLibraryManga
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersonalReadingTest : ApplicationTest() {
    @Test
    fun localProgressAndTrackerUpdatesDoNotScheduleKoreaderSync() {
        val mangaId = createLibraryManga("Personal reading")
        createChapters(mangaId, 1, read = false)
        val chapterId =
            transaction {
                ChapterTable
                    .selectAll()
                    .where { ChapterTable.manga eq mangaId }
                    .single()[ChapterTable.id]
                    .value
            }
        transaction {
            ChapterTable.update({ ChapterTable.id eq chapterId }) { it[pageCount] = 20 }
        }
        mockkObject(KoreaderSyncService, Track)
        every { Track.asyncTrackChapter(any(), any()) } just Runs
        try {
            ChapterMutation().updateChapter(
                1,
                ChapterMutation.UpdateChapterInput(
                    id = chapterId,
                    patch = ChapterMutation.UpdateChapterPatch(lastPageRead = 5, isRead = true),
                ),
            )
            transaction {
                val state = ChapterUserTable.selectAll().where { ChapterUserTable.chapter eq chapterId }.single()
                assertEquals(5, state[ChapterUserTable.lastPageRead])
                assertTrue(state[ChapterUserTable.isRead])
            }
            Chapter.updateChapterProgress(1, mangaId, 1, pageNo = 19)
            verify(exactly = 0) { KoreaderSyncService.schedulePushProgress(any(), any()) }
            verify(exactly = 1) { Track.asyncTrackChapter(1, setOf(mangaId)) }
        } finally {
            unmockkObject(KoreaderSyncService, Track)
            clearTables(ChapterUserTable, ChapterTable, MangaTable)
        }
    }
}
