package suwayomi.tachidesk.manga.impl.update.adaptive

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdaptiveScheduleTest {
    private val day = AdaptiveSchedule.DAY

    private fun chapter(
        number: Int,
        date: Long = 0,
        suffix: String = "",
    ) = ObservedChapter("/$number$suffix", number.toFloat(), date, ChapterDateOrigin.EXTENSION_REPORTED)

    private fun learn(days: List<Int>): AdaptiveState {
        var chapters = listOf(chapter(1))
        var state = AdaptiveSchedule.observe(AdaptiveState(), emptyList(), chapters, day)
        days.forEachIndexed { index, date ->
            val now = (date + 2L) * day
            val next = chapters + chapter(index + 2, now)
            state = AdaptiveSchedule.observe(state.copy(lastCheck = now - day / 2), chapters, next, now)
            chapters = next
        }
        return state
    }

    @Test fun insufficientHistoryKeepsNormalSchedule() {
        val state = learn(listOf(0, 7, 14))
        assertEquals(0L, state.interval)
        assertTrue(AdaptiveSchedule.due(state, 0))
    }

    @Test fun dailyWeeklyMonthlyAndIrregular() {
        for (interval in listOf(1, 7, 30)) assertEquals(interval * day, learn(listOf(0, interval, 2 * interval, 3 * interval)).interval)
        assertEquals(0L, learn(listOf(0, 2, 11, 14)).interval)
    }

    @Test fun controls() {
        for (mode in AdaptiveOverride.entries) assertFalse(AdaptiveSchedule.enabled(false, true, mode))
        assertFalse(AdaptiveSchedule.enabled(true, true, AdaptiveOverride.NORMAL))
        assertTrue(AdaptiveSchedule.enabled(true, false, AdaptiveOverride.SMART))
        assertFalse(AdaptiveSchedule.enabled(true, false, AdaptiveOverride.FOLLOW_SOURCE))
    }

    @Test fun bulkDuplicatesBackfillsAndFallbackDates() {
        val state = AdaptiveState(lastCheck = day)
        val old = listOf(chapter(10))
        for (new in listOf(listOf(chapter(10, 2 * day, "other")), listOf(chapter(3, 2 * day)), (11..20).map { chapter(it, 2 * day) })) {
            assertTrue(AdaptiveSchedule.observe(state, old, old + new, 2 * day).observations.isEmpty())
        }
        val batch = AdaptiveSchedule.observe(state, old, old + listOf(chapter(11), chapter(12)), 2 * day)
        assertEquals(1, batch.observations.size)
        assertFalse(batch.observations.single().sourceDate)
        assertTrue(AdaptiveSchedule.observe(state, old, old + chapter(11), 10 * day).observations.isEmpty())
    }

    @Test fun fasterPatternReturnsToLearningAndHiatusIsBounded() {
        val state = learn(listOf(0, 7, 14, 21))
        val now = state.lastDiscovery + 2 * day
        val faster =
            AdaptiveSchedule.observe(
                state.copy(lastCheck = now - day / 2),
                listOf(chapter(10)),
                listOf(chapter(10), chapter(11, now)),
                now,
            )
        assertEquals(AdaptivePhase.LEARNING, faster.phase)
        assertEquals(0L, faster.interval)
        val hiatus = AdaptiveSchedule.observe(state, listOf(chapter(10)), listOf(chapter(10)), state.lastDiscovery + 90 * day)
        assertEquals(AdaptivePhase.BACKOFF, hiatus.phase)
        assertEquals(14 * day, hiatus.nextCheck - hiatus.lastCheck)
    }

    @Test fun unchangedGuardDoesNotMoveReleaseAnchorAndDailyStaysFrequent() {
        val weekly = learn(listOf(0, 7, 14, 21))
        val now = weekly.lastDiscovery + 4 * day
        val guard = AdaptiveSchedule.observe(weekly, listOf(chapter(10)), listOf(chapter(10)), now)
        assertEquals(weekly.observations, guard.observations)
        assertEquals(weekly.lastDiscovery + 7 * day, guard.nextCheck)
        assertFalse(AdaptiveSchedule.due(guard, now))
        assertTrue(AdaptiveSchedule.due(learn(listOf(0, 1, 2, 3)), 0))
    }

    @Test fun ambiguousCadenceChangeNeedsTwoObservationsThenFourToRelearn() {
        val weekly = learn(listOf(0, 7, 14, 21))
        var now = weekly.lastDiscovery + 4 * day
        var old = listOf(chapter(10))
        var next = old + chapter(11, now)
        var state = AdaptiveSchedule.observe(weekly.copy(lastCheck = now - day / 2), old, next, now)
        assertEquals(7 * day, state.interval)
        assertEquals(1, state.conflicts)
        repeat(4) { index ->
            old = next
            now += 4 * day
            next = old + chapter(12 + index, now)
            state = AdaptiveSchedule.observe(state.copy(lastCheck = now - day / 2), old, next, now)
            if (index < 3) assertEquals(0L, state.interval)
        }
        assertEquals(4 * day, state.interval)
    }

    @Test fun unknownChapterNumbersAndBulkImportsResetLearnedTiming() {
        val weekly = learn(listOf(0, 7, 14, 21))
        val old = listOf(chapter(10))
        val now = weekly.lastDiscovery + day
        for (added in listOf(listOf(ObservedChapter("/unknown", -1f, now)), (11..20).map { chapter(it, now) })) {
            val result = AdaptiveSchedule.observe(weekly, old, old + added, now)
            assertEquals(0L, result.interval)
            assertEquals(AdaptivePhase.LEARNING, result.phase)
        }
    }

    private fun historical(interval: Int = 7) = (1..6).map { chapter(it, (10L + it * interval) * day) }

    @Test fun historicalThresholdsAndUnreliableTails() {
        val history = historical()
        val now = 53 * day
        assertEquals(7 * day, AdaptiveSchedule.historicalCandidate(history, now)?.interval)
        assertEquals(null, AdaptiveSchedule.historicalCandidate(history.take(5), now))
        assertEquals(null, AdaptiveSchedule.historicalCandidate(history, 63 * day))
        assertEquals(null, AdaptiveSchedule.historicalCandidate(history + chapter(7), now))
        assertEquals(null, AdaptiveSchedule.historicalCandidate(history + chapter(7, 40 * day), now))
        assertEquals(null, AdaptiveSchedule.historicalCandidate(history + chapter(6, 49 * day, "duplicate"), now))
        assertEquals(7 * day, AdaptiveSchedule.historicalCandidate(history + chapter(6, 52 * day, "duplicate"), now)?.interval)
        assertEquals(null, AdaptiveSchedule.historicalCandidate(historical(2), 22 * day))
        assertEquals(30 * day, AdaptiveSchedule.historicalCandidate(historical(30), 191 * day)?.interval)
        val jitter = history.mapIndexed { index, chapter -> chapter.copy(uploadDate = chapter.uploadDate + (index % 2) * day / 2) }
        assertEquals(7 * day + day / 2, AdaptiveSchedule.historicalCandidate(jitter, 53 * day)?.interval)
        val bulk = (1..24).map { chapter(it, (10L + (it - 1) / 4 * 7) * day) }
        assertEquals(null, AdaptiveSchedule.historicalCandidate(bulk, now))
        val batches = (1..12).map { chapter(it, (10L + (it - 1) / 2 * 7) * day) }
        assertEquals(7 * day, AdaptiveSchedule.historicalCandidate(batches, now)?.interval)
    }

    @Test fun historicalConfirmationKeepsNormalChecksUntilTwoNewReleases() {
        var history = historical()
        var state = AdaptiveSchedule.observe(AdaptiveState(), emptyList(), history, 53 * day)
        assertEquals(0, state.historical?.confirmations)
        assertTrue(state.observations.isEmpty())
        repeat(2) { index ->
            val now = (59L + 7 * index) * day
            val next = history + chapter(7 + index, now)
            state = AdaptiveSchedule.observe(state.copy(lastCheck = now - day / 2), history, next, now)
            history = next
            assertEquals(index + 1, state.observations.size)
            if (index == 0) {
                assertEquals(1, state.historical?.confirmations)
                assertEquals(0L, state.interval)
                assertTrue(AdaptiveSchedule.due(state, now))
            }
        }
        assertEquals(7 * day, state.interval)
        assertEquals(null, state.historical)
    }

    @Test fun rejectedHistoryDoesNotReseedAndFreshLearningContinues() {
        var history = historical()
        var state = AdaptiveSchedule.observe(AdaptiveState(), emptyList(), history, 53 * day)
        repeat(4) { index ->
            val now = (55L + 4 * index) * day
            val next = history + chapter(7 + index, now)
            state = AdaptiveSchedule.observe(state.copy(lastCheck = now - day / 2), history, next, now)
            history = next
            assertEquals(null, state.historical)
            assertEquals(index + 1, state.observations.size)
        }
        assertEquals(4 * day, state.interval)
        val seed = AdaptiveSchedule.observe(AdaptiveState(), emptyList(), historical(), 53 * day)
        val expired = AdaptiveSchedule.observe(seed, historical(), historical(), 64 * day)
        assertEquals(null, expired.historical)
        assertEquals(null, AdaptiveSchedule.observe(expired, historical(), historical(), 65 * day).historical)
    }

    @Test fun resetSuppressesExistingHistoryAndOldJsonHasSafeDefaults() {
        val old =
            kotlinx.serialization.json.Json
                .decodeFromString<AdaptiveState>("""{"lastCheck":123}""")
        assertEquals(null, old.historical)
        assertEquals(null, old.historicalAttemptHigh)
        val reset = AdaptiveSchedule.observe(AdaptiveState(historicalDisabled = true), historical(), historical(), 53 * day)
        assertEquals(null, reset.historical)
        assertEquals(6f, reset.historicalAttemptHigh)
        assertEquals(null, AdaptiveSchedule.observe(reset, historical(), historical(), 54 * day).historical)
    }

    @Test fun historicalGroupingAndRecencyBoundaries() {
        val history = historical()
        val latest = 52 * day
        val tolerance = 7 * day / 5
        assertTrue(AdaptiveSchedule.historicalCandidate(history, latest + 7 * day + tolerance) != null)
        assertEquals(null, AdaptiveSchedule.historicalCandidate(history, latest + 7 * day + tolerance + 1))
        assertEquals(null, AdaptiveSchedule.historicalCandidate(history, latest - 1))
        assertTrue(AdaptiveSchedule.historicalCandidate(history + chapter(6, latest + day / 2, "other"), latest + day) != null)
        assertEquals(null, AdaptiveSchedule.historicalCandidate(history + chapter(6, latest + day / 2 + 1, "other"), latest + day))
        val pairs = (1..12).map { number -> chapter(number, (10L + (number - 1) / 2 * 7) * day + (number - 1) % 2 * day) }
        assertTrue(AdaptiveSchedule.historicalCandidate(pairs, 53 * day) != null)
        val irregular =
            history.mapIndexed { index, chapter ->
                if (index ==
                    3
                ) {
                    chapter.copy(uploadDate = chapter.uploadDate + 3 * day)
                } else {
                    chapter
                }
            }
        assertEquals(null, AdaptiveSchedule.historicalCandidate(irregular, 53 * day))
    }

    @Test fun historicalImportsDuplicatesBackfillsAndRepeatedRefreshesNeverConfirm() {
        val history = historical()
        var state = AdaptiveSchedule.observe(AdaptiveState(), emptyList(), history, 53 * day)
        repeat(3) {
            state = AdaptiveSchedule.observe(state, history, history, 53 * day + it)
            assertTrue(state.observations.isEmpty())
            assertEquals(0, state.historical?.confirmations)
            assertEquals(0L, state.interval)
        }
        for (extra in listOf(chapter(6, 52 * day, "scanlator"), chapter(0, 10 * day))) {
            val refreshed = AdaptiveSchedule.observe(state, history, history + extra, 54 * day)
            assertTrue(refreshed.observations.isEmpty())
            assertEquals(0L, refreshed.interval)
            assertTrue(AdaptiveSchedule.due(refreshed, 54 * day))
        }
        val next = history + chapter(7, 59 * day)
        val first = AdaptiveSchedule.observe(state.copy(lastCheck = 59 * day - day / 2), history, next, 59 * day)
        val imported = AdaptiveSchedule.observe(first, emptyList(), next, 60 * day)
        assertTrue(imported.observations.isEmpty())
        assertEquals(null, imported.historical)
        assertEquals(0L, imported.interval)
    }

    @Test fun releaseThatCreatesCandidateDoesNotAlsoConfirmIt() {
        val old = historical().take(5)
        val baseline = AdaptiveState(lastCheck = 52 * day - day / 2)
        val seeded = AdaptiveSchedule.observe(baseline, old, historical(), 52 * day)
        assertEquals(1, seeded.observations.size)
        assertEquals(0, seeded.historical?.confirmations)
        val first =
            AdaptiveSchedule.observe(
                seeded.copy(lastCheck = 59 * day - day / 2),
                historical(),
                historical() + chapter(7, 59 * day),
                59 * day,
            )
        assertEquals(1, first.historical?.confirmations)
        assertEquals(0L, first.interval)
    }

    @Test fun missingHistoryAfterFirstConfirmationFallsBackWithoutDiscardingFreshReleases() {
        var history = historical()
        var state = AdaptiveSchedule.observe(AdaptiveState(), emptyList(), history, 53 * day)
        repeat(4) { index ->
            val now = (59L + index * 7) * day
            val next = (history + chapter(7 + index, now)).map { if (index > 0) it.copy(uploadDate = 0) else it }
            state = AdaptiveSchedule.observe(state.copy(lastCheck = now - day / 2), history, next, now)
            history = next
            assertEquals(index + 1, state.observations.size)
            if (index == 0) assertEquals(1, state.historical?.confirmations)
            if (index in 1..2) {
                assertEquals(null, state.historical)
                assertEquals(0L, state.interval)
                assertTrue(AdaptiveSchedule.due(state, now))
            }
        }
        assertEquals(7 * day, state.interval)
    }

    @Test fun conflictingDuplicateDatesCannotConfirmHistoricalSchedule() {
        val history = historical()
        val seed = AdaptiveSchedule.observe(AdaptiveState(), emptyList(), history, 53 * day)
        val next = history + chapter(7, 59 * day) + chapter(7, 56 * day, "scanlator")
        val result = AdaptiveSchedule.observe(seed.copy(lastCheck = 59 * day - day / 2), history, next, 59 * day)
        assertEquals(null, result.historical)
        assertEquals(0L, result.interval)
        assertEquals(1, result.observations.size)
    }

    @Test fun shortenedListsCannotLowerRejectedWatermarkEvenAcrossSerialization() {
        val history = historical()
        val seed = AdaptiveSchedule.observe(AdaptiveState(), emptyList(), history, 53 * day)
        val rejected = AdaptiveSchedule.observe(seed, history, history, 64 * day)
        val shortened = listOf(chapter(1, 17 * day))
        val duplicate = shortened + chapter(1, 17 * day, "other")
        val result = AdaptiveSchedule.observe(rejected, shortened, duplicate, 64 * day)
        assertEquals(6f, result.historicalAttemptHigh)
        val json = kotlinx.serialization.json.Json
        val reloaded = json.decodeFromString<AdaptiveState>(json.encodeToString(AdaptiveState.serializer(), result))
        val restored = AdaptiveSchedule.observe(reloaded, emptyList(), history, 54 * day)
        assertEquals(null, restored.historical)
        assertEquals(6f, restored.historicalAttemptHigh)
        val shrinkDuringConfirmation = AdaptiveSchedule.observe(seed, history, duplicate, 54 * day)
        assertEquals(6f, shrinkDuringConfirmation.historicalAttemptHigh)
        assertEquals(null, shrinkDuringConfirmation.historical)
    }
}
