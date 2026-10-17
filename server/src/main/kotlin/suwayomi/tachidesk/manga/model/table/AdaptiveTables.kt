package suwayomi.tachidesk.manga.model.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table

object AdaptiveSourcePolicyTable : Table("adaptive_source_policy") {
    val sourceId = long("source_id")
    val providerPackage = varchar("provider_package", 256)
    val enabled = bool("enabled").default(false)
    override val primaryKey = PrimaryKey(sourceId)
}

object AdaptiveMangaStateTable : Table("adaptive_manga_state") {
    val manga = reference("manga_id", MangaTable, onDelete = ReferenceOption.CASCADE)
    val overrideMode = varchar("override_mode", 32).default("FOLLOW_SOURCE")

    // Versioned, bounded serialized state. It is not copied from chapter fallback dates.
    val state = text("state").default("{}")
    override val primaryKey = PrimaryKey(manga)
}
