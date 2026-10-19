package suwayomi.tachidesk.manga.impl.komga

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

/** Deliberately independent of extension HTTP clients, cookies, interceptors and source scheduling. */
internal class KomgaFailure(
    val code: String,
) : RuntimeException(code)

internal fun JsonObject.text(key: String): String? = (get(key) as? JsonPrimitive)?.content?.takeUnless { it == "null" }

internal fun JsonObject.int(key: String): Int? = text(key)?.toIntOrNull()

internal fun JsonObject.bool(key: String): Boolean? = text(key)?.toBooleanStrictOrNull()

internal fun JsonObject.obj(key: String): JsonObject = get(key) as? JsonObject ?: JsonObject(emptyMap())

internal fun JsonObject.objects(key: String): List<JsonObject> =
    (get(key) as? kotlinx.serialization.json.JsonArray)
        ?.mapNotNull {
            it as? JsonObject
        }.orEmpty()

internal object KomgaSecret {
    fun read(): String {
        try {
            val path =
                Path.of(
                    System.getProperty("suwayomi.komga.apiKeyFile") ?: System.getenv("KOMGA_API_KEY_FILE") ?: "/run/secrets/komga_api_key",
                )
            if (Files.size(path) !in 1..4096) throw KomgaFailure("SECRET_UNAVAILABLE")
            return Files.readString(path).trim().takeIf { it.isNotEmpty() && it.all { char -> char.code in 33..126 } }
                ?: throw KomgaFailure("SECRET_UNAVAILABLE")
        } catch (_: Exception) {
            throw KomgaFailure("SECRET_UNAVAILABLE")
        }
    }
}

internal class KomgaClient(
    private val baseUrl: String,
    private val key: String,
) {
    companion object {
        private val http =
            OkHttpClient
                .Builder()
                .connectTimeout(3, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .callTimeout(8, TimeUnit.SECONDS)
                .followRedirects(false)
                .followSslRedirects(false)
                // All requests are reads, including POST list searches. Recover closed pooled sockets.
                .retryOnConnectionFailure(true)
                .build()

        fun normalizeUrl(
            value: String,
            allowEmpty: Boolean = false,
        ): String {
            if (allowEmpty && value.isBlank()) return ""
            require(value.length <= 2048) { "URL is too long." }
            val url = runCatching { value.trim().toHttpUrl() }.getOrNull()
            require(url != null && url.username.isEmpty() && url.password.isEmpty() && url.query == null && url.fragment == null) {
                "Use an HTTP(S) base URL without credentials, query or fragment."
            }
            return url.toString().trimEnd('/')
        }
    }

    private fun request(
        path: List<String>,
        query: Map<String, String> = emptyMap(),
        body: JsonObject? = null,
    ): JsonObject {
        val url =
            baseUrl
                .toHttpUrl()
                .newBuilder()
                .apply {
                    path.forEach { addPathSegment(it) }
                    query.forEach { (name, value) -> addQueryParameter(name, value) }
                }.build()
        val request =
            Request
                .Builder()
                .url(url)
                .header("X-API-Key", key)
                .header("Accept", "application/json")
                .apply {
                    if (body != null) post(body.toString().toRequestBody("application/json".toMediaType()))
                }.build()
        try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw KomgaFailure(
                        when (response.code) {
                            401 -> "AUTH_FAILED"
                            403 -> "ACCESS_DENIED"
                            404, 405 -> "NOT_FOUND"
                            429 -> "RATE_LIMITED"
                            in 300..399 -> "REDIRECT"
                            else -> "REMOTE_ERROR"
                        },
                    )
                }
                val bytes = response.body.byteStream().readNBytes(4 * 1024 * 1024 + 1)
                if (bytes.size > 4 * 1024 * 1024) throw KomgaFailure("RESPONSE_TOO_LARGE")
                return Json.parseToJsonElement(bytes.decodeToString()).jsonObject
            }
        } catch (failure: KomgaFailure) {
            throw failure
        } catch (_: java.io.IOException) {
            throw KomgaFailure("UNREACHABLE")
        } catch (_: Exception) {
            throw KomgaFailure("INVALID_RESPONSE")
        }
    }

    private fun fallback(
        primary: () -> JsonObject,
        alternate: () -> JsonObject,
    ): JsonObject =
        try {
            primary()
        } catch (failure: KomgaFailure) {
            if (failure.code == "NOT_FOUND") alternate() else throw failure
        }

    fun test() {
        val user = fallback({ request(listOf("api", "v2", "users", "me")) }, { request(listOf("api", "v1", "users", "me")) })
        if (user.text("id").isNullOrBlank()) throw KomgaFailure("INVALID_RESPONSE")
    }

    fun series(id: String): JsonObject =
        request(listOf("api", "v1", "series", id)).also {
            if (it.text("id") != id || it.bool("deleted") == true) throw KomgaFailure("NOT_FOUND")
        }

    // GET endpoints are available on older Komga releases. POST alternatives are read-only searches.
    fun search(
        title: String,
        page: Int,
    ): JsonObject =
        fallback(
            { request(listOf("api", "v1", "series"), mapOf("search" to title, "page" to "$page", "size" to "20", "deleted" to "false")) },
            {
                request(
                    listOf("api", "v1", "series", "list"),
                    mapOf("page" to "$page", "size" to "20"),
                    buildJsonObject {
                        put("fullTextSearch", title)
                    },
                )
            },
        )

    fun books(
        series: String,
        page: Int,
    ): JsonObject =
        fallback(
            {
                request(
                    listOf("api", "v1", "series", series, "books"),
                    mapOf(
                        "page" to "$page",
                        "size" to "200",
                        "deleted" to "false",
                        "sort" to "url,asc",
                    ),
                )
            },
            {
                request(
                    listOf("api", "v1", "books", "list"),
                    mapOf("page" to "$page", "size" to "200", "sort" to "url,asc"),
                    buildJsonObject {
                        put(
                            "condition",
                            buildJsonObject {
                                put(
                                    "seriesId",
                                    buildJsonObject {
                                        put("operator", "is")
                                        put("value", series)
                                    },
                                )
                            },
                        )
                    },
                )
            },
        )
}
