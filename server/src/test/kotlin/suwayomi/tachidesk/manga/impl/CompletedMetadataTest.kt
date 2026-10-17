package suwayomi.tachidesk.manga.impl

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.manga.impl.util.source.GetSource
import suwayomi.tachidesk.manga.impl.util.source.StubSource
import suwayomi.tachidesk.manga.model.table.CompletedCategoryRuleTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.test.ApplicationTest
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompletedMetadataTest : ApplicationTest() {
    private val source =
        object : StubSource(92345671L) {
            var calls = 0
            var status = SManga.COMPLETED
            var fail = false

            override suspend fun getMangaUpdate(
                manga: SManga,
                chapters: List<SChapter>,
                fetchDetails: Boolean,
                fetchChapters: Boolean,
            ): SMangaUpdate {
                calls++
                if (fail) throw IOException("Failed metadata")
                manga.status = status
                return SMangaUpdate(
                    manga,
                    listOf(
                        SChapter.create().apply {
                            url = "/1"
                            name = "Chapter 1"
                            chapter_number = 1f
                        },
                    ),
                )
            }
        }
    private var id = 0
    private var category = 0

    @AfterEach fun clean() {
        GetSource.unregisterSource(source.id)
        transaction {
            CompletedCategoryRuleTable.deleteWhere { user eq 1 }
            MangaTable.deleteWhere { MangaTable.id eq this@CompletedMetadataTest.id }
        }
        if (category !=
            0
        ) {
            transaction {
                suwayomi.tachidesk.manga.model.table.CategoryTable.deleteWhere {
                    suwayomi.tachidesk.manga.model.table.CategoryTable.id eq
                        category
                }
            }
        }
    }

    @Test fun successfulMetadataOnlyTriggersRuleAndAddsNoRequestsOrChapterChanges() =
        runBlocking {
            GetSource.registerSource(source.id to source)
            category = Category.createCategory(1, "Metadata completed ${System.nanoTime()}")
            id =
                transaction {
                    MangaTable
                        .insertAndGetId {
                            it[url] = "/completed-metadata"
                            it[title] = "Metadata test"
                            it[sourceReference] = this@CompletedMetadataTest.source.id
                            it[initialized] = true
                        }.value
                }
            transaction {
                MangaUserTable.upsert(MangaUserTable.user, MangaUserTable.manga) {
                    it[user] = 1
                    it[manga] = this@CompletedMetadataTest.id
                    it[inLibrary] = true
                }
            }
            CompletedCategories.configure(1, true, category)
            source.fail = true
            assertFailsWith<IOException> { Manga.fetchManga(id) }
            assertTrue(CategoryManga.getMangaCategories(1, id).isEmpty())
            source.fail = false
            Manga.updateMangaAndChapters(id, updateManga = false)
            assertTrue(CategoryManga.getMangaCategories(1, id).isEmpty())
            val chapterId =
                transaction {
                    suwayomi.tachidesk.manga.model.table.ChapterTable
                        .selectAll()
                        .where {
                            suwayomi.tachidesk.manga.model.table.ChapterTable.manga eq
                                this@CompletedMetadataTest.id
                        }.single()[suwayomi.tachidesk.manga.model.table.ChapterTable.id]
                        .value
                }
            transaction {
                suwayomi.tachidesk.manga.model.table.ChapterUserTable.upsert(
                    suwayomi.tachidesk.manga.model.table.ChapterUserTable.chapter,
                    suwayomi.tachidesk.manga.model.table.ChapterUserTable.user,
                ) {
                    it[chapter] = chapterId
                    it[user] = 1
                    it[isRead] = true
                    it[isBookmarked] = true
                    it[lastPageRead] = 12
                    it[isDownloaded] = true
                    it[isDownloadRequested] = true
                }
            }
            Manga.fetchManga(id)
            assertEquals(3, source.calls)
            transaction {
                val row =
                    suwayomi.tachidesk.manga.model.table.ChapterUserTable
                        .selectAll()
                        .where {
                            suwayomi.tachidesk.manga.model.table.ChapterUserTable.chapter eq chapterId
                        }.single()
                assertTrue(row[suwayomi.tachidesk.manga.model.table.ChapterUserTable.isRead])
                assertTrue(row[suwayomi.tachidesk.manga.model.table.ChapterUserTable.isBookmarked])
                assertTrue(row[suwayomi.tachidesk.manga.model.table.ChapterUserTable.isDownloaded])
                assertTrue(row[suwayomi.tachidesk.manga.model.table.ChapterUserTable.isDownloadRequested])
                assertEquals(12, row[suwayomi.tachidesk.manga.model.table.ChapterUserTable.lastPageRead])
            }
            assertTrue(CategoryManga.getMangaCategories(1, id).any { it.id == category })
            source.status = SManga.ONGOING
            Manga.fetchManga(id)
            assertTrue(CategoryManga.getMangaCategories(1, id).any { it.id == category })
            CategoryManga.removeMangaFromCategory(1, id, category)
            source.status = SManga.COMPLETED
            Manga.fetchManga(id)
            assertFalse(CategoryManga.getMangaCategories(1, id).any { it.id == category })
            assertEquals(5, source.calls)
        }
}
