package ru.severnoyesiyanieback.menu.controller

import io.swagger.v3.oas.annotations.Hidden
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import ru.severnoyesiyanieback.menu.dto.MenuItemResponse
import ru.severnoyesiyanieback.menu.dto.TasteTagResponse
import ru.severnoyesiyanieback.menu.service.MenuItemService

@Hidden
@RestController
@RequestMapping("/internal/menu")
class InternalMenuController(private val menuItemService: MenuItemService) {
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
