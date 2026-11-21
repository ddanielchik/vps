package ru.severnoyesiyanieback.user.exception

class UserNotFoundException(userId: Int) : RuntimeException("User with id $userId does not exist")
