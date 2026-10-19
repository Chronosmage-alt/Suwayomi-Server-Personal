package suwayomi.tachidesk.server.database.migration

import de.neonew.exposed.migrations.helpers.AddTableMigration
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

@Suppress("ClassName", "unused")
class M0070_Komga : AddTableMigration() {
    override val tables: Array<Table> get() = arrayOf(Connection, Link)

    private object User : IntIdTable("useraccount")

    private object Manga : IntIdTable("manga")

    private object Connection : Table("komga_connection") {
        val user = reference("user_id", User, onDelete = ReferenceOption.CASCADE)
        val enabled = bool("enabled").default(false)
        val apiUrl = varchar("api_url", 2048).default("")
        val browserUrl = varchar("browser_url", 2048).default("")
        val identity = varchar("identity", 36)
        override val primaryKey = PrimaryKey(user)
    }

    private object Link : Table("komga_link") {
        val user = reference("user_id", User, onDelete = ReferenceOption.CASCADE)
        val manga = reference("manga_id", Manga, onDelete = ReferenceOption.CASCADE)
        val identity = varchar("identity", 36)
        val series = varchar("series_id", 256)
        val title = text("title")
        override val primaryKey = PrimaryKey(user, manga)
    }
}
