package ru.severnoyesiyanieBack.exception

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import ru.severnoyesiyanieBack.dto.ErrorResponse

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(UserNotFoundException::class)
    fun handleUserNotFoundException(exception: UserNotFoundException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
        .status(HttpStatus.NOT_FOUND)
        .body(ErrorResponse(message = exception.message ?: "User not found" ))
    }

    @ExceptionHandler(UserAlreadyExistsException::class)
    fun handleUserAlreadyExistsException(exception: UserAlreadyExistsException): ResponseEntity<ErrorResponse> {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
        .body(ErrorResponse(message = exception.message ?: "User already exists"))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(exception: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = exception.bindingResult.fieldErrors.firstOrNull()?.defaultMessage?:"Validation error"

        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
        .body(ErrorResponse(message = message))
    }
    @ExceptionHandler(MenuItemNotFoundException::class)
    fun handleMenuItemNotFound(
        ex: MenuItemNotFoundException
    ): ResponseEntity<ErrorResponse> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ex.message ?: "Cocktail not found"))
    }

    @ExceptionHandler(VisitNotFoundException::class)
    fun handleVisitNotFound(ex: VisitNotFoundException): ResponseEntity<String> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.message)
    }

    @ExceptionHandler(VisitAlreadyClosedException::class)
    fun handleVisitAlreadyClosed(ex: VisitAlreadyClosedException): ResponseEntity<String> {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ex.message ?: "The visit has been already closed")
    }
    @ExceptionHandler(TasteTagsNotFoundException::class)
    fun handleTasteTagsNotFound(ex: TasteTagsNotFoundException): ResponseEntity<String> {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ex.message ?: "Some specified taste tags were not found")
    }
    @ExceptionHandler(OrderNotFoundException::class)
    fun handleOrderNotFound(ex: OrderNotFoundException): ResponseEntity<String> {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ex.message ?: "Order not found")
    }

    @ExceptionHandler(InvalidUserRoleException::class)
    fun handleInvalidUserRole(ex: InvalidUserRoleException): ResponseEntity<String> {
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ex.message ?: "Invalid user role")
    }
}