package ru.severnoyesiyanieback.menu.controller

import jakarta.validation.Valid
import org.springframework.data.domain.Slice
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import org.springframework.web.bind.annotation.DeleteMapping
import ru.severnoyesiyanieback.menu.dto.TasteTagResponse
import ru.severnoyesiyanieback.menu.dto.CreateMenuItemRequest
import ru.severnoyesiyanieback.menu.dto.MenuItemResponse
import ru.severnoyesiyanieback.menu.service.MenuItemService

@RestController
@RequestMapping("/api/menu")
class MenuItemController(private val menuItemService: MenuItemService) {
    @GetMapping("/{idPosition}")
    fun getMenuItemById(@PathVariable idPosition: Int): Mono<MenuItemResponse> =
        menuItemService.getMenuItemById(idPosition)

    @GetMapping
    fun getAllMenuItems(@RequestParam(defaultValue = "0") page: Int): Mono<Slice<MenuItemResponse>> =
        menuItemService.getAllMenuItems(page)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createMenuItem(@Valid @RequestBody request: CreateMenuItemRequest): Mono<MenuItemResponse> =
        menuItemService.createMenuItem(request)

    @PutMapping("/{idPosition}")
    fun updateMenuItem(
        @PathVariable idPosition: Int,
        @Valid @RequestBody request: CreateMenuItemRequest
    ): Mono<MenuItemResponse> = menuItemService.updateMenuItem(idPosition, request)

    @GetMapping("/tags")
    fun getTags(@RequestParam(required = false) ids: Set<Int>?): Flux<TasteTagResponse> =
        menuItemService.getTagsByIds(ids.orEmpty())

    @GetMapping("/recommendations")
    fun getRecommendations(@RequestParam(required = false) tastes: Set<String>?): Flux<MenuItemResponse> =
        menuItemService.getRecommendedMenuItems(tastes.orEmpty())

    @DeleteMapping("/{idPosition}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteMenuItem(@PathVariable idPosition: Int): Mono<Void> =
        menuItemService.deleteMenuItem(idPosition)
}
