package com.tahirslist.bootstrap.image

import com.sun.net.httpserver.HttpServer
import com.tahirslist.application.image.ImageFetchException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.net.InetSocketAddress

/**
 * HttpImageFetcher sends a DISCRIPTIVE User-Agent on every fetch. Wikimedia's
 * User-Agent policy rejects clients that present a bare "Java-http-client/..."
 * UA (HTTP 403) even for valid freely-licensed Commons images, so without the
 * header the manifest's freely-licensed hero_urls (Ayat / Punjabi Deli /
 * Yemen Cafe) would keep failing FETCH_FAILED on live boot. This locks in the
 * header and the fetch/error contract (2xx -> bytes, non-2xx -> throw).
 */
class HttpImageFetcherTest : FunSpec() {

    private fun serverWith(handler: (incomingHeaders: Map<String, List<String>>, bytes: ByteArray) -> Pair<Int, ByteArray>): HttpServer {
        val server = HttpServer.create(InetSocketAddress(0), 0)
        server.createContext("/") { exchange ->
            val headers = exchange.requestHeaders.mapKeys { it.key.lowercase() }
            val body = exchange.requestBody.readBytes()
            val (status, resp) = handler(headers, body)
            exchange.sendResponseHeaders(status, resp.size.toLong())
            if (resp.isNotEmpty()) exchange.responseBody.use { it.write(resp) }
            exchange.close()
        }
        server.start()
        return server
    }

    init {
        test("sends a non-empty descriptive User-Agent, not the bare Java UA") {
            val server = serverWith { headers, _ ->
                val ua = headers["user-agent"]?.firstOrNull()
                // The header must be present and must identify the app.
                200 to ua.orEmpty().toByteArray()
            }
            try {
                val body = HttpImageFetcher().fetch("http://127.0.0.1:${server.address.port}/img")
                val ua = body.toString(Charsets.UTF_8)
                ua.isNotBlank() shouldBe true
                ua shouldNotContain "Java-http-client"
                ua shouldContain "TahirsList"
            } finally {
                server.stop(0)
            }
        }

        test("accepts a 200 image and returns its bytes") {
            val server = serverWith { _, _ -> 200 to "jpeg-bytes".toByteArray() }
            try {
                HttpImageFetcher().fetch("http://127.0.0.1:${server.address.port}/img")
                    .toString(Charsets.UTF_8) shouldBe "jpeg-bytes"
            } finally {
                server.stop(0)
            }
        }

        test("throws ImageFetchException on a non-2xx response") {
            val server = serverWith { _, _ -> 403 to "forbidden".toByteArray() }
            try {
                shouldThrow<ImageFetchException> {
                    HttpImageFetcher().fetch("http://127.0.0.1:${server.address.port}/img")
                }
            } finally {
                server.stop(0)
            }
        }
    }
}