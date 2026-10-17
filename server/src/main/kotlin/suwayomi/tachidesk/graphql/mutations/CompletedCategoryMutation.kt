package suwayomi.tachidesk.graphql.mutations

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.manga.impl.CompletedCategories

class CompletedCategoryMutation {
    @RequireAuth
    fun setCompletedCategorySettings(
        @GraphQLIgnore userId: Int,
        enabled: Boolean,
        categoryId: Int? = null,
    ) = CompletedCategories.configure(userId, enabled, categoryId)

    @RequireAuth
    fun organiseCompletedManga(
        @GraphQLIgnore userId: Int,
        categoryId: Int,
    ) = CompletedCategories.organise(userId, categoryId, true)
}
