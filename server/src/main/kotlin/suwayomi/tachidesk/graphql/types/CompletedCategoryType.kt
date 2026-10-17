package suwayomi.tachidesk.graphql.types

data class CompletedCategoryOption(
    val id: Int,
    val name: String,
)

data class CompletedCategorySettings(
    val enabled: Boolean,
    val categoryId: Int?,
    val effective: Boolean,
    val destinationMissing: Boolean,
    val categories: List<CompletedCategoryOption>,
)

data class CompletedCategoryPreview(
    val categoryId: Int,
    val toAdd: Int,
    val alreadyPresent: Int,
    val skipped: Int,
)
