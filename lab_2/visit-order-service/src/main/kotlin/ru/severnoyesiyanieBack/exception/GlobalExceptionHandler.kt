package ru.severnoyesiyanieBack.exception

import feign.FeignException
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.server.ResponseStatusException
import ru.severnoyesiyanieBack.client.RemoteBusinessException
import ru.severnoyesiyanieBack.dto.ErrorResponse

@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(exception: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> =
        ResponseEntity.badRequest().body(ErrorResponse(
            exception.bindingResult.fieldErrors.firstOrNull()?.defaultMessage ?: "Validation error"
        ))

    @ExceptionHandler(VisitNotFoundException::class, OrderNotFoundException::class)
    fun handleNotFound(exception: RuntimeException): ResponseEntity<String> =
        ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.message)

    @ExceptionHandler(VisitAlreadyClosedException::class, InvalidUserRoleException::class)
    fun handleConflict(exception: RuntimeException): ResponseEntity<String> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(exception.message)

    @ExceptionHandler(ResourceInUseException::class)
    fun handleResourceInUse(exception: ResourceInUseException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(exception.message ?: "Resource is in use"))

    @ExceptionHandler(TasteTagsNotFoundException::class)
    fun handleMissingTags(exception: TasteTagsNotFoundException): ResponseEntity<String> =
        ResponseEntity.badRequest().body(exception.message)

    @ExceptionHandler(ResponseStatusException::class)
    fun handleInvalidRequest(exception: ResponseStatusException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(exception.statusCode).body(ErrorResponse(exception.reason ?: "Invalid request"))

    @ExceptionHandler(RemoteBusinessException::class, NoFallbackAvailableException::class, FeignException::class)
    fun handleRemoteFailure(exception: Exception): ResponseEntity<*> {
        val businessError = generateSequence<Throwable>(exception) { it.cause }
            .filterIsInstance<RemoteBusinessException>().firstOrNull()
        if (businessError != null) {
            return ResponseEntity.status(businessError.status)
                .contentType(MediaType.parseMediaType(businessError.contentType ?: MediaType.TEXT_PLAIN_VALUE))
                .body(businessError.message)
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(ErrorResponse("A required service is temporarily unavailable"))
    }
}
