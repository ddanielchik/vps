package ru.severnoyesiyanieBack.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import ru.severnoyesiyanieBack.dto.CreateUserRequest
import ru.severnoyesiyanieBack.dto.UpdateUserRequest
import ru.severnoyesiyanieBack.dto.UserResponse
import ru.severnoyesiyanieBack.entity.User
import ru.severnoyesiyanieBack.entity.UserRole
import ru.severnoyesiyanieBack.exception.UserAlreadyExistsException
import ru.severnoyesiyanieBack.exception.UserNotFoundException
import ru.severnoyesiyanieBack.repository.UserRepository
import java.util.Optional

class UserServiceTest {
    private val savedUsers = mutableListOf<User>()
    private val userRepository = mock(UserRepository::class.java) { invocation ->
        if (invocation.method.name == "save") {
            invocation.getArgument<User>(0).also { user ->
                if (user.idUser == null) user.idUser = 42
                savedUsers += user
            }
        } else {
            Answers.RETURNS_DEFAULTS.answer(invocation)
        }
    }
    private val service = UserService(userRepository)

    @Test
    fun `create user assigns client role and returns persisted identity`() {
        val request = CreateUserRequest("Anna", "anna@example.com")
        doReturn(false).`when`(userRepository).existsByEmail(request.email)

        val response = service.createUser(request)

        assertEquals(UserResponse(42, request.name, request.email, UserRole.CLIENT), response)
        val savedUser = savedUsers.single()
        assertEquals(request.name, savedUser.name)
        assertEquals(request.email, savedUser.email)
        assertEquals(UserRole.CLIENT, savedUser.role)
        verify(userRepository).existsByEmail(request.email)
        verify(userRepository).save(savedUser)
        verifyNoMoreInteractions(userRepository)
    }

    @Test
    fun `create user rejects an existing email without saving`() {
        val request = CreateUserRequest("Anna", "taken@example.com")
        doReturn(true).`when`(userRepository).existsByEmail(request.email)

        val exception = assertThrows(UserAlreadyExistsException::class.java) {
            service.createUser(request)
        }

        assertTrue(exception.message.orEmpty().contains(request.email))
        assertTrue(savedUsers.isEmpty())
        verify(userRepository).existsByEmail(request.email)
        verifyNoMoreInteractions(userRepository)
    }

    @Test
    fun `get user maps all fields including employee role`() {
        val user = user(role = UserRole.WAITER)
        doReturn(Optional.of(user)).`when`(userRepository).findById(7)

        assertEquals(
            UserResponse(7, user.name, user.email, UserRole.WAITER),
            service.getUserById(7)
        )
    }

    @Test
    fun `get missing user reports the requested identity`() {
        doReturn(Optional.empty<User>()).`when`(userRepository).findById(404)

        val exception = assertThrows(UserNotFoundException::class.java) {
            service.getUserById(404)
        }

        assertTrue(exception.message.orEmpty().contains("404"))
    }

    @Test
    fun `update user changes name and email but preserves identity and role`() {
        val user = user(role = UserRole.WORKER)
        val request = UpdateUserRequest("New name", "new@example.com")
        doReturn(Optional.of(user)).`when`(userRepository).findById(7)
        doReturn(false).`when`(userRepository).existsByEmail(request.email)

        val response = service.updateUser(7, request)

        assertEquals(UserResponse(7, request.name, request.email, UserRole.WORKER), response)
        assertSame(user, savedUsers.single())
        assertEquals(request.name, user.name)
        assertEquals(request.email, user.email)
        verify(userRepository).findById(7)
        verify(userRepository).existsByEmail(request.email)
        verify(userRepository).save(user)
        verifyNoMoreInteractions(userRepository)
    }

    @Test
    fun `update with unchanged email skips the duplicate check`() {
        val user = user()
        val request = UpdateUserRequest("Renamed", user.email)
        doReturn(Optional.of(user)).`when`(userRepository).findById(7)

        val response = service.updateUser(7, request)

        assertEquals(request.name, response.name)
        assertEquals(request.email, response.email)
        verify(userRepository).findById(7)
        verify(userRepository).save(user)
        verifyNoMoreInteractions(userRepository)
    }

    @Test
    fun `update rejects another users email before mutating the user`() {
        val user = user()
        val originalName = user.name
        val originalEmail = user.email
        val request = UpdateUserRequest("Changed", "taken@example.com")
        doReturn(Optional.of(user)).`when`(userRepository).findById(7)
        doReturn(true).`when`(userRepository).existsByEmail(request.email)

        assertThrows(UserAlreadyExistsException::class.java) {
            service.updateUser(7, request)
        }

        assertEquals(originalName, user.name)
        assertEquals(originalEmail, user.email)
        assertTrue(savedUsers.isEmpty())
        verify(userRepository).findById(7)
        verify(userRepository).existsByEmail(request.email)
        verifyNoMoreInteractions(userRepository)
    }

    @Test
    fun `update missing user performs no duplicate check or save`() {
        doReturn(Optional.empty<User>()).`when`(userRepository).findById(404)

        assertThrows(UserNotFoundException::class.java) {
            service.updateUser(404, UpdateUserRequest("Anna", "anna@example.com"))
        }

        verify(userRepository).findById(404)
        verifyNoMoreInteractions(userRepository)
    }

    @Test
    fun `delete user deletes the loaded entity`() {
        val user = user()
        doReturn(Optional.of(user)).`when`(userRepository).findById(7)

        service.deleteUser(7)

        verify(userRepository).findById(7)
        verify(userRepository).delete(user)
        verifyNoMoreInteractions(userRepository)
    }

    @Test
    fun `delete missing user does not call delete`() {
        doReturn(Optional.empty<User>()).`when`(userRepository).findById(404)

        assertThrows(UserNotFoundException::class.java) { service.deleteUser(404) }

        verify(userRepository).findById(404)
        verifyNoMoreInteractions(userRepository)
    }

    private fun user(role: UserRole = UserRole.CLIENT) = User(
        idUser = 7,
        name = "Anna",
        email = "anna@example.com",
        role = role
    )
}
