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

    fun observe(
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
