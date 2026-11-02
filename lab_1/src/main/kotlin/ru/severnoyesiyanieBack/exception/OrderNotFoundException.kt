package ru.severnoyesiyanieBack.exception

class OrderNotFoundException(id: Int) :
    RuntimeException("Order with ID $id not found")