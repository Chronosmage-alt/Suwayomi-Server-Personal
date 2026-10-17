package suwayomi.tachidesk.graphql.mutations

import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.directives.RequirePermissions
import suwayomi.tachidesk.graphql.types.AdaptiveMangaSchedule
import suwayomi.tachidesk.graphql.types.AdaptiveUpdateInfo
import suwayomi.tachidesk.graphql.types.AdaptiveUpdateMode
import suwayomi.tachidesk.graphql.types.AdaptiveUpdateSettings
import suwayomi.tachidesk.manga.impl.update.adaptive.AdaptiveOverride
import suwayomi.tachidesk.manga.impl.update.adaptive.AdaptiveUpdates
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.settings.value
import suwayomi.tachidesk.server.user.UserPermission

class AdaptiveUpdateMutation {
    @RequireAuth
    @RequirePermissions(UserPermission.MANAGE_SETTINGS)
    fun setAdaptiveUpdatesEnabled(enabled: Boolean): AdaptiveUpdateSettings {
        serverConfig.smartLibraryUpdates.value = enabled
        return AdaptiveUpdateInfo.settings(true)
    }

    @RequireAuth
    @RequirePermissions(UserPermission.MANAGE_SETTINGS)
    fun setAdaptiveSource(
        sourceId: Long,
        enabled: Boolean,
    ): AdaptiveUpdateSettings {
        AdaptiveUpdates.setSource(sourceId, enabled)
        return AdaptiveUpdateInfo.settings(true)
    }

    @RequireAuth
    @RequirePermissions(UserPermission.MANAGE_SETTINGS)
    fun setAdaptiveMangaMode(
        mangaId: Int,
        mode: AdaptiveUpdateMode,
    ): AdaptiveMangaSchedule {
        AdaptiveUpdates.setMode(mangaId, AdaptiveOverride.valueOf(mode.name))
        return AdaptiveUpdateInfo.manga(mangaId, true)
    }

    @RequireAuth
    @RequirePermissions(UserPermission.MANAGE_SETTINGS)
    fun resetAdaptiveMangaHistory(mangaId: Int): AdaptiveMangaSchedule {
        AdaptiveUpdates.resetHistory(mangaId)
        return AdaptiveUpdateInfo.manga(mangaId, true)
    }
}
