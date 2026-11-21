package ru.severnoyesiyanieback.user.service

import org.springframework.dao.DuplicateKeyException
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.reactive.TransactionalOperator
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import ru.severnoyesiyanieback.user.dto.CreateUserRequest
import ru.severnoyesiyanieback.user.dto.UpdateUserRequest
import ru.severnoyesiyanieback.user.dto.UserResponse
import ru.severnoyesiyanieback.user.entity.User
import ru.severnoyesiyanieback.user.entity.UserStatus
import ru.severnoyesiyanieback.user.exception.UserAlreadyExistsException
import ru.severnoyesiyanieback.user.exception.UserNotFoundException
import ru.severnoyesiyanieback.user.repository.UserRepository

@Service
class UserService(
    private val userRepository: UserRepository,
    transactionManager: ReactiveTransactionManager,
    private val passwordEncoder: PasswordEncoder
) {
    private val transactions = TransactionalOperator.create(transactionManager)

    fun createUser(request: CreateUserRequest): Mono<UserResponse> =
        Mono.fromCallable { passwordEncoder.encode(request.password) }
            .subscribeOn(Schedulers.boundedElastic())
            .flatMap { passwordHash ->
                userRepository.save(User(
                    name = request.name,
                    email = request.email,
                    role = request.role,
                    passwordHash = passwordHash
                ))
            }
            .map(::toResponse)
            .onErrorMap(DuplicateKeyException::class.java) { UserAlreadyExistsException(request.email) }

    fun getAllUsers(): Flux<UserResponse> = Flux.defer { userRepository.findAll() }.map(::toResponse)

    fun getUserById(userId: Int): Mono<UserResponse> = findUser(userId).map(::toResponse)

    fun updateUser(userId: Int, request: UpdateUserRequest): Mono<UserResponse> =
        transactions.transactional(
            findUserForUpdate(userId).flatMap { user ->
                userRepository.save(user.copy(name = request.name, email = request.email, status = request.status ?: user.status))
            }.map(::toResponse)
        ).onErrorMap(DuplicateKeyException::class.java) { UserAlreadyExistsException(request.email) }

    fun deleteUser(userId: Int): Mono<Void> = transactions.transactional(
        findUserForUpdate(userId)
            .filter { it.status != UserStatus.BLOCKED }
            .flatMap { user -> userRepository.save(user.copy(status = UserStatus.BLOCKED)) }
    ).then()

    private fun findUserForUpdate(userId: Int): Mono<User> = Mono.defer { userRepository.findByIdForUpdate(userId) }
        .switchIfEmpty(Mono.error(UserNotFoundException(userId)))

    private fun findUser(userId: Int): Mono<User> = Mono.defer { userRepository.findById(userId) }
        .switchIfEmpty(Mono.error(UserNotFoundException(userId)))

    private fun toResponse(user: User) = UserResponse.Builder()
        .idUser(requireNotNull(user.idUser))
        .name(user.name)
        .email(user.email)
        .role(user.role)
        .status(user.status)
        .build()
}
