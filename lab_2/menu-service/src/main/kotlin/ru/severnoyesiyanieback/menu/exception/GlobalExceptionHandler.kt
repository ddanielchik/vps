package ru.severnoyesiyanieback.menu.exception

import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.http.server.reactive.ServerHttpResponse
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Mono
import ru.severnoyesiyanieback.menu.dto.ErrorResponse

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(MenuItemNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNotFound(exception: MenuItemNotFoundException): Mono<ErrorResponse> =
        Mono.just(ErrorResponse(exception.message ?: "Cocktail not found"))

    @ExceptionHandler(TasteTagsNotFoundException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleMissingTags(exception: TasteTagsNotFoundException): Mono<String> =
        Mono.just(exception.message ?: "Some specified taste tags were not found")

    @ExceptionHandler(WebExchangeBindException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidation(exception: WebExchangeBindException): Mono<ErrorResponse> =
        Mono.just(ErrorResponse(exception.fieldErrors.firstOrNull()?.defaultMessage ?: "Validation error"))

    @ExceptionHandler(ConstraintViolationException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleEntityValidation(exception: ConstraintViolationException): Mono<ErrorResponse> =
        Mono.just(ErrorResponse(exception.constraintViolations.firstOrNull()?.message ?: "Validation error"))

    @ExceptionHandler(ResponseStatusException::class)
    fun handleStatus(exception: ResponseStatusException, response: ServerHttpResponse): Mono<ErrorResponse> {
        response.statusCode = exception.statusCode
        return Mono.just(ErrorResponse(exception.reason ?: "Invalid request"))
    }
}
