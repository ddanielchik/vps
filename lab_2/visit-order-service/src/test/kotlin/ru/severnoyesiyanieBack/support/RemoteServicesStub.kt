package ru.severnoyesiyanieBack.support

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import ru.severnoyesiyanieBack.client.TasteTagResponse
import ru.severnoyesiyanieBack.dto.MenuItemResponse
import ru.severnoyesiyanieBack.dto.UserResponse
import ru.severnoyesiyanieBack.entity.UserRole
import tools.jackson.databind.json.JsonMapper
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/** Real HTTP peers: tests exercise the configured Feign encoders, decoders and circuit breakers. */
class RemoteServicesStub : AutoCloseable {
    private val json = JsonMapper.builder().build()
    private val executor = Executors.newCachedThreadPool { runnable -> Thread(runnable).apply { isDaemon = true } }
    private val server = HttpServer.create(InetSocketAddress("localhost", 0), 0).apply {
        createContext("/") { exchange -> handle(exchange) }
        this.executor = this@RemoteServicesStub.executor
        start()
    }
    val url: String get() = "http://localhost:${server.address.port}"
    val users = ConcurrentHashMap<Int, UserResponse>()
    val menu = ConcurrentHashMap<Int, MenuItemResponse>()
    val userRequests = AtomicInteger()
    val userFailureStatus = AtomicInteger()
    @Volatile var recommendations: List<MenuItemResponse> = emptyList()
    @Volatile var requestedTastes: Set<String> = emptySet()
    private val nextUserId = AtomicInteger()
    private val nextMenuId = AtomicInteger()
    private val tags = listOf(TasteTagResponse(1, "SWEET"), TasteTagResponse(2, "SOUR"), TasteTagResponse(3, "BITTER"))

    fun reset() {
        users.clear()
        menu.clear()
        nextUserId.set(0)
        nextMenuId.set(0)
        userRequests.set(0)
        userFailureStatus.set(0)
        recommendations = emptyList()
        requestedTastes = emptySet()
    }

    fun createUser(role: UserRole, email: String): Int {
        val id = nextUserId.incrementAndGet()
        users[id] = UserResponse(id, role.name, email, role)
        return id
    }

    fun createMenu(name: String, price: java.math.BigDecimal, tagIds: Set<Int>): Int {
        val id = nextMenuId.incrementAndGet()
        menu[id] = MenuItemResponse(id, name, price, "Gin, lime", tags.filter { it.idTag in tagIds }.map { it.name }.sorted())
        return id
    }

    private fun handle(exchange: HttpExchange) {
        exchange.use {
            val path = exchange.requestURI.path
            val id = path.substringAfterLast('/').toIntOrNull() ?: -1
            when {
                path.startsWith("/api/users/") && exchange.requestMethod == "GET" -> {
                    userRequests.incrementAndGet()
                    val failure = userFailureStatus.get()
                    if (failure > 0) reply(exchange, failure, mapOf("message" to "User service failed"))
                    else users[id]?.let { reply(exchange, 200, it) }
                        ?: reply(exchange, 404, mapOf("message" to "User $id not found"))
                }
                path.startsWith("/internal/users/") && exchange.requestMethod == "DELETE" -> {
                    if (users.remove(id) != null) reply(exchange, 204) else reply(exchange, 404, mapOf("message" to "User $id not found"))
                }
                path.startsWith("/api/menu/") -> menu[id]?.let { reply(exchange, 200, it) }
                    ?: reply(exchange, 404, mapOf("message" to "Cocktail $id not found"))
                path == "/internal/menu/tags" -> {
                    val ids = query(exchange, "ids").map(String::toInt).toSet()
                    val selected = tags.filter { it.idTag in ids }
                    if (selected.size == ids.size) reply(exchange, 200, selected)
                    else reply(exchange, 400, "Some specified taste tags were not found")
                }
                path == "/internal/menu/recommendations" -> {
                    requestedTastes = query(exchange, "tastes").toSet()
                    reply(exchange, 200, recommendations)
                }
                path.startsWith("/internal/menu/") && exchange.requestMethod == "DELETE" -> {
                    if (menu.remove(id) != null) reply(exchange, 204) else reply(exchange, 404, mapOf("message" to "Cocktail $id not found"))
                }
                else -> reply(exchange, 404, "Unknown test endpoint")
            }
        }
    }

    private fun query(exchange: HttpExchange, name: String): List<String> =
        exchange.requestURI.rawQuery.orEmpty().split('&').filter { it.substringBefore('=') == name }
            .flatMap { URLDecoder.decode(it.substringAfter('='), StandardCharsets.UTF_8).split(',') }

    private fun reply(exchange: HttpExchange, status: Int, body: Any? = null) {
        if (body == null) {
            exchange.sendResponseHeaders(status, -1)
            return
        }
        val bytes = if (body is String) body.toByteArray(StandardCharsets.UTF_8) else json.writeValueAsBytes(body)
        exchange.responseHeaders.set("Content-Type", if (body is String) "text/plain" else "application/json")
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.write(bytes)
    }

    override fun close() {
        server.stop(0)
        executor.shutdownNow()
    }
}
