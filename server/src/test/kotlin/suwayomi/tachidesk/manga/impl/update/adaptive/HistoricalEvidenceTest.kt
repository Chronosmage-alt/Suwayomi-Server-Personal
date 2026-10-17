package suwayomi.tachidesk.manga.impl.update.adaptive

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HistoricalEvidenceTest {
    private val day = AdaptiveSchedule.DAY
    private val history =
        (1..6).map {
            ObservedChapter("/private-title/$it", it.toFloat(), (10L + it * 7) * day, ChapterDateOrigin.EXTENSION_REPORTED)
        }

    @Test fun legacyDatesStayUnknownAcrossReuseRestartAndUrlChanges() {
        val classified = HistoricalEvidence.classify(emptyList(), history, history)
        assertTrue(classified.all { it.origin == ChapterDateOrigin.UNKNOWN })
        val state =
            AdaptiveSchedule.observe(AdaptiveState(lastCheck = 52 * day), history, classified, 53 * day).copy(
                dateEvidence = HistoricalEvidence.retain(classified, history),
            )
        assertEquals(null, state.historical)
        assertEquals(HistoricalReason.UNKNOWN_DATES, state.historicalDiagnostic?.reason)
        val serialized = Json.encodeToString(state)
        assertFalse(serialized.contains("private-title"))
        val reloaded = Json.decodeFromString<AdaptiveState>(serialized)
        val renamed = history.map { it.copy(url = it.url + "/replacement") }
        assertTrue(HistoricalEvidence.classify(reloaded.dateEvidence, history, renamed).all { it.origin == ChapterDateOrigin.UNKNOWN })
        assertEquals(state, reloaded)
    }

    @Test fun reusedFallbackDatesCannotSeedEvenIfTheyLookWeekly() {
        val missing = history.map { it.copy(uploadDate = 0) }
        val classified = HistoricalEvidence.classify(emptyList(), emptyList(), missing)
        val evidence = HistoricalEvidence.retain(classified, history)
        val reused = HistoricalEvidence.classify(evidence, history, history)
        assertTrue(reused.all { it.origin == ChapterDateOrigin.FALLBACK })
        val assessment = AdaptiveSchedule.assessHistory(reused, 53 * day)
        assertEquals(null, assessment.candidate)
        assertEquals(HistoricalReason.FALLBACK_DATES, assessment.diagnostic.reason)
        assertEquals(6, assessment.diagnostic.fallbackDates)
        val replacement = history.map { it.copy(url = it.url + "-new") }
        assertTrue(HistoricalEvidence.classify(evidence, history, replacement).all { it.origin == ChapterDateOrigin.FALLBACK })
    }

    @Test fun returnedDatesWithRecordedOriginRemainUsableButNotFreshConfirmations() {
        val classified = HistoricalEvidence.classify(emptyList(), emptyList(), history)
        val evidence = HistoricalEvidence.retain(classified, history)
        val seed = AdaptiveSchedule.observe(AdaptiveState(), emptyList(), classified, 53 * day)
        val reused = HistoricalEvidence.classify(evidence, history, history)
        val checked = AdaptiveSchedule.observe(seed, history, reused, 54 * day)
        assertEquals(0, checked.historical?.confirmations)
        assertTrue(checked.observations.isEmpty())
        assertEquals(HistoricalReason.PROVISIONAL, checked.historicalDiagnostic?.reason)
    }

    @Test fun untrustedEvidenceRejectsProvisionalConfirmationWithoutDiscardingFreshLearning() {
        val seed = AdaptiveSchedule.observe(AdaptiveState(), emptyList(), history, 53 * day)
        for (origin in listOf(ChapterDateOrigin.UNKNOWN, ChapterDateOrigin.FALLBACK, ChapterDateOrigin.MISSING)) {
            val next = history + ObservedChapter("/7", 7f, 59 * day, origin)
            val checked = AdaptiveSchedule.observe(seed.copy(lastCheck = 59 * day - day / 2), history, next, 59 * day)
            assertEquals(null, checked.historical)
            assertEquals(0L, checked.interval)
            assertEquals(1, checked.observations.size)
            assertTrue(AdaptiveSchedule.due(checked, 59 * day))
        }
    }

    @Test fun fourFreshObservationsStillLearnWhenHistoricalOriginsAreUnknown() {
        var old = history.map { it.copy(origin = ChapterDateOrigin.UNKNOWN) }
        var state = AdaptiveState(lastCheck = 52 * day)
        repeat(4) { index ->
            val now = (59L + index * 7) * day
            val next = old + ObservedChapter("/new-$index", 7f + index, now, ChapterDateOrigin.UNKNOWN)
            state = AdaptiveSchedule.observe(state.copy(lastCheck = now - day / 2), old, next, now)
            old = next
            assertEquals(index + 1, state.observations.size)
            if (index < 3) assertEquals(0L, state.interval)
        }
        assertEquals(7 * day, state.interval)
        assertEquals(null, state.historical)
        assertEquals(HistoricalReason.ACTIVE_SCHEDULE, state.historicalDiagnostic?.reason)
    }

    @Test fun omittedDatesRejectCurrentEvidenceWithoutRewritingAnExistingDatesOrigin() {
        val missing = history.map { it.copy(uploadDate = 0, origin = ChapterDateOrigin.MISSING) }
        for (origin in listOf(ChapterDateOrigin.EXTENSION_REPORTED, ChapterDateOrigin.UNKNOWN, ChapterDateOrigin.FALLBACK)) {
            val evidence = HistoricalEvidence.retain(history.map { it.copy(origin = origin) }, history)
            val retained = HistoricalEvidence.retain(missing, history, history, evidence)
            assertTrue(HistoricalEvidence.classify(retained, history, history).all { it.origin == origin })
            assertEquals(null, AdaptiveSchedule.historicalCandidate(missing, 53 * day))
        }
        assertEquals(ChapterDateOrigin.UNKNOWN, ObservedChapter("/legacy", 1f, day).origin)
    }

    @Test fun changedDatesCanEstablishReportedEvidenceButMissingDatesNeverCan() {
        val changed = history.map { it.copy(uploadDate = it.uploadDate + 1234) }
        assertTrue(HistoricalEvidence.classify(emptyList(), history, changed).all { it.origin == ChapterDateOrigin.EXTENSION_REPORTED })
        val missing = history.map { it.copy(uploadDate = 0) }
        assertTrue(HistoricalEvidence.classify(emptyList(), history, missing).all { it.origin == ChapterDateOrigin.MISSING })
    }

    @Test fun evidenceAndDiagnosticsAreBoundedAndLegacyStateIsUnassessed() {
        val many = (1..1000).map { ObservedChapter("/sensitive/$it", it.toFloat(), it * day, ChapterDateOrigin.EXTENSION_REPORTED) }
        assertEquals(HistoricalEvidence.MAX_ENTRIES, HistoricalEvidence.retain(many, many).size)
        assertEquals(24, AdaptiveSchedule.assessHistory(many, 1001 * day).diagnostic.inspectedNumbers)
        assertEquals(null, Json.decodeFromString<AdaptiveState>("{}").historicalDiagnostic)
        assertEquals(emptyList(), Json.decodeFromString<AdaptiveState>("{}").dateEvidence)
        // Eviction loses proof rather than guessing a fresh origin.
        val evidence = HistoricalEvidence.retain(many, many)
        assertEquals(ChapterDateOrigin.UNKNOWN, HistoricalEvidence.classify(evidence, many, many.take(1)).single().origin)
    }

    @Test fun deletedAndEvictedUntrustedDatesCannotReturnAsNewEvidence() {
        val cutoff = 53 * day
        for (origin in listOf(ChapterDateOrigin.UNKNOWN, ChapterDateOrigin.FALLBACK)) {
            val original = history.map { it.copy(origin = origin) }
            val ledger = HistoricalEvidence.retain(original, original)
            val newer = (7..110).map { ObservedChapter("/new/$it", it.toFloat(), (53L + it) * day) }
            // Retention drops deleted entries and can evict old entries at the cap.
            val afterDeletion = HistoricalEvidence.retain(newer, newer)
            assertFalse(afterDeletion.any { it in ledger })
            for (returned in listOf(history, history.reversed().map { it.copy(url = it.url + "-reupload") })) {
                val classified = HistoricalEvidence.classify(afterDeletion, newer, returned, cutoff)
                assertTrue(classified.all { it.origin == ChapterDateOrigin.UNKNOWN })
                assertEquals(null, AdaptiveSchedule.historicalCandidate(classified, cutoff))
                val nextLedger = HistoricalEvidence.retain(classified, returned)
                assertTrue(
                    HistoricalEvidence.classify(nextLedger, returned, returned, cutoff).all { it.origin == ChapterDateOrigin.UNKNOWN },
                )
            }
        }
    }

    @Test fun reorderAndReplacementPreserveRecordedOriginsAndNewDatesRemainAvailable() {
        val origins = listOf(ChapterDateOrigin.EXTENSION_REPORTED, ChapterDateOrigin.UNKNOWN, ChapterDateOrigin.FALLBACK)
        val original = history.mapIndexed { index, chapter -> chapter.copy(origin = origins[index % origins.size]) }
        val ledger = HistoricalEvidence.retain(original, original)
        val replaced = original.reversed().map { it.copy(url = it.url + "-new") }
        val classified = HistoricalEvidence.classify(ledger, original, replaced, 53 * day)
        assertEquals(original.reversed().map { it.origin }, classified.map { it.origin })
        val retained = HistoricalEvidence.retain(classified, classified)
        assertEquals(classified, HistoricalEvidence.classify(retained, classified, classified.reversed(), 53 * day).reversed())
        val fresh = ObservedChapter("/new-release", 7f, 59 * day)
        assertEquals(
            ChapterDateOrigin.EXTENSION_REPORTED,
            HistoricalEvidence.classify(retained, classified, listOf(fresh), 53 * day).single().origin,
        )
    }

    @Test fun provenanceCheckedPredictionRequiresTwoNewReleasesEvenAcrossRestartAndRepeatedLists() {
        var chapters = history
        var ledger = HistoricalEvidence.retain(history, history)
        var state = AdaptiveSchedule.observe(AdaptiveState(), emptyList(), history, 53 * day)
        var cutoff = 53 * day
        repeat(2) { index ->
            val now = (59L + index * 7) * day
            val returned = chapters + ObservedChapter("/confirmation-$index", 7f + index, now)
            val classified = HistoricalEvidence.classify(ledger, chapters, returned, cutoff)
            state = AdaptiveSchedule.observe(state.copy(lastCheck = now - day / 2), chapters, classified, now)
            ledger = HistoricalEvidence.retain(classified, returned, chapters, ledger)
            chapters = returned
            cutoff = now
            if (index == 0) {
                assertEquals(1, state.historical?.confirmations)
                assertEquals(0L, state.interval)
                assertTrue(AdaptiveSchedule.due(state, now))
                state = Json.decodeFromString<AdaptiveState>(Json.encodeToString(state))
                repeat(3) { offset ->
                    val unchanged = HistoricalEvidence.classify(ledger, chapters, chapters.reversed(), cutoff)
                    state = AdaptiveSchedule.observe(state, chapters, unchanged, now + offset + 1)
                    assertEquals(1, state.historical?.confirmations)
                    assertEquals(1, state.observations.size)
                    assertEquals(0L, state.interval)
                }
            }
        }
        assertEquals(null, state.historical)
        assertEquals(7 * day, state.interval)
        assertEquals(2, state.observations.size)
        assertFalse(AdaptiveSchedule.due(state, 66 * day))
    }

    @Test fun rejectionReasonsExplainThresholdsWithoutChangingThem() {
        assertEquals(HistoricalReason.INSUFFICIENT_EVENTS, AdaptiveSchedule.assessHistory(history.take(5), 53 * day).diagnostic.reason)
        assertEquals(HistoricalReason.STALE_HISTORY, AdaptiveSchedule.assessHistory(history, 63 * day).diagnostic.reason)
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
        assertEquals(HistoricalReason.INCONSISTENT_INTERVALS, AdaptiveSchedule.assessHistory(irregular, 53 * day).diagnostic.reason)
        val suppressed = AdaptiveSchedule.observe(AdaptiveState(historicalDisabled = true), history, history, 53 * day)
        assertEquals(HistoricalReason.SUPPRESSED, suppressed.historicalDiagnostic?.reason)
        val rejected = AdaptiveSchedule.observe(AdaptiveState(historicalAttemptHigh = 6f), history, history, 53 * day)
        assertEquals(HistoricalReason.WAITING_FOR_NEW_HISTORY, rejected.historicalDiagnostic?.reason)
    }
}
