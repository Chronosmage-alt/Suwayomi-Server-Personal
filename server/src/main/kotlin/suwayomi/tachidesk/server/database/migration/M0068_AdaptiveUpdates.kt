package suwayomi.tachidesk.server.database.migration

import de.neonew.exposed.migrations.helpers.AddTableMigration
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

@Suppress("ClassName", "unused")
class M0068_AdaptiveUpdates : AddTableMigration() {
    override val tables: Array<Table> get() = arrayOf(SourcePolicy, MangaState)

    // Freeze this migration independently of subsequent runtime model changes.
    private object Manga : IntIdTable("manga")

    private object SourcePolicy : Table("adaptive_source_policy") {
        val sourceId = long("source_id")
        val providerPackage = varchar("provider_package", 256)
        val enabled = bool("enabled").default(false)
        override val primaryKey = PrimaryKey(sourceId)
    }

    private object MangaState : Table("adaptive_manga_state") {
        val manga = reference("manga_id", Manga, onDelete = ReferenceOption.CASCADE)
        val overrideMode = varchar("override_mode", 32).default("FOLLOW_SOURCE")
        val state = text("state").default("{}")
        override val primaryKey = PrimaryKey(manga)
    }
}
