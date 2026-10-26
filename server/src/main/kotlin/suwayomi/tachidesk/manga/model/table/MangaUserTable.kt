package suwayomi.tachidesk.manga.model.table

/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/. */

import org.jetbrains.exposed.v1.core.ReferenceOption
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.core.notExists
import org.jetbrains.exposed.v1.jdbc.select
import suwayomi.tachidesk.global.model.table.UserAccountTable

object MangaUserTable : IntIdTable() {
    val manga = reference("manga", MangaTable, ReferenceOption.CASCADE)
    val user = reference("user_id", UserAccountTable, ReferenceOption.CASCADE)
    val inLibrary = bool("in_library").default(false)
    val inLibraryAt = long("in_library_at").default(0)

    // Tachiyomi reader/chapter-list bitmasks, passthrough for sync
    val viewer = integer("viewer").default(0)
    val viewerFlags = integer("viewer_flags").nullable()
    val chapterFlags = integer("chapter_flags").default(0)

    // syncyomi
    val version = long("version").default(0)
    val isSyncing = bool("is_syncing").default(false)
    val lastModifiedAt = long("last_modified_at").default(0)

    init {
        uniqueIndex(manga, user)
        index("mangauser_user_library_manga", false, user, inLibrary, manga)
    }
}

/** Library membership and category assignments are both scoped to the requesting user. */
fun uncategorizedLibraryManga(userId: Int) =
    MangaUserTable.select(MangaUserTable.manga).where {
        (MangaUserTable.user eq userId) and (MangaUserTable.inLibrary eq true) and
            notExists(
                CategoryMangaTable.select(CategoryMangaTable.manga).where {
                    (CategoryMangaTable.user eq userId) and (CategoryMangaTable.manga eq MangaUserTable.manga)
                },
            )
    }

fun MangaTable.getWithUserData(userId: Int) =
    leftJoin(
        MangaUserTable,
        onColumn = { MangaTable.id },
        otherColumn = { MangaUserTable.manga },
        additionalConstraint = { MangaUserTable.user eq userId },
    )
