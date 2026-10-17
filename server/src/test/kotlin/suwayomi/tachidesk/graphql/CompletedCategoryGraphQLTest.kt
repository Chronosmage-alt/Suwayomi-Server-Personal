package suwayomi.tachidesk.graphql

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.upsert
import suwayomi.tachidesk.graphql.types.AuthMode
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.CategoryManga
import suwayomi.tachidesk.manga.impl.CompletedCategories
import suwayomi.tachidesk.manga.model.table.CategoryTable
import suwayomi.tachidesk.manga.model.table.CompletedCategoryRuleTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.settings.value
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.server.user.getUserFromToken
import suwayomi.tachidesk.test.GraphQLTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class CompletedCategoryGraphQLTest : GraphQLTest() {
    @Test fun noLoginUsesExistingDefaultUserAndDestinationNames(): Unit =
        runBlocking {
            val old = serverConfig.authMode.value
            val category = Category.createCategory(1, "Completed GraphQL ${System.nanoTime()}")
            try {
                serverConfig.authMode.value = AuthMode.NONE
                assertEquals(1, assertIs<UserType.Admin>(getUserFromToken(null)).id)
                graphql(
                    "mutation { setCompletedCategorySettings(enabled: true, categoryId: $category) { enabled effective categories { id name } } }",
                ).also {
                    it.assertNoErrors()
                    assertEquals(true, it.dataPath("setCompletedCategorySettings", "effective"))
                }
                graphql("query { completedCategoryPreview(categoryId: $category) { toAdd alreadyPresent skipped } }").assertNoErrors()
                val visitor = graphql("query { completedCategorySettings { enabled } }", user = UserType.Visitor)
                assertFalse(visitor.errors.isNullOrEmpty())
                graphql("mutation { setCompletedCategorySettings(enabled: true, categoryId: 2147483647) { enabled } }").also {
                    assertFalse(it.errors.isNullOrEmpty())
                }
            } finally {
                serverConfig.authMode.value = old
                transaction {
                    CompletedCategoryRuleTable.deleteWhere { user eq 1 }
                    CategoryTable.deleteWhere { CategoryTable.id eq category }
                }
            }
        }

    @Test fun graphQlManualRemovalAndClearBeforeAutomaticHandlingAreRespected() {
        val category = Category.createCategory(1, "Removal GraphQL ${System.nanoTime()}")
        val ids =
            transaction {
                (1..2).map { number ->
                    val id =
                        MangaTable
                            .insertAndGetId {
                                it[url] = "/completed-graphql-$number-${System.nanoTime()}"
                                it[title] = "Removal test"
                                it[sourceReference] = 0
                                it[status] = SManga.COMPLETED
                            }.value
                    MangaUserTable.upsert(
                        MangaUserTable.user,
                        MangaUserTable.manga,
                    ) {
                        it[user] = 1
                        it[manga] = id
                        it[inLibrary] = true
                    }
                    id
                }
            }
        try {
            CompletedCategories
                .configure(1, true, category)
            ids.forEach {
                CategoryManga
                    .addMangaToCategory(1, it, category)
            }
            graphql(
                "mutation { updateMangaCategories(input: {id: ${ids[0]}, patch: {removeFromCategories: [$category]}}) { manga { id } } }",
            ).assertNoErrors()
            graphql(
                "mutation { updateMangaCategories(input: {id: ${ids[1]}, patch: {clearCategories: true}}) { manga { id } } }",
            ).assertNoErrors()
            ids.forEach { id ->
                transaction {
                    CompletedCategories
                        .metadataCompleted(id)
                }
                assertEquals(
                    emptyList(),
                    CategoryManga
                        .getMangaCategories(1, id),
                )
            }
            transaction { CategoryTable.deleteWhere { CategoryTable.id eq category } }
            graphql("query { completedCategorySettings { effective destinationMissing categoryId } }").also {
                it.assertNoErrors()
                assertEquals(false, it.dataPath("completedCategorySettings", "effective"))
                assertEquals(true, it.dataPath("completedCategorySettings", "destinationMissing"))
            }
        } finally {
            transaction {
                CompletedCategoryRuleTable.deleteWhere { user eq 1 }
                ids.forEach { id ->
                    MangaTable.deleteWhere {
                        MangaTable.id eq
                            id
                    }
                }
                CategoryTable.deleteWhere { CategoryTable.id eq category }
            }
        }
    }
}
