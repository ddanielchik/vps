package ru.severnoyesiyanieBack.exception

class UserNotFoundException(idUser:Int) : RuntimeException("User with id $idUser does not exist") {
}