package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.types.AdaptiveMangaSchedule
import suwayomi.tachidesk.graphql.types.AdaptiveUpdateInfo
import suwayomi.tachidesk.graphql.types.AdaptiveUpdateSettings
import suwayomi.tachidesk.server.user.UserPermission
import suwayomi.tachidesk.server.user.hasPermission

class AdaptiveUpdateQuery {
    @RequireAuth
    fun adaptiveUpdateSettings(
        @GraphQLIgnore permissions: List<UserPermission>,
    ): AdaptiveUpdateSettings = AdaptiveUpdateInfo.settings(permissions.hasPermission(UserPermission.MANAGE_SETTINGS))

    @RequireAuth
    fun adaptiveMangaSchedule(
        mangaId: Int,
        @GraphQLIgnore permissions: List<UserPermission>,
    ): AdaptiveMangaSchedule = AdaptiveUpdateInfo.manga(mangaId, permissions.hasPermission(UserPermission.MANAGE_SETTINGS))
}
