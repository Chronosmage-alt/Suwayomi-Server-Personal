package suwayomi.tachidesk.manga.impl.komga

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KomgaPaginationTest {
    private fun page(
        metadata: String,
        content: String = "{}",
        number: Int = 0,
    ) = KomgaPagination.read(Json.parseToJsonElement("""{"content":[$content],$metadata}""").jsonObject, number, 200)

    @Test fun contradictoryMetadataIsRejectedInsteadOfCompletingInventory() {
        for (metadata in listOf(
            """"last":false,"totalPages":1""",
            """"last":true,"totalPages":2""",
            """"last":true,"totalPages":0""",
            """"last":true,"number":1""",
            """"last":true,"numberOfElements":2""",
            """"last":true,"empty":true""",
            """"last":true,"totalElements":0""",
            """"last":true,"totalElements":2,"size":1,"totalPages":1""",
            """"last":true,"totalElements":2,"size":2""",
            """"last":true,"size":0""",
            """"last":true,"totalPages":null""",
        )) {
            assertEquals("INVALID_RESPONSE", assertFailsWith<KomgaFailure> { page(metadata) }.code)
        }
        assertFailsWith<KomgaFailure> { page(""""last":false,"totalPages":2""", "") }
    }

    @Test fun consistentPagesAndEmptyCollectionsAreAccepted() {
        assertTrue(page(""""last":false,"number":0,"size":1,"totalElements":2,"totalPages":2""").hasNext)
        assertFalse(page(""""last":true,"number":1,"size":1,"totalElements":2,"totalPages":2""", number = 1).hasNext)
        assertFalse(page(""""last":true,"size":200,"totalElements":0,"totalPages":0""", "").hasNext)
        assertFalse(page(""""last":true""").hasNext)
    }
}
