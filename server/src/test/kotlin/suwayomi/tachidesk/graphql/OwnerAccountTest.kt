/*
 * Copyright (C) Contributors to the Suwayomi project
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package suwayomi.tachidesk.graphql

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.global.impl.util.Jwt
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.graphql.mutations.OwnerMutation
import suwayomi.tachidesk.graphql.mutations.UserMutation
import suwayomi.tachidesk.graphql.server.GraphQLSchemaProvider
import suwayomi.tachidesk.graphql.types.AuthMode
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.user.UserCodeService
import suwayomi.tachidesk.server.user.UserType
import suwayomi.tachidesk.server.user.getUserFromToken
import suwayomi.tachidesk.test.ApplicationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OwnerAccountTest : ApplicationTest() {
    @Test fun personalSchemaHasNoAccountAdministration(): Unit =
        runBlocking {
            val schema = GraphQLSchemaProvider.getSchema()
            val mutations = assertNotNull(schema.mutationType)
            listOf("users", "user", "userCodes").forEach { assertNull(schema.queryType.getFieldDefinition(it), it) }
            listOf(
                "register",
                "updateUser",
                "createRegistrationCode",
                "redeemRegistrationCode",
                "createRecoveryCode",
                "redeemRecoveryCode",
                "revokeUserCode",
                "setPassword",
            ).forEach { assertNull(mutations.getFieldDefinition(it), it) }
            assertNotNull(mutations.getFieldDefinition("login"))
            assertNotNull(mutations.getFieldDefinition("refreshToken"))
            assertNotNull(schema.queryType.getFieldDefinition("komgaSettings"))
            assertNotNull(mutations.getFieldDefinition("setCompletedCategorySettings"))
        }

    @Test fun acceptsOnlyOwnerSessionsAndKeepsNoLoginAccess(): Unit =
        runBlocking {
            val old = serverConfig.authMode.value
            val secondary = transaction { UserCodeService.createUser("owner-session-fixture", "fixture-password", null) }
            try {
                serverConfig.authMode.value = AuthMode.NONE
                assertEquals(1, assertIs<UserType.Admin>(getUserFromToken(null)).id)
                serverConfig.authMode.value = AuthMode.UI_LOGIN
                assertIs<UserType.Visitor>(getUserFromToken(null))
                val ownerTokens = Jwt.generateJwt(1)
                assertEquals(1, assertIs<UserType.Admin>(getUserFromToken(ownerTokens.accessToken)).id)
                val otherTokens = Jwt.generateJwt(secondary)
                assertIs<UserType.Visitor>(getUserFromToken(otherTokens.accessToken))
                assertFailsWith<java.util.concurrent.ExecutionException> {
                    OwnerMutation().refreshToken(UserMutation.RefreshTokenInput(refreshToken = otherTokens.refreshToken)).get()
                }
                val refreshed = OwnerMutation().refreshToken(UserMutation.RefreshTokenInput(refreshToken = ownerTokens.refreshToken)).get()
                assertEquals(1, assertIs<UserType.Admin>(getUserFromToken(refreshed.accessToken)).id)
            } finally {
                serverConfig.authMode.value = old
                transaction { UserAccountTable.deleteWhere { id eq secondary } }
            }
        }
}
