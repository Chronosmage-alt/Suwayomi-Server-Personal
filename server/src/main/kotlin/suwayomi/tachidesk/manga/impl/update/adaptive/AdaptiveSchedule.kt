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
    val historicalDiagnostic: HistoricalDiagnostic? = null,
    val dateEvidence: List<ChapterDateEvidence> = emptyList(),
    val dateEvidenceThrough: Long = 0,
)

data class ObservedChapter(
    val url: String,
    val number: Float,
    val uploadDate: Long,
    val origin: ChapterDateOrigin = ChapterDateOrigin.UNKNOWN,
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
        val assessment = assessHistory(fetched, now)
        result = result.copy(historicalDiagnostic = assessment.diagnostic)
        if (result.interval > 0) {
            return result.copy(
                historical = null,
                historicalDiagnostic = assessment.diagnostic.copy(reason = HistoricalReason.ACTIVE_SCHEDULE),
            )
        }
        val high = fetched.map { it.number }.filter { it.isFinite() && it >= 0 }.maxOrNull()
        val watermark = listOfNotNull(high, state.historicalAttemptHigh, state.historical?.highNumber).maxOrNull()
        val oldUrls = previous.map { it.url }.toSet()
        val added = fetched.distinctBy { it.url }.filter { it.url !in oldUrls }
        val candidate = state.historical
        if (candidate != null) {
            val event = result.observations.lastOrNull()?.takeIf { it.discoveredAt == now && it != state.observations.lastOrNull() }
            val ambiguous = added.isNotEmpty() && result.observations.isEmpty()
            // Recheck raw evidence: fallback/discovery dates must not rescue unreliable history.
            val evidence = assessment.candidate
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
                return result.copy(
                    historical = null,
                    historicalAttemptHigh = watermark,
                    historicalDiagnostic =
                        assessment.diagnostic.copy(
                            reason =
                                when {
                                    unreliable -> {
                                        assessment.diagnostic.reason.takeUnless { it == HistoricalReason.PROVISIONAL }
                                            ?: HistoricalReason.CONTRADICTED
                                    }

                                    ambiguous -> {
                                        HistoricalReason.AMBIGUOUS_ADDITIONS
                                    }

                                    expired -> {
                                        HistoricalReason.EXPIRED
                                    }

                                    else -> {
                                        HistoricalReason.CONTRADICTED
                                    }
                                },
                        ),
                )
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
                historicalDiagnostic = assessment.diagnostic.copy(reason = HistoricalReason.ACTIVE_SCHEDULE),
            )
        }
        if (state.historicalDisabled) {
            return result.copy(
                historicalDisabled = watermark == null,
                historicalAttemptHigh = watermark,
                historicalDiagnostic = assessment.diagnostic.copy(reason = HistoricalReason.SUPPRESSED),
            )
        }
        if (previous.isNotEmpty() && added.isNotEmpty() && result.observations.isEmpty()) {
            return result.copy(
                historicalAttemptHigh = watermark,
                historicalDiagnostic = assessment.diagnostic.copy(reason = HistoricalReason.AMBIGUOUS_ADDITIONS),
            )
        }
        // After a failed attempt require six entirely newer events, rather than retrying old evidence.
        val eligible = fetched.filter { state.historicalAttemptHigh == null || it.number > state.historicalAttemptHigh }
        val eligibleAssessment = if (state.historicalAttemptHigh == null) assessment else assessHistory(eligible, now)
        val hint =
            eligibleAssessment.candidate ?: return result.copy(
                historicalDiagnostic =
                    eligibleAssessment.diagnostic.let {
                        if (state.historicalAttemptHigh != null && it.reason == HistoricalReason.INSUFFICIENT_EVENTS) {
                            it.copy(reason = HistoricalReason.WAITING_FOR_NEW_HISTORY)
                        } else {
                            it
                        }
                    },
            )
        result = result.copy(historical = hint, historicalAttemptHigh = high, historicalDiagnostic = eligibleAssessment.diagnostic)
        return result
    }

    internal data class HistoricalAssessment(
        val diagnostic: HistoricalDiagnostic,
        val candidate: HistoricalCandidate? = null,
    )

    internal fun historicalCandidate(
        chapters: List<ObservedChapter>,
        now: Long,
    ): HistoricalCandidate? = assessHistory(chapters, now).candidate

    internal fun assessHistory(
        chapters: List<ObservedChapter>,
        now: Long,
    ): HistoricalAssessment {
        // Inspect a contiguous numbered tail, never cherry-pick around unreliable recent chapters.
        val numbered = chapters.distinctBy { it.url }.groupBy { it.number }.toSortedMap()
        val tail = numbered.entries.toList().takeLast(24)
        val inspected = tail.flatMap { it.value }
        var diagnostic =
            HistoricalDiagnostic(
                HistoricalReason.INSUFFICIENT_EVENTS,
                now,
                tail.size,
                missingDates = inspected.count { it.uploadDate <= 0 || it.origin == ChapterDateOrigin.MISSING },
                unknownDates = inspected.count { it.origin == ChapterDateOrigin.UNKNOWN },
                fallbackDates = inspected.count { it.origin == ChapterDateOrigin.FALLBACK },
                eligibleDates = inspected.count { it.origin == ChapterDateOrigin.EXTENSION_REPORTED && it.uploadDate in 1..now },
            )

        fun rejected(reason: HistoricalReason) = HistoricalAssessment(diagnostic.copy(reason = reason))
        if (numbered.keys.any { !it.isFinite() || it < 0 }) return rejected(HistoricalReason.INVALID_NUMBERS)
        if (diagnostic.missingDates > 0) return rejected(HistoricalReason.MISSING_DATES)
        if (diagnostic.fallbackDates > 0) return rejected(HistoricalReason.FALLBACK_DATES)
        if (diagnostic.unknownDates > 0) return rejected(HistoricalReason.UNKNOWN_DATES)
        val events = mutableListOf<Pair<Long, Int>>()
        for ((_, duplicates) in tail) {
            val dates = duplicates.map { it.uploadDate }
            if (dates.any { it > now }) return rejected(HistoricalReason.INVALID_DATES)
            if (dates.max() - dates.min() > DAY / 2) return rejected(HistoricalReason.DUPLICATE_DATES)
            val date = dates.min()
            val last = events.lastOrNull()
            if (last != null && date < last.first) return rejected(HistoricalReason.REVERSED_DATES)
            if (last != null && date - last.first <= DAY) {
                if (last.second >= 3) return rejected(HistoricalReason.BULK_HISTORY)
                events[events.lastIndex] = last.first to last.second + 1
            } else {
                events.add(date to 1)
            }
            diagnostic = diagnostic.copy(events = events.size, latestEventAgeDays = (now - events.last().first).toDouble() / DAY)
        }
        if (events.size < 6) return rejected(HistoricalReason.INSUFFICIENT_EVENTS)
        val recent = events.takeLast(6).map { it.first }
        val gaps = recent.zipWithNext { a, b -> b - a }
        val interval = gaps.sorted()[2]
        diagnostic = diagnostic.copy(intervalDays = interval.toDouble() / DAY)
        // Date-only sources need up to one day of jitter; slower patterns allow 20%.
        if (interval !in (2 * DAY + 1)..(60 * DAY)) return rejected(HistoricalReason.UNSUPPORTED_INTERVAL)
        if (gaps.any { abs(it - interval) > tolerance(interval) }) return rejected(HistoricalReason.INCONSISTENT_INTERVALS)
        val anchor = recent.last()
        if (now - anchor > interval + tolerance(interval)) return rejected(HistoricalReason.STALE_HISTORY)
        return HistoricalAssessment(
            diagnostic.copy(reason = HistoricalReason.PROVISIONAL),
            HistoricalCandidate(interval, anchor, numbered.lastKey()),
        )
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
