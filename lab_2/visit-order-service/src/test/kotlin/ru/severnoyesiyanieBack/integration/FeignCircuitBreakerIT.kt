package ru.severnoyesiyanieBack.integration

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class FeignCircuitBreakerIT : ApiIntegrationSupport() {
    @Test
    fun `remote business errors retain their HTTP status and do not open circuit breaker`() {
        repeat(4) {
            mockMvc.perform(post("/api/visits").contentType(MediaType.APPLICATION_JSON)
                .content("""{"clientId":999,"tableNumber":1}"""))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.message").value("User 999 not found"))
        }
        assertEquals(4, remote.userRequests.get())
        assertTrue(breakers.allCircuitBreakers.all { it.state == CircuitBreaker.State.CLOSED })
        assertTrue(breakers.allCircuitBreakers.all { it.metrics.numberOfFailedCalls == 0 })
        assertEquals(0, count("visits"))
    }

    @Test
    fun `failures open the breaker stop HTTP calls and permit recovery without partial writes`() {
        val user = createClient()
        remote.userFailureStatus.set(503)
        repeat(3) {
            mockMvc.perform(post("/api/visits").contentType(MediaType.APPLICATION_JSON)
                .content("""{"clientId":$user,"tableNumber":1}"""))
                .andExpect(status().isServiceUnavailable)
                .andExpect(jsonPath("$.message").isNotEmpty)
        }
        assertEquals(2, remote.userRequests.get())
        assertTrue(breakers.allCircuitBreakers.any { it.state == CircuitBreaker.State.OPEN })
        assertEquals(0, count("visits"))
        remote.userFailureStatus.set(0)
        breakers.allCircuitBreakers.filter { it.state == CircuitBreaker.State.OPEN }
            .forEach { it.transitionToHalfOpenState() }
        createVisit(user)
        assertEquals(3, remote.userRequests.get())
        assertEquals(1, count("visits"))
        assertTrue(breakers.allCircuitBreakers.all { it.state == CircuitBreaker.State.CLOSED })
    }
}
