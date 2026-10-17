package suwayomi.tachidesk.graphql

import graphql.schema.idl.SchemaPrinter
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.junit.jupiter.api.AfterEach
import suwayomi.tachidesk.graphql.server.GraphQLSchemaProvider
import suwayomi.tachidesk.manga.impl.update.adaptive.AdaptiveOverride
import suwayomi.tachidesk.manga.impl.update.adaptive.AdaptiveUpdates
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.settings.value
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.test.GraphQLTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class AdaptiveGraphQLTest : GraphQLTest() {
    @AfterEach fun clean() {
        serverConfig.smartLibraryUpdates.value = false
    }

    @Test fun schemaProvidesSettingsAndTitleControlsWithExplanations() {
        val id =
            transaction {
                MangaTable
                    .insertAndGetId {
                        it[url] = "/adaptive-graphql"
                        it[title] = "Adaptive GraphQL"
                        it[sourceReference] = 0
                    }.value
            }
        graphql("mutation { setAdaptiveUpdatesEnabled(enabled: true) { enabled maxBackoffDays } }").also {
            it.assertNoErrors()
            assertEquals(true, it.dataPath("setAdaptiveUpdatesEnabled", "enabled"))
            assertEquals(14, it.dataPath("setAdaptiveUpdatesEnabled", "maxBackoffDays"))
        }
        graphql("mutation { setAdaptiveMangaMode(mangaId: $id, mode: SMART) { mode reason observations nextAutomaticCheckAt } }").also {
            it.assertNoErrors()
            assertEquals("LEARNING", it.dataPath("setAdaptiveMangaMode", "reason"))
        }
        AdaptiveUpdates.saveState(
            id,
            suwayomi.tachidesk.manga.impl.update.adaptive.AdaptiveState(
                historical =
                    suwayomi.tachidesk.manga.impl.update.adaptive
                        .HistoricalCandidate(604800000, System.currentTimeMillis(), 6f, 1),
            ),
        )
        graphql(
            "query { adaptiveMangaSchedule(mangaId: $id) { provisionalIntervalDays historicalConfirmations observations nextEligibleAt } }",
        ).also {
            it.assertNoErrors()
            assertEquals(7.0, it.dataPath("adaptiveMangaSchedule", "provisionalIntervalDays"))
            assertEquals(1, it.dataPath("adaptiveMangaSchedule", "historicalConfirmations"))
            assertEquals(0, it.dataPath("adaptiveMangaSchedule", "observations"))
            assertEquals(null, it.dataPath("adaptiveMangaSchedule", "nextEligibleAt"))
        }
        graphql("mutation { resetAdaptiveMangaHistory(mangaId: $id) { mode observations } }").also {
            it.assertNoErrors()
            assertEquals("SMART", it.dataPath("resetAdaptiveMangaHistory", "mode"))
        }
        assertEquals(AdaptiveOverride.SMART, AdaptiveUpdates.entries(listOf(id))[id]!!.mode)
        // Schema artifact for the matching WebUI's normal GraphQL code generator.
        File("build/adaptive-schema.graphql").writeText(runBlocking { SchemaPrinter().print(GraphQLSchemaProvider.getSchema()) })
    }

    @Test fun viewerCanReadSchedulingButCannotChangeSharedSettings() {
        val viewer = UserType.User(1, emptyList())
        graphql("query { adaptiveUpdateSettings { enabled canManage sources { sourceId } } }", user = viewer).also {
            it.assertNoErrors()
            assertEquals(false, it.dataPath("adaptiveUpdateSettings", "canManage"))
            assertEquals(emptyList<Any>(), it.dataPath("adaptiveUpdateSettings", "sources"))
        }
        val denied = graphql("mutation { setAdaptiveUpdatesEnabled(enabled: true) { enabled } }", user = viewer)
        assertFalse(denied.errors.isNullOrEmpty())
        assertFalse(serverConfig.smartLibraryUpdates.value)
    }
}
