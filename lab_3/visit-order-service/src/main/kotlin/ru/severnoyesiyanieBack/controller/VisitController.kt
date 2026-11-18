package ru.severnoyesiyanieBack.controller

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import ru.severnoyesiyanieBack.dto.CreateVisitRequest
import ru.severnoyesiyanieBack.dto.MenuItemResponse
import ru.severnoyesiyanieBack.dto.UpdateVisitRequest
import ru.severnoyesiyanieBack.dto.VisitPreferencesRequest
import ru.severnoyesiyanieBack.dto.VisitResponse
import ru.severnoyesiyanieBack.service.VisitService

@RestController
@RequestMapping("/api/v1/visits")
class VisitController(private val visitService: VisitService) {
    @GetMapping("/{id}")
    fun getVisitById(@PathVariable id: Int): VisitResponse = visitService.getVisitById(id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun openVisit(@Valid @RequestBody request: CreateVisitRequest): VisitResponse =
        visitService.openVisit(request)

    @GetMapping
    fun getAllVisits(
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ResponseEntity<List<VisitResponse>> {

        val visits = visitService.getAllVisits(page, size)

        return ResponseEntity.ok()
            .header("X-Total-Count", visits.totalElements.toString())
            .body(visits.content)
    }

    @PutMapping("/{id}")
    fun updateTableNumber(
        @PathVariable id: Int,
        @Valid @RequestBody request: UpdateVisitRequest
    ): VisitResponse = visitService.updateVisit(id, request)

    @PutMapping("/{id}/preferences")
    fun updateTastePreferences(
        @PathVariable id: Int,
        @Valid @RequestBody request: VisitPreferencesRequest
    ): VisitResponse = visitService.updateTastePreferences(id, request)

    @PostMapping("/{id}/close")
    fun closeVisit(@PathVariable id: Int): VisitResponse = visitService.closeVisit(id)

    @GetMapping("/{id}/recommendations")
    fun getRecommendations(@PathVariable id: Int): List<MenuItemResponse> =
        visitService.getRecommendations(id)
}
