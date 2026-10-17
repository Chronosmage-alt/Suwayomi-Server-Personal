package suwayomi.tachidesk.manga.impl

import eu.kanade.tachiyomi.source.model.SManga
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.graphql.types.CompletedCategoryOption
import suwayomi.tachidesk.graphql.types.CompletedCategoryPreview
import suwayomi.tachidesk.graphql.types.CompletedCategorySettings
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupCompletedCategoryRule
import suwayomi.tachidesk.manga.model.table.CategoryMangaTable
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.manga.model.table.CompletedCategoryHandledTable as Handled
import suwayomi.tachidesk.manga.model.table.CompletedCategoryRuleTable as Rules

/** No source access. Rule-row locks serialize automation, organisation and manual removals. */
object CompletedCategories {
    fun settings(userId: Int): CompletedCategorySettings =
        transaction {
            val rule = Rules.selectAll().where { Rules.user eq userId }.firstOrNull()
            val options =
                CategoryTable
                    .selectAll()
                    .where {
                        (CategoryTable.user eq userId) and (CategoryTable.isDefaultCategory eq false)
                    }.map { CompletedCategoryOption(it[CategoryTable.id].value, it[CategoryTable.name]) }
            val categoryId = rule?.get(Rules.category)?.value
            val enabled = rule?.get(Rules.enabled) ?: false
            CompletedCategorySettings(
                enabled,
                categoryId,
                enabled && options.any { it.id == categoryId },
                rule?.get(Rules.destinationSelected) == true && options.none { it.id == categoryId },
                options,
            )
        }

    fun configure(
        userId: Int,
        enabled: Boolean,
        categoryId: Int?,
    ): CompletedCategorySettings =
        transaction {
            require(
                categoryId == null || settings(userId).categories.any { it.id == categoryId },
            ) { "Select an existing category belonging to you." }
            require(!enabled || categoryId != null) { "Select a destination category before enabling the rule." }
            Rules.upsert(Rules.user) {
                it[user] =
                    EntityID(userId, UserAccountTable)
                it[Rules.enabled] = enabled
                it[Rules.destinationSelected] = categoryId != null
                it[category] =
                    categoryId?.let { id ->
                        EntityID(id, CategoryTable)
                    }
            }
            settings(userId)
        }

    private fun lockRule(userId: Int) =
        Rules
            .selectAll()
            .where { Rules.user eq userId }
            .forUpdate()
            .firstOrNull()

    private fun mark(
        userId: Int,
        mangaId: Int,
        categoryId: Int,
    ) {
        Handled.upsert(Handled.user, Handled.manga, Handled.category) {
            it[user] =
                EntityID(userId, UserAccountTable)
            it[manga] =
                EntityID(mangaId, MangaTable)
            it[category] =
                EntityID(categoryId, CategoryTable)
        }
    }

    // Must run in the same transaction as manual category deletion, before deleting assignments.
    fun respectRemoval(
        userId: Int,
        mangaIds: List<Int>,
        categoryIds: List<Int>? = null,
    ) {
        val rule = lockRule(userId) ?: return
        val destination = rule[Rules.category]?.value ?: return
        if (categoryIds != null && destination !in categoryIds) return
        mangaIds
            .distinct()
            .filter { id -> MangaTable.selectAll().where { MangaTable.id eq id }.any() }
            .forEach { mark(userId, it, destination) }
    }

    private fun applyOne(
        userId: Int,
        mangaId: Int,
        categoryId: Int,
        apply: Boolean,
    ): Int {
        val present =
            CategoryMangaTable
                .selectAll()
                .where {
                    (CategoryMangaTable.user eq userId) and (CategoryMangaTable.manga eq mangaId) and
                        (CategoryMangaTable.category eq categoryId)
                }.any()
        val handled =
            Handled
                .selectAll()
                .where {
                    (Handled.user eq userId) and (Handled.manga eq mangaId) and (Handled.category eq categoryId)
                }.any()
        if (apply && !handled) {
            if (!present) CategoryManga.addMangaToCategory(userId, mangaId, categoryId)
            mark(userId, mangaId, categoryId)
        }
        return if (present) {
            1
        } else if (handled) {
            2
        } else {
            0
        }
    }

    // Called only by the successful metadata persistence path, in its transaction.
    fun metadataCompleted(mangaId: Int) {
        val users =
            MangaUserTable
                .selectAll()
                .where {
                    (MangaUserTable.manga eq mangaId) and (MangaUserTable.inLibrary eq true)
                }.map { it[MangaUserTable.user].value }
                .sorted()
        users.forEach { userId ->
            val rule = lockRule(userId) ?: return@forEach
            val destination = rule[Rules.category]?.value ?: return@forEach
            if (rule[Rules.enabled] && settings(userId).effective) applyOne(userId, mangaId, destination, true)
        }
    }

    fun organise(
        userId: Int,
        categoryId: Int,
        apply: Boolean,
    ): CompletedCategoryPreview =
        transaction {
            lockRule(userId)
            val config = settings(userId)
            require(
                config.categoryId == categoryId &&
                    config.categories.any {
                        it.id == categoryId
                    },
            ) { "Destination changed or was deleted. Select a category and preview again." }
            val counts = IntArray(3)
            (MangaTable innerJoin MangaUserTable)
                .selectAll()
                .where {
                    (MangaUserTable.user eq userId) and (MangaUserTable.inLibrary eq true) and (MangaTable.status eq SManga.COMPLETED)
                }.forEach { counts[applyOne(userId, it[MangaTable.id].value, categoryId, apply)]++ }
            CompletedCategoryPreview(categoryId, counts[0], counts[1], counts[2])
        }

    fun backupRule(userId: Int): BackupCompletedCategoryRule =
        transaction {
            val config = settings(userId)
            val order =
                config.categoryId?.let { id ->
                    CategoryTable
                        .selectAll()
                        .where {
                            (CategoryTable.id eq id) and (CategoryTable.user eq userId)
                        }.firstOrNull()
                        ?.get(CategoryTable.order)
                }
            BackupCompletedCategoryRule(config.enabled, order, config.categoryId != null || config.destinationMissing)
        }

    fun restoreRule(
        userId: Int,
        rule: BackupCompletedCategoryRule,
        mapping: Map<Int, Int>,
    ) = transaction {
        val destination = rule.categoryOrder?.let { mapping[it] }?.takeIf { id -> settings(userId).categories.any { it.id == id } }
        configure(userId, false, destination)
        // Keep enabled intent with a missing destination so the UI can explain why it is inactive.
        Rules.update({ Rules.user eq userId }) {
            it[Rules.enabled] = rule.enabled
            it[Rules.destinationSelected] = rule.hadDestination || rule.categoryOrder != null
        }
    }

    fun handledOrders(
        userId: Int,
        mangaId: Int,
    ): List<Int> =
        transaction {
            (Handled innerJoin CategoryTable)
                .selectAll()
                .where {
                    (Handled.user eq userId) and (Handled.manga eq mangaId) and (CategoryTable.user eq userId)
                }.map { it[CategoryTable.order] }
        }

    fun restoreHandled(
        userId: Int,
        mangaId: Int,
        orders: List<Int>,
        mapping: Map<Int, Int>,
    ) = transaction {
        lockRule(userId)
        val owned = settings(userId).categories.map { it.id }.toSet()
        orders
            .mapNotNull { mapping[it] }
            .filter { it in owned }
            .distinct()
            .forEach { mark(userId, mangaId, it) }
    }
}
