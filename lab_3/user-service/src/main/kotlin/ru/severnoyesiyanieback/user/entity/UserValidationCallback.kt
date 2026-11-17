package ru.severnoyesiyanieback.user.entity

import jakarta.validation.ConstraintViolationException
import jakarta.validation.Validator
import org.reactivestreams.Publisher
import org.springframework.data.r2dbc.mapping.event.BeforeConvertCallback
import org.springframework.data.relational.core.sql.SqlIdentifier
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class UserValidationCallback(private val validator: Validator) : BeforeConvertCallback<User> {
    override fun onBeforeConvert(entity: User, table: SqlIdentifier): Publisher<User> = Mono.fromCallable {
        val violations = validator.validate(entity)
        if (violations.isNotEmpty()) {
            throw ConstraintViolationException(violations)
        }
        entity
    }
}
