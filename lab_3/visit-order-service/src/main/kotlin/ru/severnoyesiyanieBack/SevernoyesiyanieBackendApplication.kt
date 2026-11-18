package ru.severnoyesiyanieBack

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.servers.Server
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import reactivefeign.spring.config.EnableReactiveFeignClients

@SpringBootApplication
@EnableReactiveFeignClients
@OpenAPIDefinition(servers = [Server(url = "/")])
class SevernoyesiyanieBackendApplication

fun main(args: Array<String>) {
    runApplication<SevernoyesiyanieBackendApplication>(*args)
}
