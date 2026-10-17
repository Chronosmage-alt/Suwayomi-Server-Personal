package suwayomi.tachidesk.manga.impl.update.adaptive

import kotlinx.serialization.Serializable
import java.security.MessageDigest

enum class ChapterDateOrigin { EXTENSION_REPORTED, UNKNOWN, FALLBACK, MISSING }

enum class HistoricalReason {
    PROVISIONAL,
    ACTIVE_SCHEDULE,
    MISSING_DATES,
    UNKNOWN_DATES,
    FALLBACK_DATES,
    INVALID_NUMBERS,
    INVALID_DATES,
    DUPLICATE_DATES,
    REVERSED_DATES,
    BULK_HISTORY,
    INSUFFICIENT_EVENTS,
    UNSUPPORTED_INTERVAL,
    INCONSISTENT_INTERVALS,
    STALE_HISTORY,
    SUPPRESSED,
    WAITING_FOR_NEW_HISTORY,
    AMBIGUOUS_ADDITIONS,
    CONTRADICTED,
    EXPIRED,
}

/** One bounded summary, never a chapter list. Null on legacy/unassessed state. */
@Serializable
data class HistoricalDiagnostic(
    val reason: HistoricalReason,
    val assessedAt: Long? = null,
    val inspectedNumbers: Int = 0,
    val missingDates: Int = 0,
    val unknownDates: Int = 0,
    val fallbackDates: Int = 0,
    val eligibleDates: Int = 0,
    val events: Int = 0,
    val intervalDays: Double? = null,
    val latestEventAgeDays: Double? = null,
)

/** Opaque fingerprint of URL + persisted date; no title, URL or chapter metadata. */
@Serializable
data class ChapterDateEvidence(
    val fingerprint: String,
    val origin: ChapterDateOrigin,
)

object HistoricalEvidence {
    const val MAX_ENTRIES = 96

    private fun fingerprint(chapter: ObservedChapter): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest("${chapter.url}\u0000${chapter.uploadDate}".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    /** Do not mistake unchanged legacy/fallback dates for newly supplied source evidence. */
    fun classify(
        evidence: List<ChapterDateEvidence>,
        previous: List<ObservedChapter>,
        fetched: List<ObservedChapter>,
        evidenceThrough: Long = 0,
    ): List<ObservedChapter> {
        val known = evidence.associate { it.fingerprint to it.origin }
        val oldByUrl =
            previous.associate { chapter ->
                chapter.url to (chapter.uploadDate to (known[fingerprint(chapter)] ?: ChapterDateOrigin.UNKNOWN))
            }
        val oldByDate = oldByUrl.values.groupBy { it.first }.mapValues { (_, dates) -> dates.maxBy { it.second.ordinal }.second }
        return fetched.map { chapter ->
            val origin =
                when {
                    chapter.uploadDate <= 0 -> {
                        ChapterDateOrigin.MISSING
                    }

                    known.containsKey(fingerprint(chapter)) -> {
                        known.getValue(fingerprint(chapter))
                    }

                    else -> {
                        // A changed URL can still be a stored chapter reused by an extension.
                        oldByUrl[chapter.url]?.takeIf { it.first == chapter.uploadDate }?.second
                            ?: oldByDate[chapter.uploadDate]
                            // Deleted/evicted entries cannot return as new historical proof.
                            ?: if (chapter.uploadDate <=
                                evidenceThrough
                            ) {
                                ChapterDateOrigin.UNKNOWN
                            } else {
                                ChapterDateOrigin.EXTENSION_REPORTED
                            }
                    }
                }
            chapter.copy(origin = origin)
        }
    }

    fun retain(
        classified: List<ObservedChapter>,
        stored: List<ObservedChapter>,
        previous: List<ObservedChapter> = emptyList(),
        previousEvidence: List<ChapterDateEvidence> = emptyList(),
    ): List<ChapterDateEvidence> {
        val persisted = stored.associateBy { it.url }
        val oldByUrl = previous.associateBy { it.url }
        val known = previousEvidence.associate { it.fingerprint to it.origin }
        return classified
            .distinctBy { it.url }
            .sortedByDescending { it.number }
            .take(MAX_ENTRIES)
            .map { chapter ->
                val saved = persisted[chapter.url] ?: chapter
                val origin =
                    if (chapter.origin == ChapterDateOrigin.MISSING) {
                        // Keeping an existing DB date does not change its original provenance.
                        val old = oldByUrl[chapter.url]?.takeIf { it.uploadDate == saved.uploadDate }
                        if (old == null) ChapterDateOrigin.FALLBACK else known[fingerprint(old)] ?: ChapterDateOrigin.UNKNOWN
                    } else {
                        chapter.origin
                    }
                ChapterDateEvidence(fingerprint(saved), origin)
            }
    }
}
