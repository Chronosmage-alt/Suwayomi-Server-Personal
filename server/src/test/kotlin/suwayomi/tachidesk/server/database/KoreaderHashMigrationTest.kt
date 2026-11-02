package suwayomi.tachidesk.server.database

import suwayomi.tachidesk.server.database.migration.M0073_MoveKoreaderHashToChapter
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KoreaderHashMigrationTest {
    @Test
    fun upgradesExistingPersonalSchemaAndCanRunAgain() {
        DriverManager.getConnection("jdbc:h2:mem:koreader-migration;DB_CLOSE_DELAY=-1").use { connection ->
            connection.createStatement().use { statement ->
                statement.execute("CREATE TABLE CHAPTER (ID INT PRIMARY KEY)")
                statement.execute("CREATE TABLE CHAPTERUSER (CHAPTER INT, KOREADER_HASH VARCHAR(32), LAST_PAGE_READ INT)")
                statement.execute("INSERT INTO CHAPTER VALUES (1)")
                statement.execute("INSERT INTO CHAPTERUSER VALUES (1, 'old-filename-hash', 12)")
                repeat(2) {
                    M0073_MoveKoreaderHashToChapter()
                        .sql
                        .split(';')
                        .filter { it.isNotBlank() }
                        .forEach(statement::execute)
                }
                statement.executeQuery("SELECT KOREADER_HASH FROM CHAPTER WHERE ID = 1").use {
                    assertTrue(it.next())
                    assertEquals(null, it.getString(1))
                }
                statement.executeQuery("SELECT LAST_PAGE_READ FROM CHAPTERUSER WHERE CHAPTER = 1").use {
                    assertTrue(it.next())
                    assertEquals(12, it.getInt(1))
                }
                connection.metaData.getColumns(null, null, "CHAPTERUSER", "KOREADER_HASH").use {
                    assertFalse(it.next())
                }
            }
        }
    }
}
