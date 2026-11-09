package eu.kanade.tachiyomi.network.interceptor

import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.Call
import okhttp3.Connection
import okhttp3.EventListener
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.zstd.Zstd
import okio.Buffer
import okio.GzipSource
import okio.InflaterSource
import okio.buffer
import org.brotli.dec.BrotliInputStream
import java.util.Base64
import java.util.zip.Inflater

/** Dev diagnostics for the hosts involved in the current image-download investigation. */
internal class ImageHostDiagnostics(
    private val enabled: () -> Boolean = { logger.isDebugEnabled() },
    private val emit: (String) -> Unit = { message -> logger.debug { message } },
) : Interceptor {
    // The listener sees wire headers after extension network interceptors have added browser metadata.
    fun listener(): EventListener =
        object : EventListener() {
            override fun requestHeadersEnd(
                call: Call,
                request: Request,
            ) {
                if (!enabled() || !targets(request.url)) return
                emit(
                    "ImageHostDiagnostics id=${id(call)} phase=request " +
                        buildJsonObject {
                            put("url", safeUrl(request.url))
                            put("method", request.method)
                            putJsonObject("headers") {
                                request.headers.safeHeaders(requestHeaders).forEach { (key, value) ->
                                    put(key, value)
                                }
                            }
                            put("cookieNames", cookieNames(request.header("Cookie")))
                            put("authorizationPresent", request.header("Authorization") != null)
                            request.url.queryParameter("token")?.let { token ->
                                put("tokenPresent", true)
                                runCatching {
                                    val payload = String(Base64.getUrlDecoder().decode(token.split('.')[1]), Charsets.UTF_8)
                                    Json
                                        .parseToJsonElement(payload)
                                        .jsonObject["exp"]
                                        ?.jsonPrimitive
                                        ?.longOrNull
                                }.getOrNull()?.let { put("unverifiedTokenExpiresInSeconds", it - System.currentTimeMillis() / 1000) }
                            }
                        },
                )
            }

            override fun connectionAcquired(
                call: Call,
                connection: Connection,
            ) {
                if (!enabled() || !targets(connection.route().address.url)) return
                emit(
                    "ImageHostDiagnostics id=${id(call)} phase=connection " +
                        buildJsonObject {
                            put(
                                "host",
                                connection
                                    .route()
                                    .address.url.host,
                            )
                            put("peer", connection.route().socketAddress.toString())
                            put(
                                "proxyType",
                                connection
                                    .route()
                                    .proxy
                                    .type()
                                    .name,
                            )
                            put("protocol", connection.protocol().toString())
                            put("tls", connection.handshake()?.tlsVersion?.javaName)
                        },
                )
            }
        }

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (!enabled() || !targets(chain.request().url)) return response
        val request = chain.request()
        val diagnostic =
            buildJsonObject {
                put("url", safeUrl(request.url))
                put("status", response.code)
                putJsonObject("headers") { response.headers.safeHeaders(responseHeaders).forEach { (key, value) -> put(key, value) } }
                put("setCookieNames", response.headers.values("Set-Cookie").joinToString(",") { it.substringBefore('=') })
                if (response.code >= 400) {
                    try {
                        put("bodySample", sample(response, request))
                    } catch (e: Exception) {
                        put("bodySampleUnavailable", e.javaClass.simpleName)
                    }
                }
            }
        emit("ImageHostDiagnostics id=${id(chain.call())} phase=response $diagnostic")
        return response
    }

    private fun sample(
        response: Response,
        request: Request,
    ): String {
        val mime = response.header("Content-Type").orEmpty().lowercase()
        if (mime.isNotEmpty() && !mime.startsWith("text/") && !mime.contains("json") && !mime.contains("xml")) {
            return "[non-text body omitted]"
        }
        val peek = Buffer().write(response.peekBody(16384).bytes())
        if (peek.size == 0L) return "[empty body]"
        // This runs before OkHttp/extension decompression; read a bounded copy, never the live body.
        val encoding = response.header("Content-Encoding").orEmpty().lowercase()
        val input =
            when (encoding) {
                "", "identity" -> {
                    peek.inputStream()
                }

                "gzip" -> {
                    GzipSource(peek).buffer().inputStream()
                }

                "br" -> {
                    BrotliInputStream(peek.inputStream())
                }

                "zstd" -> {
                    Zstd.decompress(peek).buffer().inputStream()
                }

                "deflate" -> {
                    val zlib =
                        peek.size >= 2 && (peek[0].toInt() and 0x0f) == 8 &&
                            ((peek[0].toInt() and 0xff) * 256 + (peek[1].toInt() and 0xff)) % 31 == 0
                    InflaterSource(peek, Inflater(!zlib)).buffer().inputStream()
                }

                else -> {
                    return "[unsupported content encoding]"
                }
            }
        val text = input.use { String(it.readNBytes(8192), Charsets.UTF_8) }
        val safe = redact(text, request)
        val readable =
            if (mime.contains("html")) {
                safe
                    .replace(Regex("(?is)<(script|style)\\b[^>]*>.*?</\\1>"), " ")
                    .replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()
            } else {
                safe
            }
        return readable.take(2048) + if (readable.length > 2048) " [truncated]" else ""
    }

    internal fun redact(
        text: String,
        request: Request,
    ): String {
        var safe = text
        val secrets =
            request.url.queryParameterNames
                .flatMap { request.url.queryParameterValues(it).filterNotNull() }
                .filter { it.length >= 4 } +
                request.headers.values("Cookie").flatMap { it.split(';').map { pair -> pair.substringAfter('=', "").trim() } } +
                listOfNotNull(request.header("Authorization"), request.header("Authorization")?.substringAfter(' '))
        secrets.filter { it.isNotEmpty() }.sortedByDescending { it.length }.forEach { safe = safe.replace(it, "[redacted]") }
        safe = urls.replace(safe) { it.value.toHttpUrlOrNull()?.let(::safeUrl) ?: "[redacted URL]" }
        safe = secretFields.replace(safe) { "${it.groupValues[1]}[redacted]" }
        safe = queryValues.replace(safe) { "${it.groupValues[1]}[redacted]" }
        return opaqueValues.replace(safe, "[redacted]")
    }

    private fun Headers.safeHeaders(allowlist: Set<String>): Map<String, String> =
        names()
            .mapNotNull { name ->
                if (name.lowercase() !in allowlist) return@mapNotNull null
                val value = values(name).joinToString(", ")
                name to
                    if (name.equals("Referer", true) || name.equals("Location", true)) {
                        value.toHttpUrlOrNull()?.let(::safeUrl) ?: value.substringBefore('?').substringBefore('#')
                    } else {
                        opaqueValues.replace(value, "[redacted]")
                    }
            }.toMap()

    private fun cookieNames(cookie: String?): String =
        cookie
            .orEmpty()
            .split(';')
            .map {
                it.substringBefore('=').trim()
            }.filter { it.isNotEmpty() }
            .joinToString(",")

    companion object {
        private val logger = KotlinLogging.logger {}
        private val hosts = setOf("kstatic.to", "kagane.moe", "kagane.org", "mangaball.com", "bulbasaur.poke-black-and-white.net")
        private val requestHeaders =
            setOf(
                "origin",
                "referer",
                "user-agent",
                "accept",
                "accept-encoding",
                "accept-language",
                "sec-fetch-site",
                "sec-fetch-mode",
                "sec-fetch-dest",
                "sec-ch-ua",
                "sec-ch-ua-mobile",
                "sec-ch-ua-platform",
                "priority",
                "cache-control",
                "pragma",
                "content-type",
            )
        private val responseHeaders =
            setOf(
                "server",
                "cf-mitigated",
                "cf-ray",
                "cf-cache-status",
                "content-type",
                "content-encoding",
                "content-length",
                "access-control-allow-origin",
                "access-control-allow-credentials",
                "vary",
                "cache-control",
                "age",
                "retry-after",
                "date",
                "via",
                "x-cache",
                "location",
            )
        private val urls = Regex("""https?://[^\s"'<>]+""")
        private val secretFields =
            Regex(
                """(?i)(["']?(?:access[_-]?token|token|authorization|cookie|set-cookie|cf_clearance|password|secret|api[_-]?key)""" +
                    """["']?\s*[:=]\s*)(?:"[^"]*"|'[^']*'|[^\s,;<>{}]+)""",
            )
        private val queryValues = Regex("""([?&][^=&#\s"'<>]+=)[^&#\s"'<>]*""")
        private val opaqueValues = Regex("""[A-Za-z0-9_+/=-]{40,}(?:\.[A-Za-z0-9_+/=-]+)*""")

        internal fun targets(url: HttpUrl): Boolean = url.host in hosts

        internal fun safeUrl(url: HttpUrl): String =
            url
                .newBuilder()
                .username("")
                .password("")
                .query(null)
                .fragment(null)
                .build()
                .toString() +
                if (url.querySize > 0) "?" + url.queryParameterNames.joinToString("&") { "$it=[redacted]" } else ""

        private fun id(call: Call): String = Integer.toHexString(System.identityHashCode(call))
    }
}
