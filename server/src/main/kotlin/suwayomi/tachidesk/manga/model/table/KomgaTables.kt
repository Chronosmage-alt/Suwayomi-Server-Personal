package suwayomi.tachidesk.manga.model.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import suwayomi.tachidesk.global.model.table.UserAccountTable

object KomgaConnectionTable : Table("komga_connection") {
    val user = reference("user_id", UserAccountTable, onDelete = ReferenceOption.CASCADE)
    val enabled = bool("enabled").default(false)
    val apiUrl = varchar("api_url", 2048).default("")
    val browserUrl = varchar("browser_url", 2048).default("")
    val identity = varchar("identity", 36)
    override val primaryKey = PrimaryKey(user)
}

object KomgaLinkTable : Table("komga_link") {
    val user = reference("user_id", UserAccountTable, onDelete = ReferenceOption.CASCADE)
    val manga = reference("manga_id", MangaTable, onDelete = ReferenceOption.CASCADE)
    val identity = varchar("identity", 36)
    val series = varchar("series_id", 256)
    val title = text("title")
    override val primaryKey = PrimaryKey(user, manga)
}
