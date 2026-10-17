package suwayomi.tachidesk.manga.impl.update.adaptive

import kotlinx.serialization.Serializable
import kotlin.math.abs

enum class AdaptiveOverride { FOLLOW_SOURCE, SMART, NORMAL }

enum class AdaptivePhase { LEARNING, SCHEDULED, BACKOFF }

@Serializable
data class ReleaseObservation(
    val discoveredAt: Long,
    val releaseAt: Long,
    val sourceDate: Boolean,
)

@Serializable
data class HistoricalCandidate(
    val interval: Long,
    val anchor: Long,
    val highNumber: Float,
    val confirmations: Int = 0,
)

@Serializable
data class AdaptiveState(
    val version: Int = 1,
    val lastCheck: Long = 0,
    val lastDiscovery: Long = 0,
    val interval: Long = 0,
    val nextCheck: Long = 0,
    val emptyChecks: Int = 0,
    val phase: AdaptivePhase = AdaptivePhase.LEARNING,
    val observations: List<ReleaseObservation> = emptyList(),
    val conflicts: Int = 0,
    val historical: HistoricalCandidate? = null,
    val historicalAttemptHigh: Float? = null,
    val historicalDisabled: Boolean = false,
)

data class ObservedChapter(
    val url: String,
    val number: Float,
    val uploadDate: Long,
)

/** All times are UTC epoch milliseconds. No source or database access. */
object AdaptiveSchedule {
    const val DAY = 86_400_000L
    const val MAX_GAP = 14 * DAY
    private const val MAX_HISTORY = 12

    fun enabled(
        master: Boolean,
        source: Boolean,
        override: AdaptiveOverride,
    ): Boolean = master && override != AdaptiveOverride.NORMAL && (override == AdaptiveOverride.SMART || source)

    fun due(
        state: AdaptiveState,
        now: Long,
    ): Boolean = state.interval == 0L || state.nextCheck <= now

    /** History is a hint only: normal observations accumulate independently. */
    fun observe(
        state: AdaptiveState,
        previous: List<ObservedChapter>,
        fetched: List<ObservedChapter>,
        now: Long,
    ): AdaptiveState {
        var result = observeFresh(state, previous, fetched, now)
        if (result.interval > 0) return result.copy(historical = null)
        val high = fetched.map { it.number }.filter { it.isFinite() && it >= 0 }.maxOrNull()
        val watermark = listOfNotNull(high, state.historicalAttemptHigh, state.historical?.highNumber).maxOrNull()
        val oldUrls = previous.map { it.url }.toSet()
        val added = fetched.distinctBy { it.url }.filter { it.url !in oldUrls }
        val candidate = state.historical
        if (candidate != null) {
            val event = result.observations.lastOrNull()?.takeIf { it.discoveredAt == now && it != state.observations.lastOrNull() }
            val ambiguous = added.isNotEmpty() && result.observations.isEmpty()
            // Recheck raw evidence: fallback/discovery dates must not rescue unreliable history.
            val evidence = historicalCandidate(fetched, now)
            val unreliable = evidence == null || abs(evidence.interval - candidate.interval) > tolerance(candidate.interval)
            val expired = now > candidate.anchor + candidate.interval + tolerance(candidate.interval) + 2 * DAY
            if (ambiguous || unreliable || expired || (
                    event != null &&
                        (
                            high == null || added.none { it.number.isFinite() && it.number > candidate.highNumber } ||
                                abs(event.releaseAt - candidate.anchor - candidate.interval) > tolerance(candidate.interval)
                        )
                )
            ) {
                return result.copy(historical = null, historicalAttemptHigh = watermark)
            }
            if (event == null) {
                return result.copy(historical = candidate.copy(highNumber = watermark!!), historicalAttemptHigh = watermark)
            }
            val confirmed = candidate.copy(anchor = event.releaseAt, highNumber = high!!, confirmations = candidate.confirmations + 1)
            if (confirmed.confirmations < 2) return result.copy(historical = confirmed, historicalAttemptHigh = watermark)
            return result.copy(
                historical = null,
                interval = candidate.interval,
                nextCheck =
                    if (candidate.interval >
                        2 * DAY
                    ) {
                        minOf(event.releaseAt + candidate.interval, now + guard(candidate.interval))
                    } else {
                        0
                    },
                phase = AdaptivePhase.SCHEDULED,
            )
        }
        if (state.historicalDisabled) return result.copy(historicalDisabled = watermark == null, historicalAttemptHigh = watermark)
        if (previous.isNotEmpty() && added.isNotEmpty() && result.observations.isEmpty()) {
            return result.copy(historicalAttemptHigh = watermark)
        }
        // After a failed attempt require six entirely newer events, rather than retrying old evidence.
        val eligible = fetched.filter { state.historicalAttemptHigh == null || it.number > state.historicalAttemptHigh }
        val hint = historicalCandidate(eligible, now) ?: return result
        result = result.copy(historical = hint, historicalAttemptHigh = high)
        return result
    }

    internal fun historicalCandidate(
        chapters: List<ObservedChapter>,
        now: Long,
    ): HistoricalCandidate? {
        // Inspect a contiguous numbered tail, never cherry-pick around unreliable recent chapters.
        val numbered = chapters.distinctBy { it.url }.groupBy { it.number }.toSortedMap()
        if (numbered.isEmpty() || numbered.keys.any { !it.isFinite() || it < 0 }) return null
        val tail = numbered.entries.toList().takeLast(24)
        val events = mutableListOf<Pair<Long, Int>>()
        for ((_, duplicates) in tail) {
            val dates = duplicates.map { it.uploadDate }
            if (dates.any { it <= 0 || it > now } || dates.max() - dates.min() > DAY / 2) return null
            val date = dates.min()
            val last = events.lastOrNull()
            if (last != null && date < last.first) return null
            if (last != null && date - last.first <= DAY) {
                if (last.second >= 3) return null
                events[events.lastIndex] = last.first to last.second + 1
            } else {
                events.add(date to 1)
            }
        }
        if (events.size < 6) return null
        val recent = events.takeLast(6).map { it.first }
        val gaps = recent.zipWithNext { a, b -> b - a }
        val interval = gaps.sorted()[2]
        // Date-only sources need up to one day of jitter; slower patterns allow 20%.
        if (interval !in (2 * DAY + 1)..(60 * DAY) || gaps.any { abs(it - interval) > tolerance(interval) }) return null
        val anchor = recent.last()
        if (now - anchor > interval + tolerance(interval)) return null
        return HistoricalCandidate(interval, anchor, numbered.lastKey())
    }

    private fun observeFresh(
        state: AdaptiveState,
        previous: List<ObservedChapter>,
        fetched: List<ObservedChapter>,
        now: Long,
    ): AdaptiveState {
        val previousNumbers = previous.map { it.number }.filter { it.isFinite() && it >= 0 }.toSet()
        val high = previousNumbers.maxOrNull()
        val oldUrls = previous.map { it.url }.toSet()
        val added = fetched.distinctBy { it.url }.filter { it.url !in oldUrls }
        val forward =
            added
                .filter { it.number.isFinite() && high != null && it.number > high }
                .distinctBy { it.number }
        // Larger batches cannot be distinguished safely from missing history in v1.
        val plausibleBatch =
            forward.size in 1..3 && added.map { it.number }.distinct().size <= 3 &&
                added.all { it.number.isFinite() && it.number >= 0 }
        val sourceDates = forward.map { it.uploadDate }
        val rawDate = sourceDates.maxOrNull() ?: 0
        val reliableDate =
            plausibleBatch &&
                sourceDates.all {
                    it > 0 && it >= state.lastCheck - DAY && it <= now + DAY
                } && (sourceDates.maxOrNull()!! - sourceDates.minOrNull()!!) <= DAY
        val discoveryReliable = state.lastCheck > 0 && now - state.lastCheck <= DAY
        val qualifies =
            previous.isNotEmpty() && high != null && state.lastCheck > 0 && plausibleBatch &&
                (reliableDate || discoveryReliable)
        val base = state.copy(lastCheck = now)
        if (!qualifies) {
            if (previous.isEmpty() || state.lastCheck == 0L || added.isNotEmpty()) {
                // Replacements/backfills and bulk changes must not fabricate a release cadence.
                return if (forward.isNotEmpty() || previous.isEmpty() ||
                    added.any { !it.number.isFinite() || it.number < 0 }
                ) {
                    base.copy(
                        interval = 0,
                        nextCheck = 0,
                        phase = AdaptivePhase.LEARNING,
                        observations = emptyList(),
                        emptyChecks = 0,
                    )
                } else {
                    base
                }
            }
            if (state.interval <= 2 * DAY) return base
            val anchor = state.observations.lastOrNull()?.releaseAt ?: state.lastDiscovery
            val expected = anchor + state.interval
            val empty = (state.emptyChecks + 1).coerceAtMost(1000)
            if (now < expected) return base.copy(emptyChecks = empty, nextCheck = minOf(expected, now + guard(state.interval)))
            if (now < expected + 2 * DAY) return base.copy(emptyChecks = empty, nextCheck = now)
            val elapsed = now - expected - 2 * DAY
            val gap =
                when {
                    elapsed < DAY -> DAY
                    elapsed < 3 * DAY -> 2 * DAY
                    elapsed < 7 * DAY -> 4 * DAY
                    elapsed < 14 * DAY -> 7 * DAY
                    else -> MAX_GAP
                }
            return base.copy(emptyChecks = empty, nextCheck = now + gap, phase = AdaptivePhase.BACKOFF)
        }
        val event = ReleaseObservation(now, if (reliableDate) rawDate.coerceAtMost(now) else now, reliableDate)
        val last = state.observations.lastOrNull()
        if (last != null && event.releaseAt - last.releaseAt < DAY / 2) return base
        val gap = last?.let { event.releaseAt - it.releaseAt }
        val conflicting = state.interval > 0 && gap != null && abs(gap - state.interval) > tolerance(state.interval)
        val faster = conflicting && gap!! < state.interval / 2
        val reset = state.phase == AdaptivePhase.BACKOFF || faster || (conflicting && state.conflicts >= 1)
        val history = (if (reset) listOf(event) else state.observations + event).takeLast(MAX_HISTORY)
        val gaps = history.takeLast(4).zipWithNext { a, b -> b.releaseAt - a.releaseAt }
        val median = gaps.sorted().getOrNull(1) ?: 0
        val learned = gaps.size == 3 && median in DAY..(60 * DAY) && gaps.all { abs(it - median) <= tolerance(median) }
        val interval =
            if (reset) {
                0
            } else if (learned) {
                median
            } else {
                state.interval
            }
        return base.copy(
            lastDiscovery = now,
            interval = interval,
            nextCheck = if (interval > 2 * DAY) minOf(event.releaseAt + interval, now + guard(interval)).coerceAtLeast(now) else 0,
            emptyChecks = 0,
            observations = history,
            conflicts = if (conflicting && !reset) state.conflicts + 1 else 0,
            phase = if (interval > 0) AdaptivePhase.SCHEDULED else AdaptivePhase.LEARNING,
        )
    }

    private fun tolerance(interval: Long) = maxOf(DAY, interval / 5)

    private fun guard(interval: Long) = minOf(interval / 2, 7 * DAY)
}
