package ru.severnoyesiyanieBack.service

import org.springframework.transaction.annotation.Transactional
import ru.severnoyesiyanieBack.dto.UpdateUserRequest
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.PathVariable
import ru.severnoyesiyanieBack.dto.CreateUserRequest

import ru.severnoyesiyanieBack.dto.UserResponse
import ru.severnoyesiyanieBack.entity.User
import ru.severnoyesiyanieBack.entity.UserRole
import ru.severnoyesiyanieBack.exception.UserAlreadyExistsException
import ru.severnoyesiyanieBack.exception.UserNotFoundException
import ru.severnoyesiyanieBack.repository.UserRepository

@Service
class UserService(
    private val userRepository: UserRepository
) {
    fun createUser(createUserRequest: CreateUserRequest): UserResponse {
        if (userRepository.existsByEmail(createUserRequest.email)) {
            throw UserAlreadyExistsException(createUserRequest.email)
        }
        val user = User(
            name = createUserRequest.name,
            email = createUserRequest.email,
            role = UserRole.CLIENT,
        )
        val savedUser = userRepository.save(user)
        return UserResponse(
            idUser = requireNotNull(savedUser.idUser),
            name = savedUser.name,
            email = savedUser.email,
            role = savedUser.role,
        )
    }

    fun getUserById(userId: Int): UserResponse {
        val user = userRepository.findById(userId).orElseThrow { UserNotFoundException(userId) }
        return UserResponse(
            idUser = requireNotNull(user.idUser),
            name = user.name,
            email = user.email,
            role = user.role
        )
    }

    @Transactional
    fun updateUser(userId: Int, request: UpdateUserRequest): UserResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { UserNotFoundException(userId) }

        if (user.email != request.email &&
            userRepository.existsByEmail(request.email)
        ) {
            throw UserAlreadyExistsException(request.email)
        }

        user.name = request.name
        user.email = request.email

        val updatedUser = userRepository.save(user)

        return UserResponse(
            idUser = requireNotNull(updatedUser.idUser),
            name = updatedUser.name,
            email = updatedUser.email,
            role = updatedUser.role
        )
    }

    @Transactional
    fun deleteUser(userId: Int) {
        val user = userRepository.findById(userId).orElseThrow { UserNotFoundException(userId) }
        userRepository.delete(user)
    }

    fun getUserEntityById(userId: Int): User {
        return userRepository.findById(userId)
            .orElseThrow { UserNotFoundException(userId) }
    }
}
