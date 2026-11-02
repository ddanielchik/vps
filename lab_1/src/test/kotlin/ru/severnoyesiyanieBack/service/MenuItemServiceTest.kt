package ru.severnoyesiyanieBack.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito.*
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import ru.severnoyesiyanieBack.dto.CreateMenuItemRequest
import ru.severnoyesiyanieBack.dto.MenuItemResponse
import ru.severnoyesiyanieBack.entity.MenuItem
import ru.severnoyesiyanieBack.entity.TasteTag
import ru.severnoyesiyanieBack.exception.MenuItemNotFoundException
import ru.severnoyesiyanieBack.repository.MenuItemRepository
import java.math.BigDecimal
import java.util.Optional

class MenuItemServiceTest {

    private val savedItems = mutableListOf<MenuItem>()

    private val menuItemRepository = mock(MenuItemRepository::class.java) { invocation ->
        if (invocation.method.name == "save") {
            invocation.getArgument<MenuItem>(0).also { item ->
                if (item.idPosition == null) {
                    item.idPosition = 42
                }
                savedItems += item
            }
        } else {
            Answers.RETURNS_DEFAULTS.answer(invocation)
        }
    }

    private val tasteTagService = mock(TasteTagService::class.java)

    private val service = MenuItemService(
        menuItemRepository,
        tasteTagService
    )

    @Test
    fun `get menu item maps all fields and sorts taste names`() {
        val item = menuItem()

        doReturn(Optional.of(item))
            .`when`(menuItemRepository).findById(7)

        assertEquals(
            MenuItemResponse(
                7,
                "Aurora",
                BigDecimal("450.50"),
                "Gin, lime",
                listOf("Sour", "Sweet")
            ),
            service.getMenuItemById(7)
        )
    }

    @Test
    fun `get missing menu item reports its identity`() {
        doReturn(Optional.empty<MenuItem>())
            .`when`(menuItemRepository).findById(404)

        val exception = assertThrows(MenuItemNotFoundException::class.java) {
            service.getMenuItemById(404)
        }

        assertTrue(exception.message.orEmpty().contains("404"))
    }

    @Test
    fun `get menu item entity returns existing entity`() {
        val item = menuItem()

        doReturn(Optional.of(item))
            .`when`(menuItemRepository).findById(7)

        val result = service.getMenuItemEntityById(7)

        assertSame(item, result)
        verify(menuItemRepository).findById(7)
    }

    @Test
    fun `get missing menu item entity throws exception`() {
        doReturn(Optional.empty<MenuItem>())
            .`when`(menuItemRepository).findById(404)

        assertThrows(MenuItemNotFoundException::class.java) {
            service.getMenuItemEntityById(404)
        }
    }

    @Test
    fun `menu slice uses ten sorted records and preserves next page flag`() {
        val pageable = PageRequest.of(
            2,
            10,
            Sort.by("idPosition")
        )

        doReturn(
            SliceImpl(listOf(menuItem()), pageable, true)
        ).`when`(menuItemRepository).findAllBy(pageable)

        val result = service.getAllMenuItems(2)

        assertEquals(2, result.number)
        assertEquals(10, result.size)
        assertEquals(Sort.by("idPosition"), result.sort)
        assertTrue(result.hasNext())

        assertEquals(
            listOf(7),
            result.content.map { it.idPosition }
        )

        assertEquals(
            listOf("Sour", "Sweet"),
            result.content.single().tasteTags
        )

        verify(menuItemRepository).findAllBy(pageable)
        verifyNoMoreInteractions(menuItemRepository)
    }

    @Test
    fun `empty menu page has no content or following page`() {
        val pageable = PageRequest.of(
            0,
            10,
            Sort.by("idPosition")
        )

        doReturn(
            SliceImpl<MenuItem>(emptyList(), pageable, false)
        ).`when`(menuItemRepository).findAllBy(pageable)

        val result = service.getAllMenuItems(0)

        assertTrue(result.isEmpty)
        assertFalse(result.hasNext())
    }

    @Test
    fun `negative page numbers are rejected before querying database`() {
        for (page in listOf(-1, Int.MIN_VALUE)) {
            val exception = assertThrows(ResponseStatusException::class.java) {
                service.getAllMenuItems(page)
            }

            assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
            assertEquals("Page must be non-negative", exception.reason)
        }

        verifyNoInteractions(menuItemRepository, tasteTagService)
    }

    @Test
    fun `recommendations return matching cocktails with pagination`() {
        val tastes = setOf("Sweet", "Sour")

        val pageable = PageRequest.of(
            0,
            50,
            Sort.by("idPosition")
        )

        doReturn(
            SliceImpl(listOf(menuItem()), pageable, false)
        ).`when`(menuItemRepository).findByTasteTags(tastes, pageable)

        val result = service.getRecommendedMenuItems(tastes)

        assertEquals(1, result.size)
        assertEquals(7, result.single().idPosition)
        assertEquals(listOf("Sour", "Sweet"), result.single().tasteTags)

        verify(menuItemRepository).findByTasteTags(tastes, pageable)
    }

    @Test
    fun `recommendations with empty tastes do not query database`() {
        val result = service.getRecommendedMenuItems(emptySet())

        assertTrue(result.isEmpty())
        verifyNoInteractions(menuItemRepository)
    }

    @Test
    fun `create cocktail persists requested fields and resolved taste tags`() {
        val request = request()

        val tags = setOf(
            TasteTag(2, "Sweet"),
            TasteTag(1, "Sour")
        )

        doReturn(tags)
            .`when`(tasteTagService).getTagsByIds(request.tasteTagIds)

        val response = service.createMenuItem(request)

        assertEquals(
            MenuItemResponse(
                42,
                request.name,
                request.price,
                request.ingredients,
                listOf("Sour", "Sweet")
            ),
            response
        )

        val savedItem = savedItems.single()

        assertEquals(request.name, savedItem.name)
        assertEquals(request.price, savedItem.price)
        assertEquals(request.ingredients, savedItem.ingredients)
        assertEquals(tags.toSet(), savedItem.tasteTags)

        verify(menuItemRepository).save(savedItem)
    }

    @Test
    fun `create cocktail rejects missing taste tags without saving`() {
        val request = request()

        doReturn(setOf(TasteTag(1, "Sour")))
            .`when`(tasteTagService).getTagsByIds(request.tasteTagIds)

        val exception = assertThrows(ResponseStatusException::class.java) {
            service.createMenuItem(request)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        assertEquals(
            "One or more taste tags do not exist",
            exception.reason
        )

        verifyNoInteractions(menuItemRepository)
    }

    @Test
    fun `update cocktail replaces all editable fields and taste associations`() {
        val item = menuItem()
        val request = request().copy(tasteTagIds = setOf(3))
        val replacementTag = TasteTag(3, "Bitter")

        doReturn(Optional.of(item))
            .`when`(menuItemRepository).findById(7)

        doReturn(setOf(replacementTag))
            .`when`(tasteTagService).getTagsByIds(request.tasteTagIds)

        val response = service.updateMenuItem(7, request)

        assertEquals(
            MenuItemResponse(
                7,
                request.name,
                request.price,
                request.ingredients,
                listOf("Bitter")
            ),
            response
        )

        assertSame(item, savedItems.single())
        assertEquals(setOf(replacementTag), item.tasteTags)
        assertEquals(request.name, item.name)
        assertEquals(request.price, item.price)
        assertEquals(request.ingredients, item.ingredients)

        verify(menuItemRepository).save(item)
    }

    @Test
    fun `update rejects missing tags before mutating cocktail`() {
        val item = menuItem()
        val originalTags = item.tasteTags.toSet()
        val request = request()

        doReturn(Optional.of(item))
            .`when`(menuItemRepository).findById(7)

        doReturn(emptySet<TasteTag>())
            .`when`(tasteTagService).getTagsByIds(request.tasteTagIds)

        val exception = assertThrows(ResponseStatusException::class.java) {
            service.updateMenuItem(7, request)
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        assertEquals("Aurora", item.name)
        assertEquals(BigDecimal("450.50"), item.price)
        assertEquals("Gin, lime", item.ingredients)
        assertEquals(originalTags, item.tasteTags)
        assertTrue(savedItems.isEmpty())

        verify(menuItemRepository).findById(7)
        verifyNoMoreInteractions(menuItemRepository)
    }

    @Test
    fun `update missing cocktail does not load tags or save`() {
        doReturn(Optional.empty<MenuItem>())
            .`when`(menuItemRepository).findById(404)

        assertThrows(MenuItemNotFoundException::class.java) {
            service.updateMenuItem(404, request())
        }

        verify(menuItemRepository).findById(404)
        verifyNoMoreInteractions(menuItemRepository)
        verifyNoInteractions(tasteTagService)
    }

    @Test
    fun `delete cocktail removes loaded entity`() {
        val item = menuItem()

        doReturn(Optional.of(item))
            .`when`(menuItemRepository).findById(7)

        service.deleteMenuItem(7)

        verify(menuItemRepository).findById(7)
        verify(menuItemRepository).delete(item)
        verifyNoMoreInteractions(menuItemRepository)
    }

    @Test
    fun `delete missing cocktail does not call delete`() {
        doReturn(Optional.empty<MenuItem>())
            .`when`(menuItemRepository).findById(404)

        assertThrows(MenuItemNotFoundException::class.java) {
            service.deleteMenuItem(404)
        }

        verify(menuItemRepository).findById(404)
        verifyNoMoreInteractions(menuItemRepository)
    }

    private fun request() = CreateMenuItemRequest(
        name = "Northern lights",
        price = BigDecimal("600.00"),
        ingredients = "Vodka, cranberry",
        tasteTagIds = setOf(1, 2)
    )

    private fun menuItem() = MenuItem(
        idPosition = 7,
        name = "Aurora",
        price = BigDecimal("450.50"),
        ingredients = "Gin, lime",
        tasteTags = linkedSetOf(
            TasteTag(2, "Sweet"),
            TasteTag(1, "Sour")
        )
    )
}