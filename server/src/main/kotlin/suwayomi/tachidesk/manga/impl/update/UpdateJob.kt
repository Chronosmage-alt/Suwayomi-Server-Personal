package suwayomi.tachidesk.manga.impl.update

import suwayomi.tachidesk.manga.model.dataclass.CategoryDataClass
import suwayomi.tachidesk.manga.model.dataclass.MangaDataClass

enum class JobStatus {
    PENDING,
    RUNNING,
    COMPLETE,
    FAILED,
    SKIPPED,
}

data class UpdateJob(
    val manga: MangaDataClass,
    val status: JobStatus = JobStatus.PENDING,
    val userId: Int? = null,
    val automated: Boolean = false,
    val queuedAt: Long = System.nanoTime(),
    val skipReason: String? = null,
    val nextCheckAt: Long? = null,
    val id: Long = nextId.incrementAndGet(),
) {
    companion object {
        private val nextId =
            java.util.concurrent.atomic
                .AtomicLong()
    }
}

enum class CategoryUpdateStatus {
    UPDATING,
    SKIPPED,
}

data class CategoryUpdateJob(
    val category: CategoryDataClass,
    val status: CategoryUpdateStatus,
)
