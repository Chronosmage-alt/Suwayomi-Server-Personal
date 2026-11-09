package eu.kanade.tachiyomi.network.interceptor

import io.mockk.every
import io.mockk.mockk
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import okio.GzipSink
import okio.buffer
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ImageHostDiagnosticsTest {
    private val messages = mutableListOf<String>()
    private val diagnostics = ImageHostDiagnostics({ true }, messages::add)
    private val call = mockk<Call>()
    private val request =
        Request
            .Builder()
            .url("https://kstatic.to/api/v2/books/page/original/book/page.webp?token=private-token&other=private-query")
            .header("Origin", "https://kagane.moe")
            .header("Referer", "https://kagane.moe/reader?token=private-referrer")
            .header("User-Agent", "Browser test")
            .header("Sec-Fetch-Mode", "cors")
            .header("Cookie", "cf_clearance=private-cookie; sid=abc")
            .header("Authorization", "Bearer private-auth")
            .build()

    private fun run(
        body: ByteArray,
        type: String = "application/json",
        encoding: String? = null,
        status: Int = 403,
        active: ImageHostDiagnostics = diagnostics,
    ): Response {
        val original =
            Response
                .Builder()
                .request(request)
                .protocol(Protocol.HTTP_2)
                .code(status)
                .message("test")
                .header("Content-Type", type)
                .header("Server", "cloudflare")
                .header("cf-ray", "test-ray-LHR")
                .header("Set-Cookie", "session=private-response-cookie; Secure")
                .apply { encoding?.let { header("Content-Encoding", it) } }
                .body(body.toResponseBody())
                .build()
        val chain = mockk<Interceptor.Chain>()
        every { chain.request() } returns request
        every { chain.call() } returns call
        every { chain.proceed(request) } returns original
        assertSame(original, active.intercept(chain))
        return original
    }

    @Test
    fun `wire headers are logged without cookie authorization or query values`() {
        diagnostics.listener().requestHeadersEnd(call, request)
        val log = messages.single()
        assertTrue(log.contains("cors"))
        assertTrue(log.contains("Browser test"))
        assertTrue(log.contains("https://kagane.moe"))
        assertTrue(log.contains("cf_clearance,sid"))
        for (secret in listOf("private-token", "private-query", "private-referrer", "private-cookie", "private-auth", "Bearer", "=abc")) {
            assertFalse(log.contains(secret), secret)
        }
    }

    @Test
    fun `JSON rejection and routing headers are captured without consuming response`() {
        val body = """{"error":-7,"token":"private-token","cookie":"abc"}""".toByteArray()
        run(body).use { assertTrue(it.body.bytes().contentEquals(body)) }
        val log = messages.single()
        assertTrue(log.contains("-7"))
        assertTrue(log.contains("test-ray-LHR"))
        assertTrue(log.contains("session"))
        for (secret in listOf("private-token", "private-cookie", "private-response-cookie", "abc")) assertFalse(log.contains(secret))
    }

    @Test
    fun `gzip HTML is decoded and stripped to readable error while preserving compressed body`() {
        val html = "<html><title>Access denied</title><style>hidden css</style><body>Error 1020 token=private-token</body></html>"
        val buffer = Buffer()
        GzipSink(buffer).buffer().use { it.writeUtf8(html) }
        val compressed = buffer.readByteArray()
        run(compressed, "text/html", "gzip").use { assertTrue(it.body.bytes().contentEquals(compressed)) }
        assertTrue(messages.single().contains("Error 1020"))
        assertFalse(messages.single().contains("hidden css"))
        assertFalse(messages.single().contains("private-token"))
    }

    @Test
    fun `Brotli Zstd and both deflate formats are sampled without changing wire bodies`() {
        val fixtures =
            listOf(
                "br" to "iwWAeyJlcnJvciI6LTd9Aw==",
                "zstd" to "KLUv/SAMYQAAeyJlcnJvciI6LTd9",
                "deflate" to "eJyrVkotKsovUrLSNa8FABuDBAU=",
                "deflate" to "q1ZKLSrKL1Ky0jWvBQA=",
            )
        for ((encoding, encoded) in fixtures) {
            messages.clear()
            val compressed = Base64.getDecoder().decode(encoded)
            run(compressed, encoding = encoding).use { assertTrue(it.body.bytes().contentEquals(compressed)) }
            assertTrue(messages.single().contains("-7"), encoding)
            assertFalse(messages.single().contains("bodySampleUnavailable"), encoding)
        }
    }

    @Test
    fun `binary rejection is omitted and success bodies are not sampled`() {
        run(byteArrayOf(1, 2, 3), "image/webp").close()
        assertTrue(messages.single().contains("non-text body omitted"))
        messages.clear()
        run("image".toByteArray(), "image/webp", status = 200).use { assertEquals("image", it.body.string()) }
        assertFalse(messages.single().contains("bodySample"))
    }

    @Test
    fun `malformed compressed error does not break caller`() {
        run("not gzip".toByteArray(), "text/html", "gzip").use { assertEquals("not gzip", it.body.string()) }
        assertTrue(messages.single().contains("bodySampleUnavailable"))
    }

    @Test
    fun `samples are bounded and disabled diagnostics leave response unchanged`() {
        run(("x ".repeat(10000)).toByteArray(), "text/plain").close()
        assertTrue(messages.single().contains("[truncated]"))
        assertTrue(messages.single().length < 3500)
        messages.clear()
        run("body".toByteArray(), active = ImageHostDiagnostics({ false }, messages::add)).use {
            assertEquals("body", it.body.string())
        }
        assertTrue(messages.isEmpty())
    }

    @Test
    fun `URL credentials fragments and all query values are redacted`() {
        val url =
            request.url
                .newBuilder()
                .username("private-user")
                .password("private-password")
                .fragment("private-fragment")
                .build()
        val safe = ImageHostDiagnostics.safeUrl(url)
        for (secret in listOf(
            "private-user",
            "private-password",
            "private-fragment",
            "private-token",
            "private-query",
        )) {
            assertFalse(safe.contains(secret))
        }
        assertTrue(safe.contains("token=[redacted]"))
        assertFalse(
            ImageHostDiagnostics.targets(
                Request
                    .Builder()
                    .url("https://kstatic.to.evil.example/")
                    .build()
                    .url,
            ),
        )
    }

    @Test
    fun `echoed secrets and embedded URLs are redacted`() {
        val text = "private-cookie abc private-auth /image?signature=short-secret https://user:pass@kstatic.to/path?key=short#fragment"
        val safe = diagnostics.redact(text, request)
        for (secret in listOf(
            "private-cookie",
            "abc",
            "private-auth",
            "short-secret",
            "user:pass",
            "key=short",
            "fragment",
        )) {
            assertFalse(safe.contains(secret), secret)
        }
    }
}
