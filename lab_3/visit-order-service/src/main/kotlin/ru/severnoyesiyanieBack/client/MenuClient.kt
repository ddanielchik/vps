package ru.severnoyesiyanieBack.client

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import reactivefeign.spring.config.ReactiveFeignClient
import reactor.core.publisher.Mono
import ru.severnoyesiyanieBack.dto.MenuItemResponse

@ReactiveFeignClient(name = "menu-service", url = "\${clients.menu.url:}")
interface MenuClient {
    @GetMapping("/api/v1/menu/{idPosition}")
    fun getMenuItem(@PathVariable("idPosition") idPosition: Int): Mono<MenuItemResponse>

    @GetMapping("/api/v1/menu/tags")
    fun getTags(@RequestParam("ids") ids: Set<Int>): Mono<List<TasteTagResponse>>

    @GetMapping("/api/v1/menu/recommendations")
    fun getRecommendations(@RequestParam("tastes") tastes: Set<String>): Mono<List<MenuItemResponse>>
}
