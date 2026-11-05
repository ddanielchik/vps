package ru.severnoyesiyanieBack.controller

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import ru.severnoyesiyanieBack.service.ReferenceDeletionService

@RestController
class ReferenceDeletionController(private val service: ReferenceDeletionService) {
    @DeleteMapping("/api/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteUser(@PathVariable userId: Int) = service.deleteUser(userId)

    @DeleteMapping("/api/menu/{idPosition}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteMenuItem(@PathVariable idPosition: Int) = service.deleteMenuItem(idPosition)
}
