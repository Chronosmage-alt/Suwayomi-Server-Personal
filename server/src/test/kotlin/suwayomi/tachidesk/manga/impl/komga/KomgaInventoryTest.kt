package suwayomi.tachidesk.manga.impl.komga

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class KomgaInventoryTest {
    @Test fun encodedSeparatorsAndTrailingSlashesNeverCollapseDifferentChapterUrls() {
        for ((stored, remote) in listOf(
            "https://source.invalid/a%2Fb" to "https://source.invalid/a/b",
            "https://source.invalid/c?id=a%26b" to "https://source.invalid/c?id=a&b",
            "https://source.invalid/c?id=a%3Db" to "https://source.invalid/c?id=a=b",
            "https://source.invalid/a" to "https://source.invalid/a/",
        )) {
            val book = """[{"id":"b","metadata":{"links":[{"url":"$remote"}]}}]"""
            val value = KomgaInventory.compare(snapshot(book), listOf(chapter("1", path = stored)))
            assertEquals(emptyList(), value.confirmedNumbers, "$stored must differ from $remote")
            assertNull(value.missingCount)
        }
    }

    @Test fun identicalEncodedUrlsMatchWithoutDecodingReservedCharacters() {
        val books = """[{"id":"b","metadata":{"links":[{"url":"https://source.invalid/a%2Fb?q=a%26b#ignored"}]}}]"""
        val value = KomgaInventory.compare(snapshot(books), listOf(chapter("1", path = "https://SOURCE.invalid/a%2fb?q=a%26b")))
        assertEquals(listOf("1"), value.confirmedNumbers)
        assertEquals(0, value.missingCount)
    }

    private fun snapshot(
        books: String,
        complete: Boolean = true,
    ) = KomgaSnapshot(
        Json.parseToJsonElement("""{"id":"s","metadata":{"title":"Fixture"}}""").jsonObject,
        Json.parseToJsonElement("""{"books":$books}""").jsonObject.objects("books"),
        complete,
        1000,
    )

    private fun chapter(
        number: String?,
        downloaded: Boolean = false,
        path: String = "https://source.invalid/$number",
    ) = KomgaLocalChapter(number, listOf(path), downloaded)

    @Test fun numericMetadataAloneIsNotProofOfAChapter() {
        val value = KomgaInventory.compare(snapshot("""[{"id":"b","metadata":{"number":"1"}}]"""), listOf(chapter("1")))
        assertEquals(listOf("1"), value.numberHints)
        assertEquals(emptyList(), value.confirmedNumbers)
        assertNull(value.missingCount)
        assertEquals(1, value.ambiguousBooks)
    }

    @Test fun storedUrlsConfirmDecimalsDeduplicateEditionsAndExposeFlagDiscrepancies() {
        val books = """[
            {"id":"a","metadata":{"number":"1.50","links":[{"url":"https://source.invalid/1.5"}]},"readProgress":{"completed":true,"page":20}},
            {"id":"b","metadata":{"number":"1.5","links":[{"url":"https://source.invalid/1.5"}]},"readProgress":{"completed":false,"page":4}}
        ]"""
        val value = KomgaInventory.compare(snapshot(books), listOf(chapter("1.5"), chapter("1.5"), chapter("2", true)))
        assertEquals(listOf("1.5"), value.confirmedNumbers)
        assertEquals(1, value.duplicateSourceEntries)
        assertEquals(1, value.missingCount)
        assertEquals(listOf("1.5"), value.confirmedButNotDownloaded)
        assertEquals(listOf("2"), value.downloadedButNotMatched)
        assertEquals(1, value.readBooks)
        assertEquals(1, value.inProgressBooks)
    }

    @Test fun rangesSpecialsUnknownChaptersAndTruncatedPagesRemainUncertain() {
        val value =
            KomgaInventory.compare(
                snapshot("""[{"id":"a","metadata":{"number":"1-10"}},{"id":"b","metadata":{"number":"Special"}}]""", false),
                listOf(chapter(null)),
            )
        assertEquals(2, value.ambiguousBooks)
        assertEquals(1, value.unknownSourceEntries)
        assertNull(value.missingCount)
        assertFalse(value.inventoryComplete)
        assertNull(value.bookCount)
    }

    @Test fun multipleExplicitChapterUrlsCanRepresentOneArchiveButConflictingUrlsCannotProveNumbers() {
        val value =
            KomgaInventory.compare(
                snapshot("""[{"id":"a","metadata":{"links":[{"url":"https://source.invalid/1"},{"url":"https://source.invalid/2"}]}}]"""),
                listOf(chapter("1"), chapter("2")),
            )
        assertEquals(listOf("1", "2"), value.confirmedNumbers)
        assertEquals(0, value.missingCount)
        val conflict =
            KomgaInventory.compare(
                snapshot("""[{"id":"a","metadata":{"links":[{"url":"https://source.invalid/shared"}]}}]"""),
                listOf(chapter("1", path = "https://source.invalid/shared"), chapter("2", path = "https://source.invalid/shared")),
            )
        assertEquals(emptyList(), conflict.confirmedNumbers)
        assertNull(conflict.missingCount)
    }

    @Test fun extraHintsAndDeletedBooksDoNotInventSourceChapters() {
        val value =
            KomgaInventory.compare(
                snapshot("""[{"id":"a","metadata":{"number":"100.5"}},{"id":"b","deleted":true,"metadata":{"number":"2"}}]"""),
                listOf(chapter("1")),
            )
        assertEquals(listOf("100.5"), value.extraNumberHints)
        assertEquals(1, value.books.size)
        assertNull(value.missingCount)
    }
}
