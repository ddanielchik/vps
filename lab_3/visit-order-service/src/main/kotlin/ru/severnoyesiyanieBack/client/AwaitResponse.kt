package ru.severnoyesiyanieBack.client

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono

// MVC services wait on the request thread so that their JPA transaction stays on that thread.
internal fun <T : Any> Mono<T>.awaitResponse(): T = block()
    ?: throw ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "A required service returned an empty response")
