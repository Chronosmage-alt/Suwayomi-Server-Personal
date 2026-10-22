package suwayomi.tachidesk.manga.impl.update

import android.app.Application
import android.content.Context
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.leftJoin
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import suwayomi.tachidesk.global.impl.sync.SyncManager
import suwayomi.tachidesk.global.model.table.UserAccountTable
import suwayomi.tachidesk.global.model.table.UserSettingsTable
import suwayomi.tachidesk.manga.impl.Category
import suwayomi.tachidesk.manga.impl.CategoryManga
import suwayomi.tachidesk.manga.impl.Manga
import suwayomi.tachidesk.manga.impl.update.adaptive.AdaptiveEntry
import suwayomi.tachidesk.manga.impl.update.adaptive.AdaptiveSchedule
import suwayomi.tachidesk.manga.impl.update.adaptive.AdaptiveUpdates
import suwayomi.tachidesk.manga.model.dataclass.CategoryDataClass
import suwayomi.tachidesk.manga.model.dataclass.IncludeOrExclude
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass
import suwayomi.tachidesk.manga.model.table.MangaStatus
import suwayomi.tachidesk.manga.model.table.MangaUserTable
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.settings.userConfig
import suwayomi.tachidesk.server.settings.value
import suwayomi.tachidesk.util.HAScheduler
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

@OptIn(FlowPreview::class)
class Updater : IUpdater {
    private val logger = KotlinLogging.logger {}
    private val notifyFlowScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val notifyFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    @Deprecated("Replaced with updatesFlow", replaceWith = ReplaceWith("updatesFlow"))
    private val statusFlow = MutableSharedFlow<UpdateStatus>()

    @Deprecated("Replaced with updates", replaceWith = ReplaceWith("updates"))
    override val status = statusFlow.onStart { emit(getStatusDeprecated(null)) }

    private val updatesFlow = MutableSharedFlow<UpdateUpdates>()
    override val updates = updatesFlow.onStart { emit(getUpdates(addInitial = true)) }

    init {
        // has to be in its own scope (notifyFlowScope), otherwise, the collection gets canceled due to canceling the scopes (scope) children in the reset function
        notifyFlowScope.launch {
            notifyFlow.sample(1.seconds).collect {
                updateStatus(immediate = true)
            }
        }
    }

    private val _status = MutableStateFlow(UpdateStatus())
    override val statusDeprecated = _status.asStateFlow()

    private val mangaUpdates = ConcurrentHashMap<Int, UpdateJob>()
    private val categoryUpdates = ConcurrentHashMap<Int, CategoryUpdateJob>()
    private var updateStatusCategories: Map<CategoryUpdateStatus, List<CategoryDataClass>> = emptyMap()
    private var updateStatusSkippedMangas: List<MangaDataClass> = emptyList()

    private val tracker = ConcurrentHashMap<Int, UpdateJob>()

    // The API presents the latest job per title, but every queued request must remain accounted for.
    private val activeJobs = ConcurrentHashMap<Long, UpdateJob>()
    private val workLock = Any()
    private val notificationLock = Mutex()
    private var preparations = 0
    private val updateChannels = ConcurrentHashMap<String, Channel<UpdateJob>>()

    private var maxSourcesInParallel = 20 // max permits, necessary to be set to be able to release up to 20 permits
    private val semaphore = Semaphore(maxSourcesInParallel)

    private val lastUpdateKey = "lastGlobalUpdate"
    private val lastAutomatedUpdateKey = "lastAutomatedGlobalUpdate"
    private val preferences = Injekt.get<Application>().getSharedPreferences("server_util", Context.MODE_PRIVATE)

    private var currentUpdateTaskId = ""

    init {
        serverConfig.subscribeTo(serverConfig.globalUpdateInterval, ::scheduleUpdateTask)
        serverConfig.subscribeTo(
            serverConfig.maxSourcesInParallel,
            { newMaxPermits ->
                val permitDifference = maxSourcesInParallel - newMaxPermits
                maxSourcesInParallel = newMaxPermits

                val addMorePermits = permitDifference < 0
                for (i in 1..permitDifference.absoluteValue) {
                    if (addMorePermits) {
                        semaphore.release()
                    } else {
                        semaphore.acquire()
                    }
                }
            },
            ignoreInitialValue = false,
        )
    }

    override fun getLastUpdateTimestamp(): Long = preferences.getLong(lastUpdateKey, 0)

    fun saveLastUpdateTimestamp() {
        preferences.edit().putLong(lastUpdateKey, System.currentTimeMillis()).apply()
    }

    fun getLastAutomatedUpdateTimestamp(): Long = preferences.getLong(lastAutomatedUpdateKey, 0)

    fun saveLastAutomatedUpdateTimestamp() {
        preferences.edit().putLong(lastAutomatedUpdateKey, System.currentTimeMillis()).apply()
    }

    override fun deleteLastAutomatedUpdateTimestamp() {
        preferences.edit().remove(lastAutomatedUpdateKey).apply()
    }

    internal fun autoUpdateTask() {
        try {
            val lastAutomatedUpdate = getLastAutomatedUpdateTimestamp()
            saveLastAutomatedUpdateTimestamp()

            if (getStatus().isRunning) {
                logger.debug { "Global update is already in progress" }
                return
            }

            logger.info {
                "Trigger global update (interval= ${serverConfig.globalUpdateInterval.value}h, lastAutomatedUpdate= ${Date(
                    lastAutomatedUpdate,
                )})"
            }

            val userIds =
                transaction {
                    UserAccountTable.selectAll().map { it[UserAccountTable.id].value }
                }

            synchronized(workLock) {
                if (isRunning()) return
                clearHistory()
                preparations++
            }

            try {
                userIds.forEach { userId ->
                    addCategoriesToUpdateQueue(
                        userId,
                        Category.getCategoryList(userId),
                        clear = false,
                        forceAll = false,
                        automatedUpdate = true,
                    )
                }
            } finally {
                synchronized(workLock) { preparations-- }
                notifyFlowScope.launch { updateStatus(immediate = true) }
            }
        } catch (e: Exception) {
            logger.error(e) { "autoUpdateTask: failed due to" }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun scheduleUpdateTask() {
        HAScheduler.deschedule(currentUpdateTaskId)

        val isAutoUpdateDisabled = serverConfig.globalUpdateInterval.value == 0.0
        if (isAutoUpdateDisabled) {
            return
        }

        val updateInterval = serverConfig.globalUpdateInterval.value.hours.inWholeMilliseconds
        val lastAutomatedUpdate = getLastAutomatedUpdateTimestamp()
        val isInitialScheduling = lastAutomatedUpdate == 0L

        val timeToNextExecution =
            if (!isInitialScheduling) {
                (updateInterval - (System.currentTimeMillis() - lastAutomatedUpdate)).mod(updateInterval)
            } else {
                updateInterval
            }

        if (isInitialScheduling) {
            saveLastAutomatedUpdateTimestamp()
        }

        val wasPreviousUpdateTriggered =
            System.currentTimeMillis() - (
                if (!isInitialScheduling) lastAutomatedUpdate else System.currentTimeMillis()
            ) < updateInterval
        if (!wasPreviousUpdateTriggered) {
            GlobalScope.launch {
                autoUpdateTask()
            }
        }

        currentUpdateTaskId = HAScheduler.schedule(::autoUpdateTask, updateInterval, timeToNextExecution, "global-update")
    }

    private fun isRunning(): Boolean = synchronized(workLock) { activeJobs.isNotEmpty() || preparations > 0 }

    // old status that is still required for the deprecated endpoints
    private fun getStatusDeprecated(running: Boolean? = null): UpdateStatus {
        val jobs = tracker.values.toList()
        val isRunning = running ?: isRunning()
        return UpdateStatus(this.updateStatusCategories, jobs, this.updateStatusSkippedMangas, isRunning)
    }

    private fun getStatus(
        categories: List<CategoryUpdateJob>,
        mangas: List<UpdateJob>,
        running: Boolean? = null,
        addInitial: Boolean? = false,
    ): UpdateUpdates =
        UpdateUpdates(
            running ?: isRunning(),
            categories,
            mangas,
            tracker.values.count { it.status != JobStatus.SKIPPED },
            tracker.values.count { it.status == JobStatus.COMPLETE || it.status == JobStatus.FAILED },
            this.updateStatusCategories[CategoryUpdateStatus.SKIPPED]?.size ?: 0,
            (
                this.updateStatusSkippedMangas.map {
                    it.id
                } + tracker.values.filter { it.status == JobStatus.SKIPPED }.map { it.manga.id }
            ).distinct().size,
            if (addInitial == true) getStatus() else null,
        )

    override fun getStatus(): UpdateUpdates =
        synchronized(workLock) {
            getStatus(
                this.updateStatusCategories[CategoryUpdateStatus.UPDATING]
                    ?.map {
                        CategoryUpdateJob(
                            it,
                            CategoryUpdateStatus.UPDATING,
                        )
                    }.orEmpty(),
                tracker.values.toList() +
                    updateStatusSkippedMangas.filter { !tracker.containsKey(it.id) }.map {
                        UpdateJob(it, status = JobStatus.SKIPPED, skipReason = "EXCLUDED_BY_UPDATE_FILTERS")
                    },
            )
        }

    private fun getUpdates(
        running: Boolean? = null,
        addInitial: Boolean? = null,
    ): UpdateUpdates =
        getStatus(
            categoryUpdates.values.toList(),
            mangaUpdates.values.toList(),
            running,
            addInitial = addInitial,
        )

    /**
     * Pass "isRunning" to force a specific running state
     */
    private suspend fun updateStatus(
        immediate: Boolean = false,
        categoryUpdates: List<CategoryUpdateJob> = emptyList(),
        mangaUpdates: List<UpdateJob> = emptyList(),
        isRunning: Boolean? = null,
    ) = notificationLock.withLock {
        // Always buffer the current reserved state, never a stale PENDING/completion from another job.
        mangaUpdates.forEach { incoming ->
            tracker[incoming.manga.id]?.let { this.mangaUpdates[incoming.manga.id] = it }
        }
        categoryUpdates.forEach { this.categoryUpdates[it.category.id] = it }

        if (immediate) {
            val (status, updates) =
                synchronized(workLock) {
                    this.mangaUpdates.replaceAll { id, job -> tracker[id] ?: job }
                    val status = getStatusDeprecated(running = isRunning)
                    val updates = getUpdates(isRunning)
                    this.mangaUpdates.clear()
                    this.categoryUpdates.clear()
                    status to updates
                }

            statusFlow.emit(status)
            _status.update { status }
            updatesFlow.emit(updates)

            return@withLock
        }

        notifyFlow.emit(Unit)
    }

    private fun getOrCreateUpdateChannelFor(source: String): Channel<UpdateJob> =
        updateChannels.computeIfAbsent(source) {
            logger.debug { "getOrCreateUpdateChannelFor: created channel for $source - channels: ${updateChannels.size + 1}" }
            createUpdateChannel(source)
        }

    private fun createUpdateChannel(source: String): Channel<UpdateJob> {
        val channel = Channel<UpdateJob>(Channel.UNLIMITED)
        channel
            .consumeAsFlow()
            .onEach { job ->
                semaphore.withPermit {
                    process(job)
                }
            }.catch {
                logger.error(it) { "Error during updates (source: $source)" }
                handleChannelUpdateFailure(source)
            }.onCompletion { updateChannels.remove(source, channel) }
            .launchIn(scope)
        return channel
    }

    private suspend fun handleChannelUpdateFailure(source: String) {
        val failed =
            synchronized(workLock) {
                activeJobs.values.filter { it.manga.sourceId == source }.map { job ->
                    activeJobs.remove(job.id)
                    job.copy(status = JobStatus.FAILED).also {
                        if (tracker[job.manga.id]?.id == job.id) tracker[job.manga.id] = it
                    }
                }
            }
        updateStatus(immediate = true, mangaUpdates = failed)
    }

    private suspend fun process(job: UpdateJob) {
        synchronized(workLock) {
            if (!activeJobs.containsKey(job.id)) return
            val running = job.copy(status = JobStatus.RUNNING)
            activeJobs[job.id] = running
            if (tracker[job.manga.id]?.id == job.id) tracker[job.manga.id] = running
        }
        updateStatus(mangaUpdates = listOf(job))

        // updateMangas is per-user. For automated global updates (job.userId == null), update the manga info if any
        // user with this manga has it enabled. For user-triggered updates, the triggering user's setting applies.
        val updateManga =
            if (job.userId != null) {
                userConfig.updateMangas.value(job.userId) || !job.manga.initialized
            } else {
                anyUserWantsMangaUpdate(job.manga.id) || !job.manga.initialized
            }

        val result =
            try {
                logger.info { "Updating ${job.manga}" }
                val performed =
                    Manga.updateMangaAndChapters(
                        job.manga.id,
                        updateManga = updateManga,
                        automated = job.automated,
                        queuedAt = job.queuedAt,
                    )
                if (performed) {
                    job.copy(status = JobStatus.COMPLETE)
                } else {
                    job.copy(
                        status = JobStatus.SKIPPED,
                        skipReason = "ALREADY_CHECKED_OR_NOT_DUE",
                        nextCheckAt =
                            AdaptiveUpdates
                                .entries(listOf(job.manga.id))[job.manga.id]
                                ?.state
                                ?.nextCheck
                                ?.takeIf { it > 0 },
                    )
                }
            } catch (e: Exception) {
                logger.error(e) { "Error while updating ${job.manga}" }
                if (e is CancellationException) {
                    synchronized(workLock) {
                        activeJobs.remove(job.id)
                        if (tracker[job.manga.id]?.id == job.id) tracker[job.manga.id] = job.copy(status = JobStatus.FAILED)
                    }
                    notifyFlowScope.launch { updateStatus(immediate = true, mangaUpdates = listOf(job)) }
                    throw e
                }
                job.copy(status = JobStatus.FAILED)
            }

        synchronized(workLock) {
            activeJobs.remove(job.id)
            if (tracker[job.manga.id]?.id == job.id) tracker[job.manga.id] = result
        }
        val wasLastJob = !isRunning()

        // in case this is the last update job, the running flag has to be true, before it gets set to false, to be able
        // to properly clear the dataloader store in UpdateType
        updateStatus(immediate = wasLastJob, isRunning = true, mangaUpdates = listOf(result))

        if (wasLastJob) {
            updateStatus(immediate = true)
        }
    }

    override fun addCategoriesToUpdateQueue(
        userId: Int,
        categories: List<CategoryDataClass>,
        clear: Boolean?,
        forceAll: Boolean,
        automatedUpdate: Boolean,
    ) {
        synchronized(workLock) {
            if (clear == true && !isRunning()) clearHistory()
            preparations++
        }
        scope
            .launch {
                SyncManager.ensureSync(userId)

                saveLastUpdateTimestamp()

                val includeInUpdateStatusToCategoryMap = categories.groupBy { it.includeInUpdate }
                val excludedCategories = includeInUpdateStatusToCategoryMap[IncludeOrExclude.EXCLUDE].orEmpty()
                val includedCategories = includeInUpdateStatusToCategoryMap[IncludeOrExclude.INCLUDE].orEmpty()
                val unsetCategories = includeInUpdateStatusToCategoryMap[IncludeOrExclude.UNSET].orEmpty()
                val categoriesToUpdate =
                    if (forceAll) {
                        categories
                    } else {
                        includedCategories.ifEmpty { unsetCategories }
                    }
                val skippedCategories = categories.subtract(categoriesToUpdate.toSet()).toList()
                val updateStatusCategories =
                    mapOf(
                        Pair(CategoryUpdateStatus.UPDATING, categoriesToUpdate),
                        Pair(CategoryUpdateStatus.SKIPPED, skippedCategories),
                    )

                logger.debug { "Updating categories: '${categoriesToUpdate.joinToString("', '") { it.name }}'" }

                val categoriesToUpdateMangas =
                    categoriesToUpdate
                        .flatMap { CategoryManga.getCategoryMangaList(userId, it.id) }
                        .distinctBy { it.id }
                val mangasToCategoriesMap = CategoryManga.getMangasCategories(userId, categoriesToUpdateMangas.map { it.id })
                val mangasToUpdate =
                    categoriesToUpdateMangas
                        .asSequence()
                        .filter { it.updateStrategy == UpdateStrategy.ALWAYS_UPDATE }
                        .filter {
                            if (userConfig.excludeUnreadChapters.value(userId)) {
                                (it.unreadCount ?: 0L) == 0L
                            } else {
                                true
                            }
                        }.filter {
                            if (it.initialized && userConfig.excludeNotStarted.value(userId)) {
                                (it.lastReadAt ?: 0L) > 0L
                            } else {
                                true
                            }
                        }.filter {
                            if (userConfig.excludeCompleted.value(userId)) {
                                it.status != MangaStatus.COMPLETED.name
                            } else {
                                true
                            }
                        }.filter {
                            forceAll ||
                                !excludedCategories.any { category ->
                                    mangasToCategoriesMap[it.id]?.contains(category) == true
                                }
                        }.toList()
                val skippedMangas = categoriesToUpdateMangas.subtract(mangasToUpdate.toSet()).toList()

                if (clear == true) {
                    this@Updater.updateStatusCategories = updateStatusCategories
                    this@Updater.updateStatusSkippedMangas = skippedMangas
                } else {
                    this@Updater.updateStatusCategories =
                        mergeCategoryStatusMaps(this@Updater.updateStatusCategories, updateStatusCategories)
                    this@Updater.updateStatusSkippedMangas =
                        (this@Updater.updateStatusSkippedMangas + skippedMangas).distinctBy { it.id }
                }

                if (mangasToUpdate.isEmpty()) {
                    // In case no manga gets updated and no update job was running before, the client would never receive an info
                    // about its update request
                    updateStatus(immediate = true)
                    return@launch
                }

                updateStatus(
                    categoryUpdates =
                        updateStatusCategories[CategoryUpdateStatus.UPDATING]
                            ?.map {
                                CategoryUpdateJob(it, CategoryUpdateStatus.UPDATING)
                            }.orEmpty(),
                    isRunning = true,
                )

                queueMangas(
                    mangasToUpdate
                        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, MangaDataClass::title)),
                    if (automatedUpdate) null else userId,
                    automatedUpdate,
                )
            }.invokeOnCompletion {
                synchronized(workLock) { preparations-- }
                notifyFlowScope.launch { updateStatus(immediate = true) }
            }
    }

    override fun addMangasToQueue(
        mangas: List<MangaDataClass>,
        userId: Int?,
    ) = queueMangas(mangas, userId, automated = false)

    internal fun queueMangas(
        mangas: List<MangaDataClass>,
        userId: Int?,
        automated: Boolean,
    ) {
        // create all manga update jobs before adding them to the queue so that the client is able to calculate the
        // progress properly right form the start
        val entries = if (automated && serverConfig.smartLibraryUpdates.value) AdaptiveUpdates.entries(mangas.map { it.id }) else emptyMap()
        val policies = if (entries.isNotEmpty()) AdaptiveUpdates.policies() else emptyList()
        val providers = if (entries.isNotEmpty()) AdaptiveUpdates.providers() else emptyMap()
        val now = System.currentTimeMillis()
        val jobs =
            synchronized(workLock) {
                mangas.mapNotNull { manga ->
                    val entry = entries[manga.id] ?: AdaptiveEntry()
                    val state = AdaptiveUpdates.freshState(entry.state, now)
                    val skip =
                        automated && manga.initialized && (manga.chaptersLastFetchedAt ?: 0) > 0 &&
                            AdaptiveUpdates.effective(entry, manga.sourceId.toLong(), policies, providers) &&
                            !AdaptiveSchedule.due(state, now)
                    val job =
                        UpdateJob(
                            manga,
                            userId = userId,
                            automated = automated,
                            status = if (skip) JobStatus.SKIPPED else JobStatus.PENDING,
                            skipReason = if (skip) "ADAPTIVE_NOT_DUE" else null,
                            nextCheckAt = if (skip) state.nextCheck else null,
                        )
                    if (automated && serverConfig.smartLibraryUpdates.value && tracker.containsKey(manga.id)) {
                        null
                    } else {
                        tracker[manga.id] = job
                        if (job.status == JobStatus.PENDING) activeJobs[job.id] = job
                        job
                    }
                }
            }
        scope.launch {
            updateStatus(immediate = true, mangaUpdates = jobs)
            jobs.filter { it.status == JobStatus.PENDING }.forEach(::addMangaToQueue)
        }
    }

    private fun addMangaToQueue(job: UpdateJob) {
        val updateChannel = getOrCreateUpdateChannelFor(job.manga.sourceId)
        scope.launch {
            updateChannel.send(job)
        }
    }

    override fun reset() {
        synchronized(workLock) {
            scope.coroutineContext.cancelChildren()
            activeJobs.clear()
            clearHistory()

            scope.launch {
                updateStatus(immediate = true)
            }

            updateChannels.forEach { (_, channel) -> channel.cancel() }
            updateChannels.clear()
        }
    }

    private fun clearHistory() {
        tracker.clear()
        this.mangaUpdates.clear()
        this.categoryUpdates.clear()
        this.updateStatusCategories = emptyMap()
        this.updateStatusSkippedMangas = emptyList()
    }

    /**
     * Returns true if any user who has [mangaId] in their library has the per-user `updateMangas` setting enabled
     */
    private fun anyUserWantsMangaUpdate(mangaId: Int): Boolean =
        transaction {
            MangaUserTable
                .leftJoin(
                    UserSettingsTable,
                    { MangaUserTable.user },
                    { UserSettingsTable.user },
                    { UserSettingsTable.key eq "updateMangas" },
                ).select(MangaUserTable.id)
                .where {
                    (MangaUserTable.manga eq mangaId) and
                        (MangaUserTable.inLibrary eq true) and
                        (UserSettingsTable.value eq "true")
                }.count() > 0
        }

    private fun mergeCategoryStatusMaps(
        existing: Map<CategoryUpdateStatus, List<CategoryDataClass>>,
        new: Map<CategoryUpdateStatus, List<CategoryDataClass>>,
    ): Map<CategoryUpdateStatus, List<CategoryDataClass>> {
        val merged = existing.toMutableMap()
        new.forEach { (status, categories) ->
            merged[status] = (merged[status].orEmpty() + categories).distinctBy { it.id }
        }
        return merged
    }
}
