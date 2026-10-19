package suwayomi.tachidesk.graphql.mutations

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.graphql.directives.RequirePermissions
import suwayomi.tachidesk.manga.impl.komga.Komga
import suwayomi.tachidesk.manga.impl.komga.KomgaIndex
import suwayomi.tachidesk.server.JavalinSetup.future
import suwayomi.tachidesk.server.user.UserPermission

class KomgaMutation {
    @RequireAuth
    @RequirePermissions(UserPermission.MANAGE_SETTINGS)
    fun scanKomgaLibrary(
        @GraphQLIgnore userId: Int,
    ) = KomgaIndex.start(userId)

    @RequireAuth
    @RequirePermissions(UserPermission.MANAGE_SETTINGS)
    fun setKomgaSettings(
        @GraphQLIgnore userId: Int,
        enabled: Boolean,
        apiUrl: String,
        browserUrl: String = "",
        autoLink: Boolean? = null,
        showCounter: Boolean? = null,
    ) = future {
        Komga.configure(userId, enabled, apiUrl, browserUrl, autoLink, showCounter).also {
            if (it.enabled && it.autoLink) KomgaIndex.start(userId)
        }
    }

    @RequireAuth
    @RequirePermissions(UserPermission.MANAGE_SETTINGS)
    fun testKomgaConnection(
        @GraphQLIgnore userId: Int,
    ) = future { Komga.testConnection(userId) }

    @RequireAuth
    fun linkKomgaSeries(
        @GraphQLIgnore userId: Int,
        mangaId: Int,
        seriesId: String,
    ) = future { Komga.link(userId, mangaId, seriesId) }

    @RequireAuth
    fun unlinkKomgaSeries(
        @GraphQLIgnore userId: Int,
        mangaId: Int,
    ) = future { Komga.unlink(userId, mangaId) }

    @RequireAuth
    fun refreshKomgaManga(
        @GraphQLIgnore userId: Int,
        mangaId: Int,
    ) = future { Komga.manga(userId, mangaId, true) }
}
