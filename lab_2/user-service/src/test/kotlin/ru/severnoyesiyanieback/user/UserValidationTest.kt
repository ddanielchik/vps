package ru.severnoyesiyanieback.user

import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validation
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.data.relational.core.sql.SqlIdentifier
import reactor.test.StepVerifier
import ru.severnoyesiyanieback.user.dto.CreateUserRequest
import ru.severnoyesiyanieback.user.dto.UpdateUserRequest
import ru.severnoyesiyanieback.user.entity.User
import ru.severnoyesiyanieback.user.entity.UserValidationCallback

class UserValidationTest {
    @Test
    fun `callback validates entities at subscription time`() {
        Validation.buildDefaultValidatorFactory().use { factory ->
            val callback = UserValidationCallback(factory.validator)
            val valid = User(name = "Anna", email = "anna@example.com")
            StepVerifier.create(callback.onBeforeConvert(valid, SqlIdentifier.unquoted("users")))
                .expectNext(valid).verifyComplete()
            StepVerifier.create(callback.onBeforeConvert(valid.copy(email = "broken"), SqlIdentifier.unquoted("users")))
                .expectError(ConstraintViolationException::class.java).verify()
        }
    }

    @Test
    fun `requests and entities retain the original length and blank constraints`() {
        Validation.buildDefaultValidatorFactory().use { factory ->
            val invalid = listOf(
                CreateUserRequest("", "invalid"),
                UpdateUserRequest("x".repeat(51), "x".repeat(101)),
                User(name = " ", email = " ")
            )
            invalid.forEach { value ->
                assertEquals(setOf("name", "email"), factory.validator.validate(value).map { it.propertyPath.toString() }.toSet())
            }
        }
    }
}
