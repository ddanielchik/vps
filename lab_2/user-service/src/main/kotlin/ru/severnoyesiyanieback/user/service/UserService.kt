package ru.severnoyesiyanieback.user.service

import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Mono
import ru.severnoyesiyanieback.user.dto.CreateUserRequest
import ru.severnoyesiyanieback.user.dto.UpdateUserRequest
import ru.severnoyesiyanieback.user.dto.UserResponse
import ru.severnoyesiyanieback.user.entity.User
import ru.severnoyesiyanieback.user.exception.UserAlreadyExistsException
import ru.severnoyesiyanieback.user.exception.UserNotFoundException
import ru.severnoyesiyanieback.user.repository.UserRepository

@Service
class UserService(
    private val userRepository: UserRepository,
    transactionManager: ReactiveTransactionManager
) {
    private val transactions = TransactionalOperator.create(transactionManager)

    fun createUser(request: CreateUserRequest): Mono<UserResponse> = Mono.defer {
        userRepository.save(User(name = request.name, email = request.email))
    }.map(::toResponse)
        .onErrorMap(DuplicateKeyException::class.java) { UserAlreadyExistsException(request.email) }

    fun getUserById(userId: Int): Mono<UserResponse> = findUser(userId).map(::toResponse)

    fun updateUser(userId: Int, request: UpdateUserRequest): Mono<UserResponse> =
        transactions.transactional(
            findUser(userId).flatMap { user ->
                userRepository.save(user.copy(name = request.name, email = request.email))
            }.map(::toResponse)
        ).onErrorMap(DuplicateKeyException::class.java) { UserAlreadyExistsException(request.email) }

    fun deleteUser(userId: Int): Mono<Void> = transactions.transactional(
        findUser(userId).flatMap(userRepository::delete)
    )

    private fun findUser(userId: Int): Mono<User> = Mono.defer { userRepository.findById(userId) }
        .switchIfEmpty(Mono.error(UserNotFoundException(userId)))

    private fun toResponse(user: User) = UserResponse(
        idUser = requireNotNull(user.idUser),
        name = user.name,
        email = user.email,
        role = user.role
    )
}
