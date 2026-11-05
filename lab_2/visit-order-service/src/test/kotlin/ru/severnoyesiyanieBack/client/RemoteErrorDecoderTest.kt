package ru.severnoyesiyanieBack.client

import feign.FeignException
import feign.Request
import feign.RequestTemplate
import feign.Response
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.nio.charset.StandardCharsets.UTF_8

class RemoteErrorDecoderTest {
    private val decoder = RemoteErrorDecoder()
    private val request = Request.create(Request.HttpMethod.GET, "http://localhost/api/users/7", emptyMap(), null, UTF_8, RequestTemplate())

    @Test
    fun `business response retains status body and content type`() {
        val response = Response.builder().request(request).status(404).reason("Not found")
            .headers(mapOf("Content-Type" to listOf("application/json"))).body("{\"message\":\"User 7 not found\"}", UTF_8).build()
        val exception = decoder.decode("UserClient#getUser", response)
        assertInstanceOf(RemoteBusinessException::class.java, exception)
        exception as RemoteBusinessException
        assertEquals(404, exception.status)
        assertEquals("application/json", exception.contentType)
        assertEquals("{\"message\":\"User 7 not found\"}", exception.message)
    }

    @Test
    fun `empty business response still has a readable message`() {
        val exception = decoder.decode("UserClient#getUser", Response.builder().request(request).status(400).reason("Bad request").build())
        assertInstanceOf(RemoteBusinessException::class.java, exception)
        assertEquals("Remote request was rejected", exception.message)
    }

    @Test
    fun `server failures remain infrastructure exceptions for the circuit breaker`() {
        val exception = decoder.decode("UserClient#getUser", Response.builder().request(request).status(503).reason("Unavailable").build())
        assertInstanceOf(FeignException::class.java, exception)
        assertFalse(exception is RemoteBusinessException)
    }
}
