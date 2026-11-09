package eu.kanade.tachiyomi.network.interceptor

import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.network.parseAs
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import suwayomi.tachidesk.server.serverConfig
import suwayomi.tachidesk.server.util.buildSocksProxyUrl
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeoutException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

class CloudflareInterceptor(
    private val setUserAgent: (String) -> Unit,
) : Interceptor {
    private val logger = KotlinLogging.logger {}

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        logger.trace { "CloudflareInterceptor is being used." }

        val originalResponse = chain.proceed(originalRequest)

        // Check if Cloudflare anti-bot is on
        if (!isCloudflareChallenge(originalResponse)) {
            return originalResponse
        }

        if (!serverConfig.flareSolverrEnabled.value) {
            throw IOException("Cloudflare bypass currently disabled")
        }

        logger.debug { "Cloudflare anti-bot is on, CloudflareInterceptor is kicking in..." }

        val flareResponseFallback = serverConfig.flareSolverrAsResponseFallback.value

        return try {
            resolveCloudflare(chain, originalRequest, originalResponse, flareResponseFallback)
        } catch (e: Exception) {
            originalResponse.close()
            // Because OkHttp's enqueue only handles IOExceptions, wrap the exception so that we don't crash the entire app
            throw IOException(e)
        }
    }

    private fun resolveCloudflare(
        chain: Interceptor.Chain,
        originalRequest: Request,
        originalResponse: Response,
        flareResponseFallback: Boolean,
    ): Response {
        val host = originalRequest.url.host

        while (true) {
            val bypassRequest = CompletableFuture<CFClearance.Result>()
            val inflightRequest = CFClearance.inflightCalls.putIfAbsent(host, bypassRequest)

            val awaitInflightResult = inflightRequest != null
            if (awaitInflightResult) {
                logger.debug { "Waiting for inflight call for host $host" }

                when (val result = awaitInflightResult(inflightRequest)) {
                    is CFClearance.Result.CloudflareBypassed -> {
                        val request =
                            CFClearance.buildRequestWithStoredCookies(
                                originalRequest,
                                result.userAgent,
                            )

                        originalResponse.close()
                        return chain.proceed(request)
                    }

                    is CFClearance.Result.CloudflareNotDetected -> {
                        logger.debug { "Inflight call did not detect Cloudflare for $host, retrying" }
                        continue
                    }
                }
            }

            logger.debug { "Calling FlareSolverr for host $host" }
            try {
                val flareResponse =
                    runBlocking {
                        CFClearance.resolveWithFlareSolver(originalRequest, !flareResponseFallback)
                    }

                val cloudflareDetected =
                    !flareResponse.message.contains("not detected", ignoreCase = true)
                return if (cloudflareDetected) {
                    val request =
                        CFClearance.requestWithFlareSolverr(
                            flareResponse,
                            setUserAgent,
                            originalRequest,
                        )
                    bypassRequest.complete(
                        CFClearance.Result.CloudflareBypassed(
                            flareResponse.solution.userAgent,
                        ),
                    )

                    originalResponse.close()
                    chain.proceed(request)
                } else {
                    CFClearance.inflightCalls.remove(host, bypassRequest)
                    bypassRequest.complete(CFClearance.Result.CloudflareNotDetected)

                    maybeFallbackToFlareSolverResponse(
                        flareResponse,
                        chain,
                        originalRequest,
                        originalResponse,
                        flareResponseFallback,
                    )
                }
            } catch (e: Exception) {
                bypassRequest.completeExceptionally(e)
                throw e
            } finally {
                CFClearance.inflightCalls.remove(host, bypassRequest)
            }
        }
    }

    private fun maybeFallbackToFlareSolverResponse(
        flareResponse: CFClearance.FlareSolverResponse,
        chain: Interceptor.Chain,
        originalRequest: Request,
        originalResponse: Response,
        flareResponseFallback: Boolean,
    ): Response {
        logger.debug { "FlareSolverr failed to detect Cloudflare challenge" }

        if (flareResponseFallback &&
            flareResponse.solution.status in 200..299 &&
            flareResponse.solution.response != null
        ) {
            val isImage =
                flareResponse.solution.response.contains(CHROME_IMAGE_TEMPLATE_REGEX)
            if (!isImage) {
                logger.debug { "Falling back to FlareSolverr response" }

                setUserAgent(flareResponse.solution.userAgent)

                originalResponse.close()
                return originalResponse
                    .newBuilder()
                    .code(flareResponse.solution.status)
                    .body(flareResponse.solution.response.toResponseBody())
                    .build()
            } else {
                logger.debug { "FlareSolverr response is an image html template, not falling back" }
            }
        }

        // A solver that did not detect a challenge may legitimately return no clearance.
        // Preserve the origin response so source interceptors can handle token/auth errors.
        if (flareResponse.solution.cookies.none { it.name in COOKIE_NAMES }) {
            return originalResponse
        }

        val request =
            CFClearance.requestWithFlareSolverr(flareResponse, setUserAgent, originalRequest)

        originalResponse.close()
        return chain.proceed(request)
    }

    private fun awaitInflightResult(future: CompletableFuture<CFClearance.Result>): CFClearance.Result {
        while (true) {
            try {
                return future.get()
            } catch (_: TimeoutException) {
                continue
            } catch (e: ExecutionException) {
                throw e.cause ?: e
            }
        }
    }

    companion object {
        private val ERROR_CODES = listOf(403, 503)
        private val SERVER_CHECK = arrayOf("cloudflare-nginx", "cloudflare")
        val COOKIE_NAMES = listOf("cf_clearance")
        private val CHROME_IMAGE_TEMPLATE_REGEX = Regex("""<title>(.*?) \(\d+×\d+\)</title>""")

        internal fun isCloudflareChallenge(response: Response): Boolean {
            // Cloudflare also proxies ordinary origin errors. Its Server header alone
            // does not mean a challenge was served (e.g. an expired image token).
            if (response.header("cf-mitigated").equals("challenge", ignoreCase = true)) return true
            if (response.code !in ERROR_CODES ||
                SERVER_CHECK.none { it.equals(response.header("Server"), ignoreCase = true) }
            ) {
                return false
            }
            val contentType =
                response
                    .header("Content-Type")
                    .orEmpty()
                    .substringBefore(';')
                    .trim()
            if (!contentType.equals("text/html", ignoreCase = true)) return false

            // peekBody leaves the response available to the extension's interceptors.
            val body = response.peekBody(64 * 1024).string()
            return CHALLENGE_MARKERS.any { body.contains(it, ignoreCase = true) }
        }

        private val CHALLENGE_MARKERS =
            listOf("/cdn-cgi/challenge-platform/", "cf-chl-", "_cf_chl_", "id=\"challenge-form\"", "id='challenge-form'")
    }
}

/*
 * This class is ported from https://github.com/vvanglro/cf-clearance
 * The original code is licensed under Apache 2.0
*/
object CFClearance {
    private val logger = KotlinLogging.logger {}
    private val network: NetworkHelper by injectLazy()
    private val client by lazy {
        @Suppress("OPT_IN_USAGE")
        serverConfig.flareSolverrTimeout
            .map { timeoutInt ->
                val timeout = timeoutInt.seconds
                network.client
                    .newBuilder()
                    .callTimeout(timeout.plus(10.seconds).toJavaDuration())
                    .readTimeout(timeout.plus(5.seconds).toJavaDuration())
                    .build()
            }.stateIn(GlobalScope, SharingStarted.Eagerly, network.client)
    }
    private val json: Json by injectLazy()
    private val jsonMediaType = "application/json".toMediaType()
    private val mutex = Mutex()

    sealed class Result {
        data class CloudflareBypassed(
            val userAgent: String,
        ) : Result()

        data object CloudflareNotDetected : Result()
    }

    val inflightCalls = ConcurrentHashMap<String, CompletableFuture<Result>>()

    fun buildRequestWithStoredCookies(
        request: Request,
        userAgent: String,
    ): Request {
        val cookies =
            network.cookieStore.get(request.url).joinToString("; ", postfix = "; ") {
                "${it.name}=${it.value}"
            }

        logger.trace { "Final cookies\n$cookies" }

        return request
            .newBuilder()
            .header("Cookie", cookies)
            .header("User-Agent", userAgent)
            .build()
    }

    @Serializable
    data class FlareSolverCookie(
        val name: String,
        val value: String,
    )

    @Serializable
    data class FlareSolverProxy(
        val url: String,
    )

    @Serializable
    data class FlareSolverRequest(
        val cmd: String,
        val url: String,
        val maxTimeout: Int? = null,
        val session: String? = null,
        @SerialName("session_ttl_minutes")
        val sessionTtlMinutes: Int? = null,
        val cookies: List<FlareSolverCookie>? = null,
        val returnOnlyCookies: Boolean? = null,
        val proxy: FlareSolverProxy? = null,
        val postData: String? = null, // only used with cmd 'request.post'
    )

    internal fun FlareSolverRequest.withRequestProxy(proxyUrl: String?): FlareSolverRequest =
        proxyUrl?.let { copy(session = null, sessionTtlMinutes = null, proxy = FlareSolverProxy(it)) } ?: this

    @Serializable
    data class FlareSolverSolutionCookie(
        val name: String,
        val value: String,
        val domain: String,
        val path: String? = null,
        val expires: Double? = null,
        val size: Int? = null,
        val httpOnly: Boolean? = null,
        val secure: Boolean? = null,
        val session: Boolean? = null,
        val sameSite: String? = null,
    )

    @Serializable
    data class FlareSolverSolution(
        val url: String,
        val status: Int,
        val headers: Map<String, String>? = null,
        val response: String? = null,
        val cookies: List<FlareSolverSolutionCookie>,
        val userAgent: String,
    )

    @Serializable
    data class FlareSolverResponse(
        val solution: FlareSolverSolution,
        val status: String,
        val message: String,
        val startTimestamp: Long,
        val endTimestamp: Long,
        val version: String,
    )

    suspend fun resolveWithFlareSolver(
        originalRequest: Request,
        onlyCookies: Boolean,
    ): FlareSolverResponse {
        val timeout = serverConfig.flareSolverrTimeout.value.seconds
        val socksProxy = buildSocksProxyUrl()

        return with(json) {
            mutex.withLock {
                client.value
                    .newCall(
                        POST(
                            url = serverConfig.flareSolverrUrl.value.removeSuffix("/") + "/v1",
                            headers = socksProxy?.let { Headers.headersOf("X-Proxy-Server", it) } ?: Headers.headersOf(),
                            body =
                                Json
                                    .encodeToString(
                                        FlareSolverRequest(
                                            "request.${originalRequest.method.lowercase()}",
                                            originalRequest.url.toString(),
                                            session = serverConfig.flareSolverrSessionName.value,
                                            sessionTtlMinutes = serverConfig.flareSolverrSessionTtl.value,
                                            cookies =
                                                network.cookieStore
                                                    .get(originalRequest.url)
                                                    .filter { it.name !in CloudflareInterceptor.COOKIE_NAMES }
                                                    .map { cookie ->
                                                        FlareSolverCookie(cookie.name, cookie.value)
                                                    },
                                            returnOnlyCookies = onlyCookies,
                                            maxTimeout = timeout.inWholeMilliseconds.toInt(),
                                            postData =
                                                if (originalRequest.method == "POST") {
                                                    originalRequest.body
                                                        ?.let { body ->
                                                            Buffer()
                                                                .also { body.writeTo(it) }
                                                                .readUtf8()
                                                        }.orEmpty()
                                                } else {
                                                    null
                                                },
                                        ).withRequestProxy(socksProxy),
                                    ).toRequestBody(jsonMediaType),
                        ),
                    ).awaitSuccess()
                    .parseAs<FlareSolverResponse>()
            }
        }
    }

    fun requestWithFlareSolverr(
        flareSolverResponse: FlareSolverResponse,
        setUserAgent: (String) -> Unit,
        originalRequest: Request,
    ): Request {
        if (flareSolverResponse.solution.cookies.none { it.name in CloudflareInterceptor.COOKIE_NAMES }) {
            logger.debug { "Cloudflare challenge failed to resolve" }
            throw CloudflareBypassException()
        } else {
            setUserAgent(flareSolverResponse.solution.userAgent)
            val cookies =
                flareSolverResponse.solution.cookies
                    .map { cookie ->
                        Cookie
                            .Builder()
                            .name(cookie.name)
                            .value(cookie.value)
                            .domain(cookie.domain.removePrefix("."))
                            .also {
                                if (cookie.httpOnly != null && cookie.httpOnly) it.httpOnly()
                                if (cookie.secure != null && cookie.secure) it.secure()
                                if (!cookie.path.isNullOrEmpty()) it.path(cookie.path)
                                // We need to convert the expires time to milliseconds for the persistent cookie store
                                if (cookie.expires != null && cookie.expires > 0) it.expiresAt((cookie.expires * 1000).toLong())
                                if (!cookie.domain.startsWith('.')) {
                                    it.hostOnlyDomain(cookie.domain.removePrefix("."))
                                }
                            }.build()
                    }.groupBy { it.domain }
                    .flatMap { (domain, cookies) ->
                        network.cookieStore.addAll(
                            HttpUrl
                                .Builder()
                                .scheme("http")
                                .host(domain.removePrefix("."))
                                .build(),
                            cookies,
                        )

                        cookies
                    }

            logger.trace { "New cookies\n${cookies.joinToString("; ")}" }

            return buildRequestWithStoredCookies(
                request = originalRequest,
                userAgent = flareSolverResponse.solution.userAgent,
            )
        }
    }

    private class CloudflareBypassException : Exception()
}
