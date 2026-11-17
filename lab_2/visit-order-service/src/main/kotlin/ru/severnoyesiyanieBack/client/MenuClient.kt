package ru.severnoyesiyanieBack.client

import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import ru.severnoyesiyanieBack.dto.MenuItemResponse

@FeignClient(name = "menu-service", url = "\${clients.menu.url:}")
interface MenuClient {
    @GetMapping("/api/menu/{idPosition}")
    fun getMenuItem(@PathVariable("idPosition") idPosition: Int): MenuItemResponse

    @GetMapping("/api/menu/tags")
    fun getTags(@RequestParam("ids") ids: Set<Int>): List<TasteTagResponse>

    @GetMapping("/api/menu/recommendations")
    fun getRecommendations(@RequestParam("tastes") tastes: Set<String>): List<MenuItemResponse>

}
