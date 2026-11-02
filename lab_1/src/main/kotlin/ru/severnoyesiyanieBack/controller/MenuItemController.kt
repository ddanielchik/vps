package ru.severnoyesiyanieBack.controller

import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import ru.severnoyesiyanieBack.dto.MenuItemResponse
import ru.severnoyesiyanieBack.service.MenuItemService
import org.springframework.data.domain.Slice
import org.springframework.web.bind.annotation.RequestParam
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import ru.severnoyesiyanieBack.dto.CreateMenuItemRequest
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.DeleteMapping


@RestController
@RequestMapping("/api/menu")
class MenuItemController (private val menuItemService: MenuItemService){

    @GetMapping("/{idPosition}")
    fun getMenuItemById(
        @PathVariable idPosition: Int): ResponseEntity<MenuItemResponse> {
        return ResponseEntity.ok(menuItemService.getMenuItemById(idPosition))
    }
    @GetMapping
    fun getAllMenuItems(
        @RequestParam(defaultValue = "0") page: Int
    ): ResponseEntity<Slice<MenuItemResponse>> {
        return ResponseEntity.ok(menuItemService.getAllMenuItems(page))
    }
    @PostMapping
    fun createMenuItem(
        @Valid @RequestBody request: CreateMenuItemRequest
    ): ResponseEntity<MenuItemResponse> {
        return ResponseEntity.status(HttpStatus.CREATED).body(menuItemService.createMenuItem(request))
    }
    @PutMapping("/{idPosition}")
    fun updateMenuItem(
        @PathVariable idPosition: Int,
        @Valid @RequestBody request: CreateMenuItemRequest
    ): ResponseEntity<MenuItemResponse> {
        return ResponseEntity.ok(menuItemService.updateMenuItem(idPosition, request))
    }
    @DeleteMapping("/{idPosition}")
    fun deleteMenuItem(@PathVariable idPosition: Int): ResponseEntity<Void> {
        menuItemService.deleteMenuItem(idPosition)
        return ResponseEntity.noContent().build()
    }
}
