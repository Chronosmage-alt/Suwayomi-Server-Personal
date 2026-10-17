package suwayomi.tachidesk.manga.impl.update.adaptive

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.protobuf.ProtoBuf
import org.jetbrains.exposed.v1.jdbc.insertAndGetId
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.manga.impl.backup.proto.models.Backup
import suwayomi.tachidesk.manga.impl.backup.proto.models.BackupAdaptiveSource
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.settings.value
import suwayomi.tachidesk.test.ApplicationTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdaptivePersistenceTest : ApplicationTest() {
    @Test fun migratedStateSurvivesIndependentTransactionsAndResetPreservesOverride() {
        val id =
            transaction {
                MangaTable
                    .insertAndGetId {
                        it[url] = "/adaptive-persistence"
                        it[title] = "Adaptive persistence"
                        it[sourceReference] = 123L
                    }.value
            }
        AdaptiveUpdates.setMode(id, AdaptiveOverride.SMART)
        val state =
            AdaptiveState(
                lastCheck = 123,
                historical = HistoricalCandidate(7 * AdaptiveSchedule.DAY, 100, 6f, 1),
                historicalAttemptHigh = 6f,
                dateEvidenceThrough = 123,
            )
        AdaptiveUpdates.saveState(id, state)
        assertEquals(AdaptiveOverride.SMART, AdaptiveUpdates.entries(listOf(id))[id]?.mode)
        AdaptiveUpdates.setMode(id, AdaptiveOverride.NORMAL)
        assertEquals(state, AdaptiveUpdates.entries(listOf(id))[id]?.state)
        AdaptiveUpdates.setMode(id, AdaptiveOverride.SMART)
        AdaptiveUpdates.resetHistory(id)
        assertEquals(
            AdaptiveEntry(
                AdaptiveOverride.SMART,
                AdaptiveState(
                    historicalDisabled = true,
                    historicalAttemptHigh = 6f,
                    historicalDiagnostic = HistoricalDiagnostic(HistoricalReason.SUPPRESSED),
                    dateEvidenceThrough = 123,
                ),
            ),
            AdaptiveUpdates.entries(listOf(id))[id],
        )
    }

    @Test fun sourcePolicyCanSurviveWithoutInstalledSourceRow() {
        val policy = AdaptivePolicy(987654321L, "test.adaptive.provider", true)
        AdaptiveUpdates.restorePolicy(policy)
        assertEquals(policy, AdaptiveUpdates.policies().first { it.sourceId == policy.sourceId })
        AdaptiveUpdates.restorePolicy(policy.copy(enabled = false))
        assertEquals(false, AdaptiveUpdates.policies().first { it.sourceId == policy.sourceId }.enabled)
    }

    @OptIn(ExperimentalSerializationApi::class)
    @Test
    fun portableBackupRetainsSourcePreferencesAndReadsLegacyDefaults() {
        val old = ProtoBuf.decodeFromByteArray(Backup.serializer(), byteArrayOf())
        assertEquals(emptyList(), old.adaptiveSources)
        val backup = Backup(adaptiveSources = listOf(BackupAdaptiveSource(123, "test.provider", true)))
        assertEquals(backup, ProtoBuf.decodeFromByteArray(Backup.serializer(), ProtoBuf.encodeToByteArray(Backup.serializer(), backup)))
    }

    @Test fun sameProviderReinstallRetainsPreferenceButDifferentIdentityDoesNotInheritIt() {
        val policy = AdaptivePolicy(567L, "test.original", true)
        AdaptiveUpdates.restorePolicy(policy)
        serverConfig.smartLibraryUpdates.value = true
        try {
            assertTrue(AdaptiveUpdates.effective(AdaptiveEntry(), 567L, AdaptiveUpdates.policies(), mapOf(567L to "test.original")))
            assertFalse(AdaptiveUpdates.effective(AdaptiveEntry(), 567L, AdaptiveUpdates.policies(), emptyMap()))
            assertTrue(AdaptiveUpdates.effective(AdaptiveEntry(), 567L, AdaptiveUpdates.policies(), mapOf(567L to "test.original")))
            assertFalse(AdaptiveUpdates.effective(AdaptiveEntry(), 567L, AdaptiveUpdates.policies(), mapOf(567L to "test.replacement")))
        } finally {
            serverConfig.smartLibraryUpdates.value = false
        }
    }

    @Test fun reloadedFirstConfirmationNeedsAnotherNewReleaseAndStaleStateRetainsWatermark() {
        val day = AdaptiveSchedule.DAY
        val history = (1..6).map { ObservedChapter("/$it", it.toFloat(), (10L + 7 * it) * day, ChapterDateOrigin.EXTENSION_REPORTED) }
        val seeded = AdaptiveSchedule.observe(AdaptiveState(), emptyList(), history, 53 * day)
        val firstHistory = history + ObservedChapter("/7", 7f, 59 * day, ChapterDateOrigin.EXTENSION_REPORTED)
        val first = AdaptiveSchedule.observe(seeded.copy(lastCheck = 59 * day - day / 2), history, firstHistory, 59 * day)
        val id =
            transaction {
                MangaTable
                    .insertAndGetId {
                        it[url] = "/adaptive-reload-${System.nanoTime()}"
                        it[title] = "Reload confirmation"
                        it[sourceReference] = 123L
                    }.value
            }
        AdaptiveUpdates.saveState(id, first)
        val reloaded = AdaptiveUpdates.entries(listOf(id)).getValue(id).state
        assertEquals(first, reloaded)
        assertEquals(1, reloaded.historical?.confirmations)
        val unchanged = AdaptiveSchedule.observe(reloaded, firstHistory, firstHistory, 60 * day)
        assertEquals(1, unchanged.historical?.confirmations)
        assertTrue(AdaptiveSchedule.due(unchanged, 60 * day))
        val confirmed =
            AdaptiveSchedule.observe(
                unchanged.copy(lastCheck = 66 * day - day / 2),
                firstHistory,
                firstHistory + ObservedChapter("/8", 8f, 66 * day, ChapterDateOrigin.EXTENSION_REPORTED),
                66 * day,
            )
        assertEquals(7 * day, confirmed.interval)
        val stale = AdaptiveUpdates.freshState(reloaded, 100 * day)
        assertEquals(null, stale.historical)
        assertEquals(7f, stale.historicalAttemptHigh)
        assertTrue(AdaptiveSchedule.due(stale, 100 * day))
    }

    @Test fun evidenceCutoffSurvivesRestartResetAndExpiryWithoutCertifyingDeletedHistory() {
        val day = AdaptiveSchedule.DAY
        val id =
            transaction {
                MangaTable
                    .insertAndGetId {
                        it[url] = "/evidence-cutoff-${System.nanoTime()}"
                        it[title] = "Evidence cutoff"
                        it[sourceReference] = 123L
                    }.value
            }
        val original = AdaptiveState(lastCheck = 53 * day, dateEvidenceThrough = 53 * day)
        AdaptiveUpdates.saveState(id, original)
        val reloaded = AdaptiveUpdates.entries(listOf(id)).getValue(id).state
        assertEquals(original, reloaded)
        val expired = AdaptiveUpdates.freshState(reloaded, 100 * day)
        assertEquals(53 * day, expired.dateEvidenceThrough)
        assertEquals(null, expired.historicalDiagnostic)
        AdaptiveUpdates.resetHistory(id)
        val reset = AdaptiveUpdates.entries(listOf(id)).getValue(id).state
        assertEquals(53 * day, reset.dateEvidenceThrough)
        assertTrue(reset.dateEvidence.isEmpty())
        val returned = listOf(ObservedChapter("/deleted", 1f, 50 * day))
        for (state in listOf(reloaded, expired, reset)) {
            assertEquals(
                ChapterDateOrigin.UNKNOWN,
                HistoricalEvidence.classify(state.dateEvidence, emptyList(), returned, state.dateEvidenceThrough).single().origin,
            )
        }
        // A database created before the cutoff field existed still has its successful check horizon.
        val legacy = original.copy(dateEvidenceThrough = 0)
        assertEquals(53 * day, AdaptiveUpdates.freshState(legacy, 100 * day).dateEvidenceThrough)
    }

    @Test fun resetWithoutExistingStateRowStillSuppressesRestoredHistory() {
        val id =
            transaction {
                MangaTable
                    .insertAndGetId {
                        it[url] = "/adaptive-reset-new-${System.nanoTime()}"
                        it[title] = "Restored without state"
                        it[sourceReference] = 123L
                    }.value
            }
        assertEquals(null, AdaptiveUpdates.entries(listOf(id))[id])
        AdaptiveUpdates.resetHistory(id)
        val reset = AdaptiveUpdates.entries(listOf(id)).getValue(id).state
        assertTrue(reset.historicalDisabled)
        val day = AdaptiveSchedule.DAY
        val history = (1..6).map { ObservedChapter("/$it", it.toFloat(), (10L + 7 * it) * day, ChapterDateOrigin.EXTENSION_REPORTED) }
        val refreshed = AdaptiveSchedule.observe(reset, emptyList(), history, 53 * day)
        assertEquals(null, refreshed.historical)
        assertEquals(6f, refreshed.historicalAttemptHigh)
        assertEquals(0L, refreshed.interval)
    }

    @Test fun resetPreservesKnownHorizonAcrossTruncatedSourceResponses() {
        val day = AdaptiveSchedule.DAY
        val id =
            transaction {
                MangaTable
                    .insertAndGetId {
                        it[url] = "/adaptive-reset-horizon-${System.nanoTime()}"
                        it[title] = "Reset horizon"
                        it[sourceReference] = 123L
                    }.value
            }
        AdaptiveUpdates.setMode(id, AdaptiveOverride.SMART)
        AdaptiveUpdates.saveState(
            id,
            AdaptiveState(
                historical = HistoricalCandidate(7 * day, 59 * day, 7f, 1),
                historicalAttemptHigh = 6f,
            ),
        )
        AdaptiveUpdates.resetHistory(id)
        val reset = AdaptiveUpdates.entries(listOf(id)).getValue(id)
        assertEquals(AdaptiveOverride.SMART, reset.mode)
        assertEquals(7f, reset.state.historicalAttemptHigh)
        assertTrue(reset.state.observations.isEmpty())
        assertEquals(null, reset.state.historical)
        val history = (1..7).map { ObservedChapter("/$it", it.toFloat(), (10L + it * 7) * day) }
        val shortened = AdaptiveSchedule.observe(reset.state, emptyList(), history.take(1), 60 * day)
        val restored = AdaptiveSchedule.observe(shortened, emptyList(), history, 60 * day)
        assertEquals(7f, restored.historicalAttemptHigh)
        assertEquals(null, restored.historical)
        assertTrue(AdaptiveSchedule.due(restored, 60 * day))
    }
}
