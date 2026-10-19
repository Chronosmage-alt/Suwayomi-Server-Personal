package suwayomi.tachidesk.graphql.queries

import com.expediagroup.graphql.generator.annotations.GraphQLIgnore
import suwayomi.tachidesk.graphql.directives.RequireAuth
import suwayomi.tachidesk.manga.impl.komga.Komga
import suwayomi.tachidesk.server.JavalinSetup.future

class KomgaQuery {
    @RequireAuth
    fun komgaSettings(
        @GraphQLIgnore userId: Int,
    ) = Komga.settings(userId)

    @RequireAuth
    fun komgaSearch(
        @GraphQLIgnore userId: Int,
        query: String,
        page: Int = 0,
    ) = future { Komga.search(userId, query, page) }

    @RequireAuth
    fun komgaManga(
        @GraphQLIgnore userId: Int,
        mangaId: Int,
    ) = future { Komga.manga(userId, mangaId) }
}
