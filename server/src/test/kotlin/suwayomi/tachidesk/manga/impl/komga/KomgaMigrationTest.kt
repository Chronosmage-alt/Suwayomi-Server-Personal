package suwayomi.tachidesk.manga.impl.komga

import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.server.database.migration.M0071_KomgaIndex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KomgaMigrationTest {
    @Test fun publishedManualLinksSurviveH2UpgradeWithSafeDefaults() {
        val previous = TransactionManager.primaryDatabase
        val db = Database.connect("jdbc:h2:mem:komga-upgrade-${System.nanoTime()}", driver = "org.h2.Driver")
        try {
            transaction(db) {
                listOf(
                    "CREATE TABLE useraccount (id INTEGER PRIMARY KEY)",
                    "CREATE TABLE manga (id INTEGER PRIMARY KEY)",
                    "CREATE TABLE komga_connection (user_id INTEGER PRIMARY KEY, enabled BOOLEAN, api_url VARCHAR(2048), browser_url VARCHAR(2048), identity VARCHAR(36))",
                    "CREATE TABLE komga_link (user_id INTEGER, manga_id INTEGER, identity VARCHAR(36), series_id VARCHAR(256), title TEXT, PRIMARY KEY(user_id,manga_id))",
                    "INSERT INTO useraccount VALUES (1)",
                    "INSERT INTO manga VALUES (1)",
                    "INSERT INTO komga_connection VALUES (1,true,'http://komga.invalid','','existing')",
                    "INSERT INTO komga_link VALUES (1,1,'existing','series','Manually confirmed')",
                ).forEach { exec(it) }
                M0071_KomgaIndex()
                    .sql
                    .split(';')
                    .filter { it.isNotBlank() }
                    .forEach { exec(it) }
                exec("SELECT * FROM komga_connection") {
                    assertTrue(it.next())
                    assertTrue(it.getBoolean("enabled"))
                    assertFalse(it.getBoolean("auto_link"))
                    assertTrue(it.getBoolean("show_counter"))
                    assertEquals("existing", it.getString("identity"))
                }
                exec("SELECT * FROM komga_link") {
                    assertTrue(it.next())
                    assertEquals("series", it.getString("series_id"))
                    assertEquals("MANUAL", it.getString("provenance"))
                }
                exec("SELECT count(*) FROM komga_index") {
                    assertTrue(it.next())
                    assertEquals(0, it.getInt(1))
                }
            }
        } finally {
            TransactionManager.closeAndUnregister(db)
            if (previous != null) TransactionManager.defaultDatabase = previous
        }
    }
}
