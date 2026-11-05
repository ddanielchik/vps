package ru.severnoyesiyanieback.user

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.springframework.dao.DuplicateKeyException
import org.springframework.transaction.ReactiveTransaction
import org.springframework.transaction.ReactiveTransactionManager
import org.springframework.transaction.TransactionDefinition
import reactor.core.publisher.Mono
import reactor.test.StepVerifier
import ru.severnoyesiyanieback.user.dto.CreateUserRequest
import ru.severnoyesiyanieback.user.dto.UpdateUserRequest
import ru.severnoyesiyanieback.user.dto.UserResponse
import ru.severnoyesiyanieback.user.entity.User
import ru.severnoyesiyanieback.user.entity.UserRole
import ru.severnoyesiyanieback.user.exception.UserAlreadyExistsException
import ru.severnoyesiyanieback.user.exception.UserNotFoundException
import ru.severnoyesiyanieback.user.repository.UserRepository
import ru.severnoyesiyanieback.user.service.UserService

class UserServiceTest {
    private val repository = mock(UserRepository::class.java)
    private val transactionManager = mock(ReactiveTransactionManager::class.java)
    private val transaction = mock(ReactiveTransaction::class.java)
    private val service: UserService

    init {
        doReturn(Mono.just(transaction)).`when`(transactionManager)
            .getReactiveTransaction(any(TransactionDefinition::class.java))
        doReturn(Mono.empty<Void>()).`when`(transactionManager).commit(transaction)
        doReturn(Mono.empty<Void>()).`when`(transactionManager).rollback(transaction)
        service = UserService(repository, transactionManager)
    }

    @Test
    fun `create is deferred and assigns the client role`() {
        val newUser = User(name = "Anna", email = "anna@example.com")
        doReturn(Mono.just(newUser.copy(idUser = 42))).`when`(repository).save(newUser)

        val result = service.createUser(CreateUserRequest(newUser.name, newUser.email))
        verifyNoInteractions(repository)
        StepVerifier.create(result)
            .expectNext(UserResponse(42, newUser.name, newUser.email, UserRole.CLIENT))
            .verifyComplete()
    }

    @Test
    fun `unique constraint failure becomes an email conflict`() {
        val newUser = User(name = "Anna", email = "taken@example.com")
        doReturn(Mono.error<User>(DuplicateKeyException("users_email_key"))).`when`(repository).save(newUser)
        StepVerifier.create(service.createUser(CreateUserRequest(newUser.name, newUser.email)))
            .expectErrorMatches { it is UserAlreadyExistsException && it.message!!.contains(newUser.email) }
            .verify()
    }

    @Test
    fun `get maps employee roles and stays deferred`() {
        val user = user(role = UserRole.WAITER)
        doReturn(Mono.just(user)).`when`(repository).findById(7)
        val result = service.getUserById(7)
        verifyNoInteractions(repository)
        StepVerifier.create(result)
            .expectNext(UserResponse(7, user.name, user.email, UserRole.WAITER))
            .verifyComplete()
    }

    @Test
    fun `missing get returns the requested identity`() {
        doReturn(Mono.empty<User>()).`when`(repository).findById(404)
        StepVerifier.create(service.getUserById(404))
            .expectErrorMatches { it is UserNotFoundException && it.message!!.contains("404") }.verify()
    }

    @Test
    fun `update preserves the identity and role and commits`() {
        val original = user(role = UserRole.WORKER)
        val changed = original.copy(name = "New name", email = "new@example.com")
        doReturn(Mono.just(original)).`when`(repository).findById(7)
        doReturn(Mono.just(changed)).`when`(repository).save(changed)
        StepVerifier.create(service.updateUser(7, UpdateUserRequest(changed.name, changed.email)))
            .expectNext(UserResponse(7, changed.name, changed.email, UserRole.WORKER)).verifyComplete()
        assertEquals("Anna", original.name)
        verify(transactionManager).commit(transaction)
    }

    @Test
    fun `update with unchanged email succeeds`() {
        val original = user()
        val changed = original.copy(name = "Renamed")
        doReturn(Mono.just(original)).`when`(repository).findById(7)
        doReturn(Mono.just(changed)).`when`(repository).save(changed)
        StepVerifier.create(service.updateUser(7, UpdateUserRequest(changed.name, changed.email)))
            .expectNext(UserResponse(7, changed.name, changed.email, UserRole.CLIENT)).verifyComplete()
    }

    @Test
    fun `duplicate email on update rolls back and keeps original immutable`() {
        val original = user()
        val changed = original.copy(name = "Changed", email = "taken@example.com")
        doReturn(Mono.just(original)).`when`(repository).findById(7)
        doReturn(Mono.error<User>(DuplicateKeyException("users_email_key"))).`when`(repository).save(changed)
        StepVerifier.create(service.updateUser(7, UpdateUserRequest(changed.name, changed.email)))
            .expectError(UserAlreadyExistsException::class.java).verify()
        assertEquals("anna@example.com", original.email)
        verify(transactionManager).rollback(transaction)
    }

    @Test
    fun `missing update rolls back`() {
        doReturn(Mono.empty<User>()).`when`(repository).findById(404)
        StepVerifier.create(service.updateUser(404, UpdateUserRequest("Anna", "anna@example.com")))
            .expectError(UserNotFoundException::class.java).verify()
        verify(transactionManager).rollback(transaction)
    }

    @Test
    fun `delete loads and deletes the entity and commits`() {
        val user = user()
        doReturn(Mono.just(user)).`when`(repository).findById(7)
        doReturn(Mono.empty<Void>()).`when`(repository).delete(user)
        StepVerifier.create(service.deleteUser(7)).verifyComplete()
        verify(repository).delete(user)
        verify(transactionManager).commit(transaction)
    }

    @Test
    fun `missing delete rolls back`() {
        doReturn(Mono.empty<User>()).`when`(repository).findById(404)
        StepVerifier.create(service.deleteUser(404)).expectError(UserNotFoundException::class.java).verify()
        verify(transactionManager).rollback(transaction)
    }

    private fun user(role: UserRole = UserRole.CLIENT) =
        User(idUser = 7, name = "Anna", email = "anna@example.com", role = role)
}
