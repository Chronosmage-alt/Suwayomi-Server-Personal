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
        val state = AdaptiveState(lastCheck = 123, interval = 7 * AdaptiveSchedule.DAY, nextCheck = 999)
        AdaptiveUpdates.saveState(id, state)
        assertEquals(AdaptiveOverride.SMART, AdaptiveUpdates.entries(listOf(id))[id]?.mode)
        AdaptiveUpdates.setMode(id, AdaptiveOverride.NORMAL)
        assertEquals(state, AdaptiveUpdates.entries(listOf(id))[id]?.state)
        AdaptiveUpdates.setMode(id, AdaptiveOverride.SMART)
        AdaptiveUpdates.resetHistory(id)
        assertEquals(AdaptiveEntry(AdaptiveOverride.SMART), AdaptiveUpdates.entries(listOf(id))[id])
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
}
