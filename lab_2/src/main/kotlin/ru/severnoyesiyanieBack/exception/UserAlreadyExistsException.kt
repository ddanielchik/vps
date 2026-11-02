package ru.severnoyesiyanieBack.exception

class UserAlreadyExistsException(email: String) : RuntimeException("User with email $email already exists") {
}