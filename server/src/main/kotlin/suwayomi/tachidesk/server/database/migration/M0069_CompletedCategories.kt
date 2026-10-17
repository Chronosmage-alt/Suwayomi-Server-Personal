package suwayomi.tachidesk.server.database.migration

import de.neonew.exposed.migrations.helpers.AddTableMigration
import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable

@Suppress("ClassName", "unused")
class M0069_CompletedCategories : AddTableMigration() {
    override val tables: Array<Table> get() = arrayOf(Rule, Handled)

    private object User : IntIdTable("useraccount")

    private object Manga : IntIdTable("manga")

    private object Category : IntIdTable("category")

    private object Rule : Table("completed_category_rule") {
        val user = reference("user_id", User, onDelete = ReferenceOption.CASCADE)
        val enabled = bool("enabled").default(false)
        val destinationSelected = bool("destination_selected").default(false)
        val category = reference("category_id", Category, onDelete = ReferenceOption.SET_NULL).nullable()
        override val primaryKey = PrimaryKey(user)
    }

    private object Handled : Table("completed_category_handled") {
        val user = reference("user_id", User, onDelete = ReferenceOption.CASCADE)
        val manga = reference("manga_id", Manga, onDelete = ReferenceOption.CASCADE)
        val category = reference("category_id", Category, onDelete = ReferenceOption.CASCADE)
        override val primaryKey = PrimaryKey(user, manga, category)
    }
}
