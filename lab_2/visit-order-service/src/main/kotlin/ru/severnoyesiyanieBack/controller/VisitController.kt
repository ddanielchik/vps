package ru.severnoyesiyanieBack.controller

import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import ru.severnoyesiyanieBack.dto.CreateVisitRequest
import ru.severnoyesiyanieBack.dto.VisitResponse
import ru.severnoyesiyanieBack.service.VisitService
import org.springframework.http.ResponseEntity
import ru.severnoyesiyanieBack.dto.UpdateVisitRequest
import ru.severnoyesiyanieBack.dto.VisitPreferencesRequest
import ru.severnoyesiyanieBack.service.RecommendationService
import ru.severnoyesiyanieBack.dto.MenuItemResponse

@RestController
@RequestMapping("/api/visits")
class VisitController(private val visitService: VisitService, private val recommendationService: RecommendationService) {

    @GetMapping("/{id}")
    fun getVisitById(@PathVariable id: Int): ResponseEntity<VisitResponse> {
        return ResponseEntity.ok(visitService.getVisitById(id))
    }

    @PostMapping
    fun openVisit(
        @Valid @RequestBody request: CreateVisitRequest
    ): ResponseEntity<VisitResponse> {
        return ResponseEntity.status(HttpStatus.CREATED).body(visitService.openVisit(request))
    }

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
    ): ResponseEntity<VisitResponse> {
        return ResponseEntity.ok(visitService.updateVisit(id, request))
    }

    @PutMapping("/{id}/preferences")
    fun updateTastePreferences(
        @PathVariable id: Int,
        @Valid @RequestBody request: VisitPreferencesRequest
    ): ResponseEntity<VisitResponse> {
        return ResponseEntity.ok(visitService.updateTastePreferences(id, request))
    }

    @PostMapping("/{id}/close")
    fun closeVisit(@PathVariable id: Int): ResponseEntity<VisitResponse> {
        return ResponseEntity.ok(visitService.closeVisit(id))
    }

    @GetMapping("/{id}/recommendations")
    fun getRecommendations(@PathVariable id: Int): ResponseEntity<List<MenuItemResponse>> {
        return ResponseEntity.ok(recommendationService.getRecommendations(id))
    }
}
