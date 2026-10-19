package suwayomi.tachidesk.manga.impl.komga

import com.sun.net.httpserver.HttpServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class KomgaClientContractTest {
    @Test fun getPaginationUsesSupportedStableSortingAcrossRepeatedChapterNumbers() = checkPagination(false)

    @Test fun postFallbackKeepsTheSameSortAndReadOnlySeriesFilter() = checkPagination(true)

    private fun checkPagination(postOnly: Boolean) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = CopyOnWriteArrayList<String>()
        val errors = CopyOnWriteArrayList<String>()
        // Scrambled physical inventory; number metadata is deliberately non-unique.
        val inventory =
            (0 until 401).reversed().map { index ->
                """{"id":"b$index","seriesId":"s","url":"file:/collection/${index.toString().padStart(4, '0')}",
                "metadata":{"number":"1"}}"""
            }
        server.createContext("/komga/") { exchange ->
            val url = "http://localhost${exchange.requestURI}".toHttpUrl()
            requests.add("${exchange.requestMethod} ${url.encodedPath}")
            val fallback = url.encodedPath == "/komga/api/v1/books/list"
            val body = exchange.requestBody.use { it.readBytes().decodeToString() }
            var code = 200
            if (postOnly && !fallback) {
                code = 405
            } else {
                if (url.queryParameterValues("sort") != listOf("url,asc")) errors.add("Unsupported or missing sort")
                if (url.queryParameter("size") != "200") errors.add("Wrong page size")
                if (exchange.requestHeaders.getFirst("X-API-Key") != "contract-fixture") errors.add("Wrong authentication")
                if (fallback) {
                    if (exchange.requestMethod != "POST") errors.add("Fallback must be POST search")
                    val filter =
                        Json
                            .parseToJsonElement(body)
                            .jsonObject
                            .obj("condition")
                            .obj("seriesId")
                    if (filter.text("operator") != "is" || filter.text("value") != "s") errors.add("Wrong series filter")
                } else if (exchange.requestMethod != "GET" || url.encodedPath != "/komga/api/v1/series/s/books") {
                    errors.add("Unexpected write or endpoint")
                }
                if (errors.isNotEmpty()) code = 400
            }
            val page = url.queryParameter("page")!!.toInt()
            val sorted = inventory.sortedBy { Json.parseToJsonElement(it).jsonObject.text("url") }
            val items = sorted.drop(page * 200).take(200)
            val response =
                """{"content":[${items.joinToString(",")}],"number":$page,"size":200,
                "numberOfElements":${items.size},"totalPages":3,"totalElements":401,"last":${page == 2},
                "sort":{"sorted":true,"unsorted":false}}""".toByteArray()
            exchange.sendResponseHeaders(code, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val client = KomgaClient("http://127.0.0.1:${server.address.port}/komga", "contract-fixture")
            val pages = (0..2).map { KomgaPagination.read(client.books("s", it), it, 200, requireSorted = true) }
            assertEquals((0 until 401).map { "b$it" }, pages.flatMap { it.items }.map { it.text("id") })
            assertEquals(listOf(true, true, false), pages.map { it.hasNext })
            assertEquals(emptyList(), errors.toList())
            assertEquals(if (postOnly) 6 else 3, requests.size)
            assertFalse(requests.any { it.startsWith("PUT") || it.startsWith("PATCH") || it.startsWith("DELETE") })
        } finally {
            server.stop(0)
        }
    }
}
