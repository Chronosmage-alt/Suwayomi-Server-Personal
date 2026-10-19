package suwayomi.tachidesk.graphql

import graphql.schema.idl.SchemaPrinter
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import suwayomi.tachidesk.graphql.server.GraphQLSchemaProvider
import suwayomi.tachidesk.manga.model.table.KomgaConnectionTable
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.test.GraphQLTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class KomgaGraphQLTest : GraphQLTest() {
    @Test fun defaultUserSettingsPermissionAndSchemaRemainCompatible() {
        try {
            graphql("query { komgaSettings { enabled apiUrl browserUrl credentialConfigured canManage autoLink showCounter } }").also {
                it.assertNoErrors()
                assertEquals(false, it.dataPath("komgaSettings", "enabled"))
                assertEquals(false, it.dataPath("komgaSettings", "autoLink"))
                assertEquals(true, it.dataPath("komgaSettings", "showCounter"))
            }
            graphql("query { komgaLibrarySummaries(mangaIds:[1]) { mangaId bookCount provenance code stale } }").assertNoErrors()
            graphql("mutation { scanKomgaLibrary { running code } }").also {
                it.assertNoErrors()
                assertEquals("DISABLED", it.dataPath("scanKomgaLibrary", "code"))
            }
            assertFalse(graphql("mutation { scanKomgaLibrary { running } }", user = UserType.User(1, emptyList())).errors.isNullOrEmpty())
            assertFalse(graphql("query { komgaLibrarySummaries(mangaIds:[1]) { code } }", user = UserType.Visitor).errors.isNullOrEmpty())
            graphql(
                "mutation { setKomgaSettings(enabled:false, apiUrl: \"http://komga:25600\", browserUrl: \"http://nas:8080\") { enabled } }",
            ).assertNoErrors()
            val denied =
                graphql(
                    "mutation { setKomgaSettings(enabled:true, apiUrl: \"http://komga:25600\") { enabled } }",
                    user = UserType.User(1, emptyList()),
                )
            assertFalse(denied.errors.isNullOrEmpty())
            val visitor = graphql("query { komgaSettings { enabled } }", user = UserType.Visitor)
            assertFalse(visitor.errors.isNullOrEmpty())
            val schema = runBlocking { SchemaPrinter().print(GraphQLSchemaProvider.getSchema()) }
            assertFalse(schema.contains("apiKey:"))
            File("build/adaptive-schema.graphql").writeText(schema)
        } finally {
            org.jetbrains.exposed.v1.jdbc.transactions
                .transaction { KomgaConnectionTable.deleteWhere { user eq 1 } }
        }
    }
}
