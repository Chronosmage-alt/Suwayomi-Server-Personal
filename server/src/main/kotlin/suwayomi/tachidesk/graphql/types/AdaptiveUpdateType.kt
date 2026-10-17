package suwayomi.tachidesk.graphql.types

import android.app.Application
import android.content.Context
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.manga.impl.update.adaptive.AdaptiveEntry
import suwayomi.tachidesk.manga.impl.update.adaptive.AdaptiveOverride
import suwayomi.tachidesk.manga.impl.update.adaptive.AdaptivePhase
import suwayomi.tachidesk.manga.impl.update.adaptive.AdaptiveSchedule
import suwayomi.tachidesk.manga.impl.update.adaptive.AdaptiveUpdates
import suwayomi.tachidesk.manga.model.table.ExtensionTable
import suwayomi.tachidesk.manga.model.table.MangaTable
import suwayomi.tachidesk.manga.model.table.SourceTable
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.settings.value
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

enum class AdaptiveUpdateMode { FOLLOW_SOURCE, SMART, NORMAL }

enum class AdaptiveUpdateReason { MASTER_DISABLED, TITLE_NORMAL, SOURCE_DISABLED, LEARNING, FREQUENT_RELEASE, SCHEDULED, BACKOFF }

data class AdaptiveSourceSetting(
    val sourceId: Long,
    val providerPackage: String,
    val name: String,
    val enabled: Boolean,
    val installed: Boolean,
)

data class AdaptiveUpdateSettings(
    val enabled: Boolean,
    val canManage: Boolean,
    val maxBackoffDays: Int,
    val sources: List<AdaptiveSourceSetting>,
)

data class AdaptiveMangaSchedule(
    val mangaId: Int,
    val mode: AdaptiveUpdateMode,
    val canManage: Boolean,
    val effective: Boolean,
    val reason: AdaptiveUpdateReason,
    val intervalDays: Double?,
    val provisionalIntervalDays: Double?,
    val historicalConfirmations: Int,
    val observations: Int,
    val emptyChecks: Int,
    val lastCheckAt: Long?,
    val lastDiscoveryAt: Long?,
    val lastReleaseAt: Long?,
    val usesPublicationDate: Boolean?,
    val nextEligibleAt: Long?,
    val nextAutomaticCheckAt: Long?,
)

object AdaptiveUpdateInfo {
    fun settings(canManage: Boolean): AdaptiveUpdateSettings {
        val policies = AdaptiveUpdates.policies()
        val installed =
            transaction {
                (SourceTable innerJoin ExtensionTable).selectAll().map { row ->
                    val id = row[SourceTable.id].value
                    val pkg = row[ExtensionTable.pkgName]
                    AdaptiveSourceSetting(
                        id,
                        pkg,
                        row[SourceTable.name],
                        policies.any {
                            it.sourceId == id && it.providerPackage == pkg &&
                                it.enabled
                        },
                        true,
                    )
                }
            }
        val dormant =
            policies
                .filter { policy ->
                    installed.none {
                        it.sourceId == policy.sourceId &&
                            it.providerPackage == policy.providerPackage
                    }
                }.map { AdaptiveSourceSetting(it.sourceId, it.providerPackage, it.sourceId.toString(), it.enabled, false) }
        return AdaptiveUpdateSettings(
            serverConfig.smartLibraryUpdates.value,
            canManage,
            14,
            if (canManage) (installed + dormant).sortedBy { it.name.lowercase() } else emptyList(),
        )
    }

    fun manga(
        mangaId: Int,
        canManage: Boolean,
    ): AdaptiveMangaSchedule {
        val source = transaction { MangaTable.selectAll().where { MangaTable.id eq mangaId }.single()[MangaTable.sourceReference] }
        val entry = AdaptiveUpdates.entries(listOf(mangaId))[mangaId] ?: AdaptiveEntry()
        val now = System.currentTimeMillis()
        val state = AdaptiveUpdates.freshState(entry.state, now)
        val effective = AdaptiveUpdates.effective(entry, source, AdaptiveUpdates.policies(), AdaptiveUpdates.providers())
        val reason =
            when {
                !serverConfig.smartLibraryUpdates.value -> AdaptiveUpdateReason.MASTER_DISABLED
                entry.mode == AdaptiveOverride.NORMAL -> AdaptiveUpdateReason.TITLE_NORMAL
                !effective -> AdaptiveUpdateReason.SOURCE_DISABLED
                state.interval == 0L -> AdaptiveUpdateReason.LEARNING
                state.interval <= 2 * AdaptiveSchedule.DAY -> AdaptiveUpdateReason.FREQUENT_RELEASE
                state.phase == AdaptivePhase.BACKOFF -> AdaptiveUpdateReason.BACKOFF
                else -> AdaptiveUpdateReason.SCHEDULED
            }
        val eligible = if (effective && state.interval > 2 * AdaptiveSchedule.DAY) maxOf(now, state.nextCheck) else now
        val interval = (serverConfig.globalUpdateInterval.value * 3_600_000).toLong()
        val nextAutomatic =
            if (interval == 0L) {
                null
            } else {
                val last =
                    Injekt
                        .get<Application>()
                        .getSharedPreferences(
                            "server_util",
                            Context.MODE_PRIVATE,
                        ).getLong("lastAutomatedGlobalUpdate", 0)
                val nextGlobal = if (last == 0L) now + interval else now + interval - (now - last).mod(interval)
                if (eligible <= nextGlobal) nextGlobal else nextGlobal + ((eligible - nextGlobal + interval - 1) / interval) * interval
            }
        val observation = state.observations.lastOrNull()
        return AdaptiveMangaSchedule(
            mangaId,
            AdaptiveUpdateMode.valueOf(entry.mode.name),
            canManage,
            effective,
            reason,
            state.interval
                .takeIf { it > 0 }
                ?.toDouble()
                ?.div(AdaptiveSchedule.DAY),
            state.historical
                ?.interval
                ?.toDouble()
                ?.div(AdaptiveSchedule.DAY),
            state.historical?.confirmations ?: 0,
            state.observations.size.coerceAtMost(4),
            state.emptyChecks,
            state.lastCheck.takeIf { it > 0 },
            state.lastDiscovery.takeIf { it > 0 },
            observation?.releaseAt,
            observation?.sourceDate,
            eligible.takeIf { effective && state.interval > 2 * AdaptiveSchedule.DAY },
            nextAutomatic,
        )
    }
}
