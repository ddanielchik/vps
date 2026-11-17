package ru.severnoyesiyanieBack.integration

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import ru.severnoyesiyanieBack.entity.UserRole
import ru.severnoyesiyanieBack.support.RemoteServicesStub
import ru.severnoyesiyanieBack.support.TestcontainersConfiguration
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper

@SpringBootTest(properties = [
    "spring.config.name=application-test", "spring.cloud.config.enabled=false", "eureka.client.enabled=false",
    "spring.cloud.discovery.enabled=false", "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.open-in-view=false",
    "spring.cloud.openfeign.circuitbreaker.enabled=true",
    "spring.cloud.circuitbreaker.resilience4j.disable-thread-pool=true",
    "resilience4j.circuitbreaker.configs.default.slidingWindowSize=2",
    "resilience4j.circuitbreaker.configs.default.minimumNumberOfCalls=2",
    "resilience4j.circuitbreaker.configs.default.permittedNumberOfCallsInHalfOpenState=1",
    "resilience4j.circuitbreaker.configs.default.ignoreExceptions[0]=ru.severnoyesiyanieBack.client.RemoteBusinessException",
    "spring.cloud.openfeign.client.config.default.connectTimeout=1000",
    "spring.cloud.openfeign.client.config.default.readTimeout=1000"
])
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration::class)
abstract class ApiIntegrationSupport {
    @Autowired protected lateinit var mockMvc: MockMvc
    @Autowired protected lateinit var jdbc: JdbcTemplate
    @Autowired protected lateinit var objectMapper: ObjectMapper
    @Autowired protected lateinit var breakers: CircuitBreakerRegistry

    @BeforeEach
    fun resetFixtures() {
        check(jdbc.queryForObject("SELECT current_database()", String::class.java) == "cocktail_bar_test")
        jdbc.execute("TRUNCATE order_items, orders, visit_taste_preferences, visits RESTART IDENTITY CASCADE")
        remote.reset()
        breakers.allCircuitBreakers.forEach { it.reset() }
    }

    protected fun json(result: ResultActions): JsonNode = objectMapper.readTree(result.andReturn().response.contentAsString)
    protected fun createClient(email: String = "client@example.com"): Int = remote.createUser(UserRole.CLIENT, email)
    protected fun createStaff(role: String): Int = remote.createUser(UserRole.valueOf(role), "${role.lowercase()}@example.com")
    protected fun createMenuItem(name: String = "Northern Lights", price: String = "125.50", tags: String = "1,2"): Int =
        remote.createMenu(name, price.toBigDecimal(), tags.split(',').filter(String::isNotBlank).map(String::toInt).toSet())

    protected fun createVisit(clientId: Int = createClient()): Int = json(mockMvc.perform(
        post("/api/visits").contentType(MediaType.APPLICATION_JSON)
            .content("""{"clientId":$clientId,"tableNumber":4}""")
    ).andExpect(status().isCreated))["idVisit"].asInt()

    protected fun count(table: String): Int = requireNotNull(jdbc.queryForObject("SELECT COUNT(*) FROM $table", Int::class.java))

    companion object {
        @JvmField val remote = RemoteServicesStub().also { stub ->
            Runtime.getRuntime().addShutdownHook(Thread { stub.close() })
        }

        @JvmStatic @DynamicPropertySource
        fun remoteProperties(registry: DynamicPropertyRegistry) {
            registry.add("clients.user.url") { remote.url }
            registry.add("clients.menu.url") { remote.url }
        }
    }
}
