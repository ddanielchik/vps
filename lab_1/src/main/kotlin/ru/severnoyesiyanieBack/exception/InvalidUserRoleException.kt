package ru.severnoyesiyanieBack.exception

import ru.severnoyesiyanieBack.entity.UserRole

class InvalidUserRoleException(id: Int, expectedRole: UserRole) :
    RuntimeException("User with ID $id must have role $expectedRole")