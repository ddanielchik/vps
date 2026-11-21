package ru.severnoyesiyanieBack.controller

import io.swagger.v3.oas.annotations.security.SecurityRequirement
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import ru.severnoyesiyanieBack.client.UserClient
import ru.severnoyesiyanieBack.client.getActiveUser
import ru.severnoyesiyanieBack.dto.CreateVisitRequest
import ru.severnoyesiyanieBack.dto.MenuItemResponse
import ru.severnoyesiyanieBack.dto.UpdateVisitRequest
import ru.severnoyesiyanieBack.dto.VisitPreferencesRequest
import ru.severnoyesiyanieBack.dto.VisitResponse
import ru.severnoyesiyanieBack.entity.UserRole
import ru.severnoyesiyanieBack.security.currentUserId
import ru.severnoyesiyanieBack.service.VisitService

@RestController
@RequestMapping("/api/v1/visits")
@SecurityRequirement(name = "bearerAuth")
class VisitController(private val visitService: VisitService, private val users: UserClient) {
    @GetMapping("/{id}")
    fun getVisitById(@PathVariable id: Int, @AuthenticationPrincipal jwt: Jwt): VisitResponse =
        visitService.getVisitById(id, clientFilter(jwt))

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun openVisit(
        @Valid @RequestBody request: CreateVisitRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): VisitResponse {
        val currentUser = users.getActiveUser(jwt.currentUserId())
        val isOwnVisit = currentUser.role == UserRole.CLIENT && currentUser.idUser == request.clientId
        if (currentUser.role != UserRole.WAITER && !isOwnVisit) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only the client or a waiter can open this visit")
        }
        return visitService.openVisit(request)
    }

    @GetMapping
    fun getAllVisits(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
        @AuthenticationPrincipal jwt: Jwt
    ): ResponseEntity<List<VisitResponse>> {
        val visits = visitService.getAllVisits(page, size, clientFilter(jwt))
        return ResponseEntity.ok().header("X-Total-Count", visits.totalElements.toString()).body(visits.content)
    }

    @PutMapping("/{id}")
    fun updateTableNumber(
        @PathVariable id: Int,
        @Valid @RequestBody request: UpdateVisitRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): VisitResponse {
        requireWaiter(jwt)
        return visitService.updateVisit(id, request)
    }

    @PutMapping("/{id}/preferences")
    fun updateTastePreferences(
        @PathVariable id: Int,
        @Valid @RequestBody request: VisitPreferencesRequest,
        @AuthenticationPrincipal jwt: Jwt
    ): VisitResponse {
        val currentUser = users.getActiveUser(jwt.currentUserId())
        if (currentUser.role != UserRole.CLIENT && currentUser.role != UserRole.WAITER) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only clients and waiters can change preferences")
        }
        val clientId = currentUser.idUser.takeIf { currentUser.role == UserRole.CLIENT }
        return visitService.updateTastePreferences(id, request, clientId)
    }

    @PostMapping("/{id}/close")
    fun closeVisit(@PathVariable id: Int, @AuthenticationPrincipal jwt: Jwt): VisitResponse {
        requireWaiter(jwt)
        return visitService.closeVisit(id)
    }

    @GetMapping("/{id}/recommendations")
    fun getRecommendations(@PathVariable id: Int, @AuthenticationPrincipal jwt: Jwt): List<MenuItemResponse> =
        visitService.getRecommendations(id, clientFilter(jwt))

    private fun clientFilter(jwt: Jwt): Int? {
        val currentUser = users.getActiveUser(jwt.currentUserId())
        return currentUser.idUser.takeIf { currentUser.role == UserRole.CLIENT }
    }

    private fun requireWaiter(jwt: Jwt) {
        val currentUser = users.getActiveUser(jwt.currentUserId())
        if (currentUser.role != UserRole.WAITER) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "This action requires the waiter role")
        }
    }
}
