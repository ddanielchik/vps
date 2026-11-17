package ru.severnoyesiyanieback.menu

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.servers.Server
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@org.springframework.cloud.openfeign.EnableFeignClients
@SpringBootApplication
@OpenAPIDefinition(servers = [Server(url = "/")])
class MenuServiceApplication

fun main(args: Array<String>) {
    runApplication<MenuServiceApplication>(*args)
}
