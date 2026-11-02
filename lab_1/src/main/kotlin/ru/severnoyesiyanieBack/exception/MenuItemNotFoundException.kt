package ru.severnoyesiyanieBack.exception

class MenuItemNotFoundException(idPosition: Int) :
    RuntimeException("Cocktail with id $idPosition not found")