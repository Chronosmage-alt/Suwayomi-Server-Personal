package suwayomi.tachidesk.manga.impl.komga

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject

internal data class KomgaPage(
    val items: List<JsonObject>,
    val hasNext: Boolean,
    val totalElements: Int?,
    val totalPages: Int?,
    val size: Int?,
)

internal object KomgaPagination {
    fun read(
        result: JsonObject,
        page: Int,
        maximumSize: Int,
        requireSorted: Boolean = false,
    ): KomgaPage {
        fun invalid(): Nothing = throw KomgaFailure("INVALID_RESPONSE")

        fun optionalInt(key: String): Int? = if (key in result) result.int(key) ?: invalid() else null

        fun optionalBool(key: String): Boolean? = if (key in result) result.bool(key) ?: invalid() else null
        val content = result["content"] as? JsonArray ?: invalid()
        if (content.any { it !is JsonObject } || content.size > maximumSize) invalid()
        val last = result.bool("last") ?: invalid()
        val size = optionalInt("size")
        val total = optionalInt("totalElements")
        val pages = optionalInt("totalPages")
        if (optionalInt("number")?.let { it != page } == true ||
            optionalInt("numberOfElements")?.let { it != content.size } == true ||
            optionalBool("empty")?.let { it != content.isEmpty() } == true ||
            optionalBool("first")?.let { it != (page == 0) } == true
        ) {
            invalid()
        }
        if (size != null && (size !in 1..maximumSize || content.size > size || (!last && content.size != size))) invalid()
        if (!last && content.isEmpty()) invalid()
        if (pages != null && (
                pages < 0 || (pages == 0 && (page != 0 || content.isNotEmpty())) ||
                    (pages > 0 && page >= pages) || last != (page + 1 >= pages)
            )
        ) {
            invalid()
        }
        if (total != null) {
            if (total < 0 || total < content.size || (total == 0 && (page != 0 || content.isNotEmpty() || !last))) invalid()
            if (size != null) {
                val expectedPages = (total.toLong() + size - 1) / size
                if (pages != null && expectedPages != pages.toLong()) invalid()
                val consumed = page.toLong() * size + content.size
                if ((last && consumed != total.toLong()) || (!last && consumed >= total)) invalid()
            }
        }
        if (requireSorted && (result.obj("sort").bool("sorted") == false || result.obj("sort").bool("unsorted") == true)) invalid()
        return KomgaPage(content.map { it as JsonObject }, !last, total, pages, size)
    }
}
