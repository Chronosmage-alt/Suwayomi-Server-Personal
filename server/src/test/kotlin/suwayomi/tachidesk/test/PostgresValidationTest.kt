package suwayomi.tachidesk.test

import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import suwayomi.tachidesk.server.database.databaseUp
import java.sql.Connection
import java.sql.SQLException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Tag("postgres-validation")
class PostgresValidationTest : ApplicationTest() {
    @Test
    fun freshInstallUpgradeConstraintsAndPersistence() {
        check(PostgresValidation.enabled) { "Run through scripts/test-postgres.ps1" }
        assertFailsWith<IllegalStateException> {
            java.net.Socket().use { it.connect(java.net.InetSocketAddress("192.0.2.1", 443), 100) }
        }
        PostgresValidation.connection().use { connection ->
            assertEquals("71", scalar(connection, "SELECT max(version) FROM migrations"))
            assertEquals("17", scalar(connection, "SELECT last_page_read FROM chapteruser WHERE chapter=900001"))
            assertEquals("true", scalar(connection, "SELECT is_downloaded::text FROM chapteruser WHERE chapter=900001"))
            if (!PostgresValidation.restarting) {
                assertEquals("0", scalar(connection, "SELECT count(*) FROM komga_connection WHERE enabled"))
                assertEquals("0", scalar(connection, "SELECT count(*) FROM komga_connection WHERE auto_link"))
                assertTrue(PostgresValidation.tableExists(connection, "komga_index"))
                connection.createStatement().use {
                    it.execute("INSERT INTO useraccount (id,username,password) VALUES (900001,'disposable-persistence-user','unused')")
                    it.execute("INSERT INTO komga_connection (user_id,identity) VALUES (900001,'persisted-confirmation')")
                    it.execute(
                        "INSERT INTO komga_link (user_id,manga_id,identity,series_id,title) VALUES (900001,900001,'persisted-confirmation','confirmed-series','Confirmed title')",
                    )
                    it.execute("INSERT INTO manga (id,url,title,source) VALUES (900002,'/optout-fixture','Persistent opt-out',0)")
                    it.execute("INSERT INTO komga_link VALUES (900001,900002,'','','','OPTED_OUT')")
                    it.execute("INSERT INTO komga_index VALUES (900001,'persisted-confirmation','fixture-fingerprint',123,'[]')")
                }
                rejected(connection, "INSERT INTO komga_connection (user_id,identity) VALUES (900001,'duplicate')", "23505")
                rejected(
                    connection,
                    "INSERT INTO komga_link (user_id,manga_id,identity,series_id,title) VALUES (900001,900001,'duplicate','other','Other')",
                    "23505",
                )
                rejected(
                    connection,
                    "INSERT INTO komga_link (user_id,manga_id,identity,series_id,title) VALUES (999999,900001,'invalid','other','Other')",
                    "23503",
                )
                rejected(
                    connection,
                    "INSERT INTO komga_link (user_id,manga_id,identity,series_id,title) VALUES (900001,999999,'invalid','other','Other')",
                    "23503",
                )
                rejected(connection, "INSERT INTO komga_connection (user_id,identity) VALUES (999999,'invalid')", "23503")
                rejected(connection, "UPDATE komga_link SET series_id=NULL WHERE user_id=900001", "23502")
                rejected(connection, "UPDATE komga_link SET series_id=repeat('x',257) WHERE user_id=900001", "22001")
                // Cascades must remove integration rows only; rollback keeps the restart fixture.
                connection.autoCommit = false
                try {
                    connection.createStatement().use { it.execute("DELETE FROM useraccount WHERE id=900001") }
                    assertEquals("0", scalar(connection, "SELECT count(*) FROM komga_connection WHERE user_id=900001"))
                    assertEquals("0", scalar(connection, "SELECT count(*) FROM komga_link WHERE user_id=900001"))
                    assertEquals("0", scalar(connection, "SELECT count(*) FROM komga_index WHERE user_id=900001"))
                } finally {
                    connection.rollback()
                    connection.autoCommit = true
                }
                connection.autoCommit = false
                try {
                    connection.createStatement().use { it.execute("DELETE FROM manga WHERE id=900001") }
                    assertEquals("0", scalar(connection, "SELECT count(*) FROM komga_link WHERE manga_id=900001"))
                } finally {
                    connection.rollback()
                    connection.autoCommit = true
                }
            }
            assertEquals(
                "confirmed-series",
                scalar(connection, "SELECT series_id FROM komga_link WHERE user_id=900001 AND manga_id=900001"),
            )
            assertEquals("persisted-confirmation", scalar(connection, "SELECT identity FROM komga_connection WHERE user_id=900001"))
            assertEquals("false", scalar(connection, "SELECT enabled::text FROM komga_connection WHERE user_id=900001"))
            assertEquals("OPTED_OUT", scalar(connection, "SELECT provenance FROM komga_link WHERE user_id=900001 AND manga_id=900002"))
            assertEquals("MANUAL", scalar(connection, "SELECT provenance FROM komga_link WHERE user_id=900001 AND manga_id=900001"))
            assertEquals("[]", scalar(connection, "SELECT payload FROM komga_index WHERE user_id=900001"))
        }
        val previous = TransactionManager.defaultDatabase
        PostgresValidation.connection("fresh").use { connection ->
            if (!PostgresValidation.restarting) {
                assertFalse(PostgresValidation.tableExists(connection, "manga"))
                try {
                    val fresh = PostgresValidation.freshDatabase()
                    TransactionManager.defaultDatabase = fresh
                    databaseUp(fresh)
                } finally {
                    TransactionManager.defaultDatabase = previous
                }
            }
            assertTrue(PostgresValidation.tableExists(connection, "komga_connection"))
            assertEquals("71", scalar(connection, "SELECT max(version) FROM migrations"))
            assertEquals("0", scalar(connection, "SELECT count(*) FROM komga_connection"))
            assertEquals("0", scalar(connection, "SELECT count(*) FROM komga_link"))
            assertEquals("1", scalar(connection, "SELECT count(*) FROM useraccount WHERE id=1"))
        }
    }

    private fun scalar(
        connection: Connection,
        sql: String,
    ): String =
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                check(result.next())
                result.getString(1)
            }
        }

    private fun rejected(
        connection: Connection,
        sql: String,
        state: String,
    ) {
        assertEquals(state, assertFailsWith<SQLException> { connection.createStatement().use { it.execute(sql) } }.sqlState)
    }
}
