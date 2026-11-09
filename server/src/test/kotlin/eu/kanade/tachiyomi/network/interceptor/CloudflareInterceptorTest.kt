package eu.kanade.tachiyomi.network.interceptor

import io.mockk.every
import io.mockk.mockk
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class CloudflareInterceptorTest {
    private val request = Request.Builder().url("https://images.example/chapter/1?token=expired").build()

    private fun response(
        code: Int = 403,
        contentType: String = "application/json",
        body: String = """{"error": -7}""",
    ): Response =
        Response
            .Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("Rejected")
            .header("Server", "cloudflare")
            .header("Content-Type", contentType)
            .body(body.toResponseBody())
            .build()

    @Test
    fun `JSON image rejection reaches the source unchanged without invoking solver`() {
        val original = response()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.proceed(request) } returns original
        val actual = CloudflareInterceptor { error("Must not change user agent") }.intercept(chain)
        assertSame(original, actual)
        assertEquals("""{"error": -7}""", actual.body.string())
    }

    @Test
    fun `source can refresh rejected token on either side of Cloudflare interceptor`() {
        for (cloudflareFirst in listOf(true, false)) {
            var attempts = 0
            val cloudflare = CloudflareInterceptor { error("Must not change user agent") }
            val refresh =
                Interceptor { chain ->
                    val rejected = chain.proceed(chain.request())
                    assertEquals(403, rejected.code)
                    assertEquals("""{"error": -7}""", rejected.body.string())
                    rejected.close()
                    chain.proceed(
                        chain
                            .request()
                            .newBuilder()
                            .url(
                                request.url
                                    .newBuilder()
                                    .setQueryParameter("token", "fresh")
                                    .build(),
                            ).build(),
                    )
                }
            val client =
                OkHttpClient
                    .Builder()
                    .addInterceptor(if (cloudflareFirst) cloudflare else refresh)
                    .addInterceptor(if (cloudflareFirst) refresh else cloudflare)
                    .addInterceptor { chain ->
                        attempts++
                        if (chain.request().url.queryParameter("token") == "fresh") {
                            response(200, "image/png", "image").newBuilder().request(chain.request()).build()
                        } else {
                            response()
                        }
                    }.build()
            client.newCall(request).execute().use {
                assertEquals(200, it.code)
                assertEquals("image", it.body.string())
                assertEquals(2, attempts)
            }
        }
    }

    @Test
    fun `proxied origin errors are not challenges`() {
        for (code in listOf(401, 403, 503, 507)) {
            response(code).use { assertFalse(CloudflareInterceptor.isCloudflareChallenge(it)) }
        }
        response(contentType = "text/html", body = "<html>Access denied by image host</html>")
            .use { assertFalse(CloudflareInterceptor.isCloudflareChallenge(it)) }
    }

    @Test
    fun `Cloudflare mitigation header identifies challenge regardless of status`() {
        response(429).newBuilder().header("cf-mitigated", "challenge").build().use {
            assertTrue(CloudflareInterceptor.isCloudflareChallenge(it))
        }
    }

    @Test
    fun `legacy challenge is detected without consuming body`() {
        val html = "<html><script src='/cdn-cgi/challenge-platform/test'></script></html>"
        response(503, "text/html; charset=utf-8", html).use {
            assertTrue(CloudflareInterceptor.isCloudflareChallenge(it))
            assertEquals(html, it.body.string())
        }
    }

    @Test
    fun `challenge inspection is bounded`() {
        response(contentType = "text/html", body = "x".repeat(65536) + "cf-chl-test").use {
            assertFalse(CloudflareInterceptor.isCloudflareChallenge(it))
        }
    }

    @Test
    fun `generic solver success still requires clearance for a challenge`() {
        val result =
            CFClearance.FlareSolverResponse(
                solution = CFClearance.FlareSolverSolution(request.url.toString(), 200, cookies = emptyList(), userAgent = "browser"),
                status = "ok",
                message = "Done",
                startTimestamp = 0,
                endTimestamp = 1,
                version = "3.1.1",
            )
        assertFails {
            CFClearance.requestWithFlareSolverr(result, { error("Must not change user agent") }, request)
        }
    }
}
