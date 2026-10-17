package suwayomi.tachidesk.manga.impl.update.adaptive

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.inList
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.upsert
import suwayomi.tachidesk.manga.impl.Manga
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.settings.value
import suwayomi.tachidesk.manga.model.table.AdaptiveMangaStateTable as States
import suwayomi.tachidesk.manga.model.table.AdaptiveSourcePolicyTable as Policies

data class AdaptiveEntry(
    val mode: AdaptiveOverride = AdaptiveOverride.FOLLOW_SOURCE,
    val state: AdaptiveState = AdaptiveState(),
)

data class AdaptivePolicy(
    val sourceId: Long,
    val providerPackage: String,
    val enabled: Boolean,
)

object AdaptiveUpdates {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }

    fun decode(row: ResultRow?): AdaptiveEntry =
        if (row == null) {
            AdaptiveEntry()
        } else {
            AdaptiveEntry(
                runCatching { AdaptiveOverride.valueOf(row[States.overrideMode]) }.getOrDefault(AdaptiveOverride.FOLLOW_SOURCE),
                runCatching {
                    json.decodeFromString<AdaptiveState>(row[States.state]).takeIf { it.version == 1 } ?: AdaptiveState()
                }.getOrDefault(AdaptiveState()),
            )
        }

    fun entries(ids: List<Int>): Map<Int, AdaptiveEntry> =
        transaction {
            if (ids.isEmpty()) {
                emptyMap()
            } else {
                States
                    .selectAll()
                    .where { States.manga inList ids }
                    .associate { it[States.manga].value to decode(it) }
            }
        }

    fun policies(): List<AdaptivePolicy> =
        transaction {
            Policies.selectAll().map { AdaptivePolicy(it[Policies.sourceId], it[Policies.providerPackage], it[Policies.enabled]) }
        }

    fun providers(): Map<Long, String> =
        transaction {
            (SourceTable innerJoin ExtensionTable).selectAll().associate { it[SourceTable.id].value to it[ExtensionTable.pkgName] }
        }

    fun effective(
        entry: AdaptiveEntry,
        sourceId: Long,
        policies: List<AdaptivePolicy>,
        providers: Map<Long, String>,
    ): Boolean {
        val source = policies.any { it.sourceId == sourceId && it.enabled && it.providerPackage == providers[sourceId] }
        return AdaptiveSchedule.enabled(serverConfig.smartLibraryUpdates.value, source, entry.mode)
    }

    fun freshState(
        state: AdaptiveState,
        now: Long,
    ): AdaptiveState =
        if (state.lastCheck > 0 &&
            now - state.lastCheck > maxOf(28 * AdaptiveSchedule.DAY, 3 * state.interval)
        ) {
            AdaptiveState(
                historicalAttemptHigh = listOfNotNull(state.historicalAttemptHigh, state.historical?.highNumber).maxOrNull(),
                historicalDisabled = state.historicalDisabled,
                dateEvidence = state.dateEvidence,
                dateEvidenceThrough = maxOf(state.dateEvidenceThrough, state.lastCheck),
                historicalDiagnostic = state.historicalDiagnostic?.copy(reason = HistoricalReason.EXPIRED),
            )
        } else {
            state
        }

    fun due(
        mangaId: Int,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (!serverConfig.smartLibraryUpdates.value) return true
        val entry = entries(listOf(mangaId))[mangaId] ?: AdaptiveEntry()
        val sourceId = transaction { MangaTable.selectAll().where { MangaTable.id eq mangaId }.first()[MangaTable.sourceReference] }
        return !effective(entry, sourceId, policies(), providers()) || AdaptiveSchedule.due(freshState(entry.state, now), now)
    }

    fun record(
        mangaId: Int,
        sourceId: Long,
        previous: List<ObservedChapter>,
        fetched: List<ObservedChapter>,
        stored: List<ObservedChapter>,
    ) {
        if (!serverConfig.smartLibraryUpdates.value) return
        val entry = entries(listOf(mangaId))[mangaId] ?: AdaptiveEntry()
        if (!effective(entry, sourceId, policies(), providers())) return
        val now = System.currentTimeMillis()
        val state = freshState(entry.state, now)
        val classified =
            HistoricalEvidence.classify(
                state.dateEvidence,
                previous,
                fetched,
                maxOf(state.dateEvidenceThrough, state.lastCheck),
            )
        saveState(
            mangaId,
            AdaptiveSchedule.observe(state, previous, classified, now).copy(
                dateEvidence = HistoricalEvidence.retain(classified, stored, previous, state.dateEvidence),
                // Include future/invalid source timestamps too: deleting one must not later
                // certify it merely because wall time caught up and its ledger entry vanished.
                dateEvidenceThrough =
                    maxOf(
                        state.dateEvidenceThrough,
                        state.lastCheck,
                        now,
                        previous.maxOfOrNull { it.uploadDate } ?: 0,
                        fetched.maxOfOrNull { it.uploadDate } ?: 0,
                    ),
            ),
        )
    }

    internal fun saveState(
        mangaId: Int,
        state: AdaptiveState,
    ) = transaction {
        States.upsert(States.manga, onUpdateExclude = listOf(States.overrideMode)) {
            it[States.manga] =
                org.jetbrains.exposed.v1.core.dao.id
                    .EntityID(mangaId, MangaTable)
            it[States.state] = json.encodeToString(state)
        }
    }

    fun setMode(
        mangaId: Int,
        mode: AdaptiveOverride,
    ) = transaction {
        States.upsert(States.manga, onUpdateExclude = listOf(States.state)) {
            it[States.manga] =
                org.jetbrains.exposed.v1.core.dao.id
                    .EntityID(mangaId, MangaTable)
            it[States.overrideMode] = mode.name
        }
    }

    fun resetHistory(mangaId: Int) =
        runBlocking {
            Manga.withMangaLock(mangaId) { resetHistoryLocked(mangaId) }
        }

    // Caller owns the title fetch lock, or is creating a title in an uncommitted transaction.
    internal fun resetHistoryLocked(mangaId: Int) =
        transaction {
            // Keep the known chapter horizon even when the next source response is truncated.
            // Restored/new titles may not have a state row yet; suppression must still persist.
            val old = decode(States.selectAll().where { States.manga eq mangaId }.firstOrNull()).state
            val watermark = listOfNotNull(old.historicalAttemptHigh, old.historical?.highNumber).maxOrNull()
            saveState(
                mangaId,
                AdaptiveState(
                    historicalDisabled = true,
                    historicalAttemptHigh = watermark,
                    historicalDiagnostic = HistoricalDiagnostic(HistoricalReason.SUPPRESSED),
                    dateEvidenceThrough = maxOf(old.dateEvidenceThrough, old.lastCheck),
                ),
            )
        }

    fun setSource(
        sourceId: Long,
        enabled: Boolean,
    ) {
        val provider = providers()[sourceId] ?: error("Source is not installed")
        restorePolicy(AdaptivePolicy(sourceId, provider, enabled))
    }

    fun restorePolicy(policy: AdaptivePolicy) {
        transaction {
            Policies.upsert(Policies.sourceId) {
                it[Policies.sourceId] = policy.sourceId
                it[Policies.providerPackage] = policy.providerPackage
                it[Policies.enabled] = policy.enabled
            }
        }
    }
}
