package ru.severnoyesiyanieback.user.service

import org.springframework.dao.DuplicateKeyException
import ru.severnoyesiyanieback.user.client.VisitOrderClient
import feign.FeignException
import org.springframework.stereotype.Service
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.reactive.TransactionalOperator
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import reactor.core.scheduler.Schedulers
import reactor.core.publisher.Flux
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
    private val visitOrderClient: VisitOrderClient,
    transactionManager: ReactiveTransactionManager
) {
    private val transactions = TransactionalOperator.create(transactionManager)

    fun createUser(request: CreateUserRequest): Mono<UserResponse> = Mono.defer {
        userRepository.save(User(name = request.name, email = request.email))
    }.map(::toResponse)
        .onErrorMap(DuplicateKeyException::class.java) { UserAlreadyExistsException(request.email) }

    fun getAllUsers(): Flux<UserResponse> = Flux.defer { userRepository.findAll() }.map(::toResponse)

    fun getUserById(userId: Int): Mono<UserResponse> = findUser(userId).map(::toResponse)

    fun updateUser(userId: Int, request: UpdateUserRequest): Mono<UserResponse> =
        transactions.transactional(
            findUser(userId).flatMap { user ->
                userRepository.save(user.copy(name = request.name, email = request.email, status = request.status ?: user.status))
            }.map(::toResponse)
        ).onErrorMap(DuplicateKeyException::class.java) { UserAlreadyExistsException(request.email) }

    fun deleteUser(userId: Int): Mono<Void> = findUser(userId).flatMap { user ->
        Mono.fromCallable { visitOrderClient.isUserInUse(userId) }
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorMap(FeignException::class.java) {
                ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Cannot check user references", it)
            }
            .flatMap { inUse ->
                if (inUse) Mono.error(ResponseStatusException(HttpStatus.CONFLICT, "User is referenced by a visit or order"))
                else transactions.transactional(userRepository.delete(user))
            }
    }

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
