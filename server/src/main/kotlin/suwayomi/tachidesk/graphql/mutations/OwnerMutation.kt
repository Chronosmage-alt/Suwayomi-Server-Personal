/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package suwayomi.tachidesk.graphql.mutations

import graphql.schema.DataFetchingEnvironment
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.global.impl.util.Jwt
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.server.JavalinSetup.future
import java.util.concurrent.CompletableFuture

/** The personal server has one owner. Account creation and administration are not API operations. */
class OwnerMutation {
    fun login(
        dataFetchingEnvironment: DataFetchingEnvironment,
        input: UserMutation.LoginInput,
    ): CompletableFuture<UserMutation.LoginPayload> {
        val ownerName =
            transaction {
                UserAccountTable
                    .select(UserAccountTable.username)
                    .where { UserAccountTable.id eq 1 }
                    .single()[UserAccountTable.username]
            }
        require(ownerName.equals(input.username, ignoreCase = true)) { "Incorrect username or password." }
        return UserMutation().login(dataFetchingEnvironment, input)
    }

    fun refreshToken(input: UserMutation.RefreshTokenInput): CompletableFuture<UserMutation.RefreshTokenPayload> =
        future {
            UserMutation.RefreshTokenPayload(
                clientMutationId = input.clientMutationId,
                accessToken = Jwt.refreshJwt(input.refreshToken, expectedUserId = 1),
            )
        }
}
