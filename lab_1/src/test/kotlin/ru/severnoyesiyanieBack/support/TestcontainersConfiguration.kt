package ru.severnoyesiyanieBack.support

import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.testcontainers.postgresql.PostgreSQLContainer

@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {
    // Spring owns the lifecycle, so a cached application context never outlives its database.
    @Bean
    @ServiceConnection
    fun postgres(): PostgreSQLContainer = PostgreSQLContainer("postgres:17-alpine")
        .withDatabaseName("cocktail_bar_test")
        .withUsername("test")
        .withPassword("test")
}
