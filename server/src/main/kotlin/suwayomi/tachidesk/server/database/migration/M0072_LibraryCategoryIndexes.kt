package suwayomi.tachidesk.server.database.migration

import de.neonew.exposed.migrations.helpers.SQLMigration

@Suppress("ClassName", "unused")
class M0072_LibraryCategoryIndexes : SQLMigration() {
    override val sql =
        """
        CREATE INDEX IF NOT EXISTS mangauser_user_library_manga ON MANGAUSER (user_id, in_library, manga);
        CREATE INDEX IF NOT EXISTS categorymanga_user_manga ON CATEGORYMANGA (user_id, manga);
        """.trimIndent()
}
