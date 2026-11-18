package ru.severnoyesiyanieback.menu.service

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Slice
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import ru.severnoyesiyanieback.menu.client.VisitOrderClient
import org.springframework.stereotype.Service
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import ru.severnoyesiyanieback.menu.dto.CreateMenuItemRequest
import ru.severnoyesiyanieback.menu.dto.MenuItemResponse
import ru.severnoyesiyanieback.menu.dto.TasteTagResponse
import ru.severnoyesiyanieback.menu.entity.MenuItem
import ru.severnoyesiyanieback.menu.entity.TasteTag
import ru.severnoyesiyanieback.menu.exception.MenuItemNotFoundException
import ru.severnoyesiyanieback.menu.exception.TasteTagsNotFoundException
import ru.severnoyesiyanieback.menu.repository.MenuItemRepository
import ru.severnoyesiyanieback.menu.repository.TasteTagRepository

@Service
class MenuItemService(
    private val menuItemRepository: MenuItemRepository,
    private val tasteTagRepository: TasteTagRepository,
    private val visitOrderClient: VisitOrderClient,
    transactionManager: PlatformTransactionManager
) {
    private val writeTransaction = TransactionTemplate(transactionManager)
    private val readTransaction = TransactionTemplate(transactionManager).apply { isReadOnly = true }

    fun getMenuItemById(idPosition: Int): Mono<MenuItemResponse> = inTransaction(readOnly = true) {
        toResponse(findItem(idPosition))
    }

    fun getAllMenuItems(page: Int): Mono<Slice<MenuItemResponse>> = inTransaction(readOnly = true) {
        if (page < 0) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be non-negative")
        }
        menuItemRepository.findAllBy(PageRequest.of(page, 10, Sort.by("idPosition"))).map(::toResponse)
    }

    fun createMenuItem(request: CreateMenuItemRequest): Mono<MenuItemResponse> = inTransaction {
        val tags = findTags(request.tasteTagIds)
        toResponse(menuItemRepository.save(MenuItem(
            name = request.name,
            price = request.price,
            ingredients = request.ingredients,
            tasteTags = tags.toMutableSet()
        )))
    }

    fun updateMenuItem(idPosition: Int, request: CreateMenuItemRequest): Mono<MenuItemResponse> = inTransaction {
        val item = findItem(idPosition)
        val tags = findTags(request.tasteTagIds)
        item.name = request.name
        item.price = request.price
        item.ingredients = request.ingredients
        item.tasteTags = tags.toMutableSet()
        toResponse(menuItemRepository.save(item))
    }

    fun deleteMenuItem(idPosition: Int): Mono<Void> = getMenuItemById(idPosition).flatMap {
        visitOrderClient.hasMenuItemsWithoutName(idPosition)
            .switchIfEmpty(Mono.error(IllegalStateException("Empty reference check response")))
            .onErrorMap {
                ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Cannot check menu references", it)
            }
            .flatMap { inUse ->
                if (inUse) Mono.error(ResponseStatusException(HttpStatus.CONFLICT, "Read older orders first to preserve this menu item name"))
                else inTransaction { menuItemRepository.delete(findItem(idPosition)) }.then()
            }
    }

    fun getTagsByIds(ids: Set<Int>): Flux<TasteTagResponse> = inTransaction(readOnly = true) {
        findTags(ids).map { TasteTagResponse(requireNotNull(it.idTag), it.name) }.sortedBy { it.idTag }
    }.flatMapMany { Flux.fromIterable(it) }

    fun getRecommendedMenuItems(preferredTastes: Set<String>): Flux<MenuItemResponse> = inTransaction(readOnly = true) {
        if (preferredTastes.isEmpty()) {
            emptyList()
        } else {
            menuItemRepository.findDistinctByTasteTags_NameIn(
                preferredTastes,
                PageRequest.of(0, 50, Sort.by("idPosition"))
            ).content.map(::toResponse)
        }
    }.flatMapMany { Flux.fromIterable(it) }

    private fun findItem(idPosition: Int): MenuItem = menuItemRepository.findById(idPosition)
        .orElseThrow { MenuItemNotFoundException(idPosition) }

    private fun findTags(ids: Set<Int>): List<TasteTag> {
        if (ids.isEmpty()) return emptyList()
        val tags = tasteTagRepository.findAllById(ids)
        if (tags.size != ids.size) throw TasteTagsNotFoundException()
        return tags
    }

    private fun toResponse(item: MenuItem) = MenuItemResponse.Builder()
        .idPosition(requireNotNull(item.idPosition))
        .name(item.name)
        .price(item.price)
        .ingredients(item.ingredients.orEmpty())
        .tasteTags(item.tasteTags.map { it.name }.sorted())
        .build()

    private fun <T : Any> inTransaction(readOnly: Boolean = false, action: () -> T): Mono<T> =
        Mono.fromCallable {
            val transaction = if (readOnly) readTransaction else writeTransaction
            requireNotNull(transaction.execute { action() })
        }.subscribeOn(Schedulers.boundedElastic())
}
