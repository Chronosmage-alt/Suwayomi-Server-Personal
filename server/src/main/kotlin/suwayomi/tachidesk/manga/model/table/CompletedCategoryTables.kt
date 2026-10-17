package suwayomi.tachidesk.manga.model.table

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.Table
import suwayomi.tachidesk.global.model.table.UserAccountTable

object CompletedCategoryRuleTable : Table("completed_category_rule") {
    val user = reference("user_id", UserAccountTable, onDelete = ReferenceOption.CASCADE)
    val enabled = bool("enabled").default(false)
    val destinationSelected = bool("destination_selected").default(false)
    val category = reference("category_id", CategoryTable, onDelete = ReferenceOption.SET_NULL).nullable()
    override val primaryKey = PrimaryKey(user)
}

object CompletedCategoryHandledTable : Table("completed_category_handled") {
    val user = reference("user_id", UserAccountTable, onDelete = ReferenceOption.CASCADE)
    val manga = reference("manga_id", MangaTable, onDelete = ReferenceOption.CASCADE)
    val category = reference("category_id", CategoryTable, onDelete = ReferenceOption.CASCADE)
    override val primaryKey = PrimaryKey(user, manga, category)
}
