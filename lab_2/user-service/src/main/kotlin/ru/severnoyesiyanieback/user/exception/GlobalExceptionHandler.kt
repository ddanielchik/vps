package ru.severnoyesiyanieback.user.exception

import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.bind.support.WebExchangeBindException
import org.springframework.web.server.ServerWebInputException
import reactor.core.publisher.Mono
import ru.severnoyesiyanieback.user.dto.ErrorResponse

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(UserNotFoundException::class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    fun handleNotFound(exception: UserNotFoundException): Mono<ErrorResponse> =
        Mono.just(ErrorResponse(requireNotNull(exception.message)))

    @ExceptionHandler(UserAlreadyExistsException::class)
    @ResponseStatus(HttpStatus.CONFLICT)
    fun handleConflict(exception: UserAlreadyExistsException): Mono<ErrorResponse> =
        Mono.just(ErrorResponse(requireNotNull(exception.message)))

    @ExceptionHandler(WebExchangeBindException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleValidation(exception: WebExchangeBindException): Mono<ErrorResponse> =
        Mono.just(ErrorResponse(exception.fieldErrors.firstOrNull()?.defaultMessage ?: "Validation error"))

    @ExceptionHandler(ConstraintViolationException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleEntityValidation(exception: ConstraintViolationException): Mono<ErrorResponse> =
        Mono.just(ErrorResponse(exception.constraintViolations.firstOrNull()?.message ?: "Validation error"))

    @ExceptionHandler(ServerWebInputException::class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    fun handleMalformedInput(exception: ServerWebInputException): Mono<ErrorResponse> =
        Mono.just(ErrorResponse(exception.reason ?: "Invalid request"))
}
