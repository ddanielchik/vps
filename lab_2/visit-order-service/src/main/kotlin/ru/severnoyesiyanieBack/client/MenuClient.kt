package ru.severnoyesiyanieBack.client

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import ru.severnoyesiyanieBack.dto.MenuItemResponse

@FeignClient(name = "menu-service", url = "\${clients.menu.url:}")
interface MenuClient {
    @GetMapping("/api/menu/{idPosition}")
    fun getMenuItem(@PathVariable("idPosition") idPosition: Int): MenuItemResponse

    @GetMapping("/internal/menu/tags")
    fun getTags(@RequestParam("ids") ids: Set<Int>): List<TasteTagResponse>

    @GetMapping("/internal/menu/recommendations")
    fun getRecommendations(@RequestParam("tastes") tastes: Set<String>): List<MenuItemResponse>

    @DeleteMapping("/internal/menu/{idPosition}")
    fun deleteMenuItem(@PathVariable("idPosition") idPosition: Int)
}
