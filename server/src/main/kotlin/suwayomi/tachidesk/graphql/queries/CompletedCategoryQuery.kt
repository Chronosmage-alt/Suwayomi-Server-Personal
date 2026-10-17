package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.manga.impl.CompletedCategories

class CompletedCategoryQuery {
    @RequireAuth
    fun completedCategorySettings(
        @GraphQLIgnore userId: Int,
    ) = CompletedCategories.settings(userId)

    @RequireAuth
    fun completedCategoryPreview(
        @GraphQLIgnore userId: Int,
        categoryId: Int,
    ) = CompletedCategories.organise(userId, categoryId, false)
}
