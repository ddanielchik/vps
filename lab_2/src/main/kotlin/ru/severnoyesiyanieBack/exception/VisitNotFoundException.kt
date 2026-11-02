package ru.severnoyesiyanieBack.exception

class VisitNotFoundException(id: Int) :
    RuntimeException("Посещение с ID $id не найдено")