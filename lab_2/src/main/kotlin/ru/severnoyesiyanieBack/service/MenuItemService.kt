package ru.severnoyesiyanieBack.service

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Slice
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.severnoyesiyanieBack.dto.CreateMenuItemRequest
import ru.severnoyesiyanieBack.entity.MenuItem
import ru.severnoyesiyanieBack.dto.MenuItemResponse
import ru.severnoyesiyanieBack.exception.MenuItemNotFoundException
import ru.severnoyesiyanieBack.repository.MenuItemRepository


@Service
class MenuItemService(
    private val menuItemRepository: MenuItemRepository,
    private val tasteTagService: TasteTagService
) {

    @Transactional(readOnly = true)
    fun getMenuItemById(idPosition: Int): MenuItemResponse {

        val cocktail = menuItemRepository.findById(idPosition)
            .orElseThrow { MenuItemNotFoundException(idPosition) }

        return toResponse(cocktail)
    }

    @Transactional(readOnly = true)
    fun getAllMenuItems(page: Int): Slice<MenuItemResponse> {

        if (page < 0) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Page must be non-negative"
            )
        }

        val pageable = PageRequest.of(
            page,
            10,
            Sort.by("idPosition")
        )

        return menuItemRepository.findAllBy(pageable)
            .map { cocktail ->
                toResponse(cocktail)
            }
    }

    @Transactional
    fun createMenuItem(request: CreateMenuItemRequest): MenuItemResponse {

        val tags = tasteTagService.getTagsByIds(request.tasteTagIds)

        if (tags.size != request.tasteTagIds.size) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "One or more taste tags do not exist"
            )
        }

        val cocktail = MenuItem(
            name = request.name,
            price = request.price,
            ingredients = request.ingredients,
            tasteTags = tags.toMutableSet()
        )

        val savedCocktail = menuItemRepository.save(cocktail)

        return toResponse(savedCocktail)
    }

    @Transactional
    fun updateMenuItem(
        idPosition: Int,
        request: CreateMenuItemRequest
    ): MenuItemResponse {

        val cocktail = menuItemRepository.findById(idPosition)
            .orElseThrow { MenuItemNotFoundException(idPosition) }

        val tags = tasteTagService.getTagsByIds(request.tasteTagIds)

        if (tags.size != request.tasteTagIds.size) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "One or more taste tags do not exist"
            )
        }

        cocktail.name = request.name
        cocktail.price = request.price
        cocktail.ingredients = request.ingredients
        cocktail.tasteTags = tags.toMutableSet()

        val savedCocktail = menuItemRepository.save(cocktail)

        return toResponse(savedCocktail)
    }

    @Transactional
    fun deleteMenuItem(idPosition: Int) {

        val cocktail = menuItemRepository.findById(idPosition)
            .orElseThrow { MenuItemNotFoundException(idPosition) }

        menuItemRepository.delete(cocktail)
    }

    fun getMenuItemEntityById(idPosition: Int): MenuItem {
        return menuItemRepository.findById(idPosition)
            .orElseThrow { MenuItemNotFoundException(idPosition) }
    }

    private fun toResponse(cocktail: MenuItem): MenuItemResponse {

        return MenuItemResponse(
            idPosition = requireNotNull(cocktail.idPosition),
            name = cocktail.name,
            price = cocktail.price,
            ingredients = requireNotNull(cocktail.ingredients),
            tasteTags = cocktail.tasteTags.map { it.name }.sorted()
        )
    }

    @Transactional(readOnly = true)
    fun getRecommendedMenuItems(
        preferredTastes: Set<String>
    ): List<MenuItemResponse> {

        if (preferredTastes.isEmpty()) {
            return emptyList()
        }

        return menuItemRepository.findByTasteTags(
            preferredTastes,
            PageRequest.of(0, 50, Sort.by("idPosition"))
        ).content.map { cocktail ->
            toResponse(cocktail)
        }
    }
}