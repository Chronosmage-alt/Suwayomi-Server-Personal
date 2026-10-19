package suwayomi.tachidesk.manga.impl.komga

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KomgaMatchingTest {
    private val teacher = KomgaIndexedSeries("1", "Best Teacher", listOf("Great Teacher"), listOf("A Writer"))

    @Test fun exactCaseAndUnicodeWhitespace() {
        assertEquals("MATCH", KomgaMatching.match(" BEST\u00a0 teacher  ", emptyList(), null, listOf(teacher)).first)
        assertEquals("best teacher", KomgaMatching.normalize("  Best\tTeacher "))
    }

    @Test fun duplicatesAndAlternateConflictsNeverChooseOne() {
        val duplicate = teacher.copy(id = "2", title = "Different", alternateTitles = listOf("Best Teacher"))
        assertEquals("AMBIGUOUS", KomgaMatching.match("Best Teacher", emptyList(), null, listOf(teacher, duplicate)).first)
    }

    @Test fun alternateOnlyRequiresCorroborationAndConflictsRequireReview() {
        assertEquals("REVIEW", KomgaMatching.match("Great Teacher", emptyList(), null, listOf(teacher)).first)
        assertEquals("MATCH", KomgaMatching.match("Great Teacher", listOf("A Writer"), null, listOf(teacher)).first)
        assertEquals("REVIEW", KomgaMatching.match("Great Teacher", listOf("Another Writer"), null, listOf(teacher)).first)
        assertEquals(
            "REVIEW",
            KomgaMatching
                .match(
                    "Great Teacher",
                    listOf("Another Writer", "Shared Artist"),
                    null,
                    listOf(teacher.copy(creators = listOf("A Writer", "Shared Artist"))),
                ).first,
        )
        assertEquals(
            "REVIEW",
            KomgaMatching
                .match(
                    "Great Teacher",
                    emptyList(),
                    "https://example.invalid/a",
                    listOf(teacher.copy(urls = listOf("https://example.invalid/b"))),
                ).first,
        )
    }

    @Test fun uniquePrimaryTitleIsEnoughDespiteDifferentCreatorCreditsAndUrls() {
        val candidate =
            KomgaIndexedSeries(
                "absolute-regression",
                "Absolute Regression",
                creators = listOf("JP", "Jang Yeonghun (장영훈)", "Park Jin-Hwan (박진환)"),
                urls = listOf("https://source.invalid/series/other"),
            )
        val match =
            KomgaMatching.match(
                "Absolute Regression",
                listOf("\u200bJang Yeonghun", "\u200b\u200bStudio JHS"),
                "https://source.invalid/series/absolute-regression",
                listOf(candidate),
            )
        assertEquals("MATCH", match.first)
        assertEquals(candidate, match.second)
        // Even this exact primary title cannot override a second matching alias.
        val collision = candidate.copy(id = "other", title = "Another series", alternateTitles = listOf("Absolute Regression"))
        assertEquals(
            "AMBIGUOUS",
            KomgaMatching.match("Absolute Regression", emptyList(), null, listOf(candidate, collision)).first,
        )
    }

    @Test fun emptyCandidatesAndEditionPunctuationStayDistinct() {
        assertEquals("UNMATCHED", KomgaMatching.match("Missing", emptyList(), null, emptyList()).first)
        assertNull(KomgaMatching.match("Missing", emptyList(), null, emptyList()).second)
        assertEquals(setOf("best teacher", "great teacher"), KomgaMatching.titles(teacher))
        assertEquals("best teacher: 2", KomgaMatching.normalize("Best Teacher: 2"))
    }

    @Test fun authoritativeUrlHostsIgnoreCaseAndFragmentsWithoutIgnoringDifferentSeriesPaths() {
        val candidate = teacher.copy(urls = listOf("https://SOURCE.invalid/series/1#metadata"))
        assertEquals("MATCH", KomgaMatching.match("Great Teacher", emptyList(), "https://source.invalid/series/1", listOf(candidate)).first)
        assertEquals(
            "REVIEW",
            KomgaMatching.match("Great Teacher", emptyList(), "https://source.invalid/series/2", listOf(candidate)).first,
        )
    }

    @Test fun largeTitleIndexRetainsCollisions() {
        val series = (1..50_000).map { KomgaIndexedSeries("$it", "Title $it") } + teacher + teacher.copy(id = "duplicate")
        val index = series.flatMap { entry -> KomgaMatching.titles(entry).map { it to entry } }.groupBy({ it.first }, { it.second })
        assertEquals(2, index["best teacher"]?.size)
        assertEquals("MATCH", KomgaMatching.match("Title 49999", emptyList(), null, index["title 49999"].orEmpty()).first)
    }
}
