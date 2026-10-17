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
    ) = ObservedChapter("/$number$suffix", number.toFloat(), date)

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
}
