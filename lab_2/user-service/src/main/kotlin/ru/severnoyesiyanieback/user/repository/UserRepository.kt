package ru.severnoyesiyanieback.user.repository

import org.springframework.data.r2dbc.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono
import ru.severnoyesiyanieback.user.entity.User

interface UserRepository : ReactiveCrudRepository<User, Int> {
    @Query("SELECT * FROM users WHERE id_user = :userId FOR UPDATE")
    fun findByIdForUpdate(@Param("userId") userId: Int): Mono<User>
}
