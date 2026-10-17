package suwayomi.tachidesk.manga.impl

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.CompletedCategoryRuleTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.test.ApplicationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
class CompletedCategoriesTest : ApplicationTest() {
    private val mangas = mutableListOf<Int>()
    private val categories = mutableListOf<Int>()

    @AfterEach fun clean() {
        transaction {
            CompletedCategoryRuleTable.deleteWhere { user eq 1 }
            mangas.forEach { id -> MangaTable.deleteWhere { MangaTable.id eq id } }
            categories.forEach { id -> CategoryTable.deleteWhere { CategoryTable.id eq id } }
        }
    }

    private fun category(user: Int = 1) = Category.createCategory(user, "Completed ${System.nanoTime()}").also { categories.add(it) }

    private fun manga(
        status: Int = SManga.COMPLETED,
        user: Int = 1,
        library: Boolean = true,
    ): Int =
        transaction {
            val id =
                MangaTable
                    .insertAndGetId {
                        it[url] = "/completed-${System.nanoTime()}"
                        it[title] = "Completed rule test"
                        it[sourceReference] = 0
                        it[MangaTable.status] = status
                    }.value
            MangaUserTable.upsert(MangaUserTable.user, MangaUserTable.manga) {
                it[MangaUserTable.user] = user
                it[manga] = id
                it[inLibrary] = library
            }
            mangas.add(id)
            id
        }

    private fun present(
        id: Int,
        category: Int,
        user: Int = 1,
    ) = CategoryManga.getMangaCategories(user, id).any { it.id == category }

    @Test fun defaultDisabledAndDefaultCategoryRejected() {
        assertFalse(CompletedCategories.settings(1).enabled)
        assertFailsWith<IllegalArgumentException> { CompletedCategories.configure(1, true, Category.getDefaultCategoryId(1)) }
        assertFailsWith<IllegalArgumentException> { CompletedCategories.configure(1, true, null) }
        val destination = category()
        val id = manga()
        CompletedCategories.configure(1, false, destination)
        transaction { CompletedCategories.metadataCompleted(id) }
        assertFalse(present(id, destination))
    }

    @Test fun addOnceRetainsOtherCategoriesAndSurvivesOngoingAndToggle() {
        val destination = category()
        val other = category()
        val id = manga()
        CategoryManga.addMangaToCategory(1, id, other)
        CompletedCategories.configure(1, true, destination)
        repeat(3) { transaction { CompletedCategories.metadataCompleted(id) } }
        assertTrue(present(id, destination))
        assertTrue(present(id, other))
        assertEquals(1L, transaction { CategoryMangaTable.selectAll().where { CategoryMangaTable.category eq destination }.count() })
        transaction { MangaTable.update({ MangaTable.id eq id }) { it[status] = SManga.ONGOING } }
        assertTrue(present(id, destination))
        CategoryManga.removeMangaFromCategory(1, id, destination)
        CompletedCategories.configure(1, false, destination)
        CompletedCategories.configure(1, true, destination)
        transaction { MangaTable.update({ MangaTable.id eq id }) { it[status] = SManga.COMPLETED } }
        transaction { CompletedCategories.metadataCompleted(id) }
        assertFalse(present(id, destination))
        assertTrue(present(id, other))
        val reloaded = CompletedCategories.settings(1)
        assertTrue(reloaded.effective)
        assertEquals(1, CompletedCategories.organise(1, destination, false).skipped)
        CompletedCategories.organise(1, destination, true)
        assertFalse(present(id, destination))
        val replacement = category()
        CompletedCategories.configure(1, true, replacement)
        transaction { CompletedCategories.metadataCompleted(id) }
        assertTrue(present(id, replacement))
    }

    @Test fun previewIsReadOnlyAndStoredOnlyOrganisationWorksWithAutomaticDisabled() {
        val destination = category()
        CompletedCategories.configure(1, false, destination)
        val baseline = CompletedCategories.organise(1, destination, false)
        val add = manga()
        val present = manga()
        val removed = manga()
        manga(SManga.ONGOING)
        manga(library = false)
        CategoryManga.addMangaToCategory(1, present, destination)
        CategoryManga.removeMangaFromCategory(1, removed, destination)
        val preview = CompletedCategories.organise(1, destination, false)
        assertEquals(baseline.toAdd + 1, preview.toAdd)
        assertEquals(baseline.alreadyPresent + 1, preview.alreadyPresent)
        assertEquals(baseline.skipped + 1, preview.skipped)
        assertFalse(present(add, destination))
        assertEquals(preview, CompletedCategories.organise(1, destination, true))
        assertTrue(present(add, destination))
        assertFalse(present(removed, destination))
        assertEquals(0, CompletedCategories.organise(1, destination, true).toAdd)
        CategoryManga.removeMangaFromAllCategories(1, add)
        transaction { CompletedCategories.metadataCompleted(add) }
        assertFalse(present(add, destination))
    }

    @Test fun deletionAndStalePreviewNeverSelectReplacement() {
        val destination = category()
        val replacement = category()
        CompletedCategories.configure(1, true, destination)
        val id = manga()
        transaction { CategoryTable.deleteWhere { CategoryTable.id eq destination } }
        val config = CompletedCategories.settings(1)
        assertTrue(config.enabled)
        assertFalse(config.effective)
        assertNull(config.categoryId)
        transaction { CompletedCategories.metadataCompleted(id) }
        assertFalse(present(id, replacement))
        assertFailsWith<IllegalArgumentException> { CompletedCategories.organise(1, destination, true) }
        CompletedCategories.configure(1, false, replacement)
        assertFailsWith<IllegalArgumentException> { CompletedCategories.organise(1, destination, true) }
    }

    @Test fun underlyingUserOwnershipIsPreserved() {
        val user =
            transaction {
                UserAccountTable
                    .insertAndGetId {
                        it[username] = "completed-${System.nanoTime()}"
                        it[password] = "unused"
                    }.value
            }
        val own = category()
        val theirs = category(user)
        try {
            assertFailsWith<IllegalArgumentException> { CompletedCategories.configure(1, true, theirs) }
            CompletedCategories.configure(1, true, own)
            CompletedCategories.configure(user, true, theirs)
            val id = manga()
            transaction { CompletedCategories.metadataCompleted(id) }
            assertFalse(present(id, theirs, user))
            transaction {
                MangaUserTable.upsert(MangaUserTable.user, MangaUserTable.manga) {
                    it[MangaUserTable.user] = user
                    it[manga] = id
                    it[inLibrary] = true
                }
            }
            transaction { CompletedCategories.metadataCompleted(id) }
            assertTrue(present(id, theirs, user))
            assertTrue(present(id, own))
        } finally {
            transaction { UserAccountTable.deleteWhere { UserAccountTable.id eq user } }
        }
    }

    @Test fun overlappingOrganisationAndRefreshesCannotUndoRemoval() =
        runBlocking {
            val destination = category()
            val id = manga()
            CompletedCategories.configure(1, true, destination)
            (1..8)
                .map {
                    async(Dispatchers.IO) {
                        if (it % 2 == 0) {
                            CompletedCategories.organise(1, destination, true)
                        } else {
                            transaction { CompletedCategories.metadataCompleted(id) }
                        }
                    }
                }.awaitAll()
            assertTrue(present(id, destination))
            (1..8)
                .map {
                    async(Dispatchers.IO) {
                        if (it % 2 == 0) {
                            CategoryManga.removeMangaFromCategory(1, id, destination)
                        } else {
                            CompletedCategories.organise(1, destination, true)
                        }
                    }
                }.awaitAll()
            assertFalse(present(id, destination))
        }

    @Test fun portableBackupRemapsRuleAndHandledRecordsWithoutRestoringRemovedAssignment() {
        val destination = category()
        val id = manga()
        CompletedCategories.configure(1, true, destination)
        transaction { CompletedCategories.metadataCompleted(id) }
        CategoryManga.removeMangaFromCategory(1, id, destination)
        val flags = suwayomi.tachidesk.manga.impl.backup.BackupFlags.DEFAULT
        val entry =
            suwayomi.tachidesk.manga.impl.backup.proto.handlers.BackupMangaHandler.backup(1, flags).first {
                it.url ==
                    transaction { MangaTable.selectAll().where { MangaTable.id eq id }.single()[MangaTable.url] }
            }
        val rule = CompletedCategories.backupRule(1)
        assertTrue(entry.completedCategoryHandled.isNotEmpty())
        assertTrue(entry.categories.isEmpty())
        val bytes =
            suwayomi.tachidesk.manga.impl.backup.proto.ProtoBackupExport.createBackup(1, flags).use {
                java.util.zip
                    .GZIPInputStream(it)
                    .readBytes()
            }
        val serializer =
            suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
                .serializer()
        val decoded =
            kotlinx.serialization.protobuf.ProtoBuf
                .decodeFromByteArray(serializer, bytes)
        assertEquals(rule, decoded.completedCategoryRule)
        val replacement = category()
        val mapping = mapOf(rule.categoryOrder!! to replacement)
        CompletedCategories.restoreRule(1, decoded.completedCategoryRule!!, mapping)
        val errors = mutableListOf<Pair<java.util.Date, String>>()
        suwayomi.tachidesk.manga.impl.backup.proto.handlers.BackupMangaHandler.restore(
            1,
            decoded.backupManga.first { it.url == entry.url },
            mapping,
            emptyMap(),
            errors,
            flags.copy(includeTracking = false),
        )
        assertTrue(errors.isEmpty(), errors.toString())
        assertEquals(replacement, CompletedCategories.settings(1).categoryId)
        transaction { CompletedCategories.metadataCompleted(id) }
        assertFalse(present(id, replacement))
        val withoutSettings =
            suwayomi.tachidesk.manga.impl.backup.proto.handlers.BackupMangaHandler
                .backup(
                    1,
                    flags.copy(includeUserSettings = false),
                ).first {
                    it.url ==
                        entry.url
                }
        assertTrue(withoutSettings.completedCategoryHandled.isEmpty())
        CompletedCategories.restoreRule(1, rule, emptyMap())
        assertFalse(CompletedCategories.settings(1).effective)
        assertTrue(CompletedCategories.settings(1).destinationMissing)
        // Clearing links during a legacy restore is not a manual opt-out.
        CompletedCategories.configure(1, true, replacement)
        val oldId = manga()
        val oldUrl = transaction { MangaTable.selectAll().where { MangaTable.id eq oldId }.single()[MangaTable.url] }
        val legacy = entry.copy(url = oldUrl, completedCategoryHandled = emptyList(), categories = emptyList())
        suwayomi.tachidesk.manga.impl.backup.proto.handlers.BackupMangaHandler.restore(
            1,
            legacy,
            emptyMap(),
            emptyMap(),
            errors,
            flags.copy(includeTracking = false, includeUserSettings = false),
        )
        assertTrue(errors.isEmpty(), errors.toString())
        transaction { CompletedCategories.metadataCompleted(oldId) }
        assertTrue(present(oldId, replacement))
    }
}
