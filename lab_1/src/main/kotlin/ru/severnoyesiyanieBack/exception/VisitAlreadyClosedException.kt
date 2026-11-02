package ru.severnoyesiyanieBack.exception

class VisitAlreadyClosedException(id: Int) :
    RuntimeException("The visit with ID $id is alredy closed")