package ru.severnoyesiyanieback.menu.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito.*
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.SliceImpl
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.SimpleTransactionStatus
import org.springframework.web.server.ResponseStatusException
import ru.severnoyesiyanieback.menu.dto.CreateMenuItemRequest
import ru.severnoyesiyanieback.menu.dto.MenuItemResponse
import ru.severnoyesiyanieback.menu.dto.TasteTagResponse
import ru.severnoyesiyanieback.menu.entity.MenuItem
import ru.severnoyesiyanieback.menu.entity.TasteTag
import ru.severnoyesiyanieback.menu.exception.MenuItemNotFoundException
import ru.severnoyesiyanieback.menu.exception.TasteTagsNotFoundException
import ru.severnoyesiyanieback.menu.repository.MenuItemRepository
import ru.severnoyesiyanieback.menu.repository.TasteTagRepository
import java.math.BigDecimal
import java.util.Optional

class MenuItemServiceTest {
    private val savedItems = mutableListOf<MenuItem>()
    private val menuRepository = mock(MenuItemRepository::class.java) { invocation ->
        if (invocation.method.name == "save") {
            invocation.getArgument<MenuItem>(0).also {
                if (it.idPosition == null) it.idPosition = 42
                savedItems += it
            }
        } else Answers.RETURNS_DEFAULTS.answer(invocation)
    }
    private val tagsRepository = mock(TasteTagRepository::class.java)
    private val transactionManager = mock(PlatformTransactionManager::class.java) { invocation ->
        if (invocation.method.name == "getTransaction") SimpleTransactionStatus()
        else Answers.RETURNS_DEFAULTS.answer(invocation)
    }
    private val service = MenuItemService(menuRepository, tagsRepository, transactionManager)

    @Test
    fun `database access is deferred to bounded elastic and fields are mapped inside transaction`() {
        doAnswer {
            assertTrue(Thread.currentThread().name.startsWith("boundedElastic-"))
            Optional.of(menuItem())
        }.`when`(menuRepository).findById(7)

        val result = service.getMenuItemById(7)
        verifyNoInteractions(menuRepository, transactionManager)
        assertEquals(MenuItemResponse(7, "Aurora", BigDecimal("450.50"), "Gin, lime", listOf("SOUR", "SWEET")), result.block())
        val order = inOrder(transactionManager, menuRepository)
        order.verify(transactionManager).getTransaction(any())
        order.verify(menuRepository).findById(7)
        order.verify(transactionManager).commit(any())
    }

    @Test
    fun `get missing cocktail reports identity`() {
        doReturn(Optional.empty<MenuItem>()).`when`(menuRepository).findById(404)
        val error = assertThrows(MenuItemNotFoundException::class.java) { service.getMenuItemById(404).block() }
        assertEquals("Cocktail with id 404 not found", error.message)
        verify(transactionManager).rollback(any())
    }

    @Test
    fun `menu slice keeps ten sorted records and next page flag`() {
        val pageable = PageRequest.of(2, 10, Sort.by("idPosition"))
        doReturn(SliceImpl(listOf(menuItem()), pageable, true)).`when`(menuRepository).findAllBy(pageable)
        val result = requireNotNull(service.getAllMenuItems(2).block())
        assertEquals(2, result.number)
        assertEquals(10, result.size)
        assertTrue(result.hasNext())
        assertEquals(listOf(7), result.content.map { it.idPosition })
        assertEquals(listOf("SOUR", "SWEET"), result.content.single().tasteTags)
    }

    @Test
    fun `empty menu page has no next page`() {
        val pageable = PageRequest.of(0, 10, Sort.by("idPosition"))
        doReturn(SliceImpl<MenuItem>(emptyList(), pageable, false)).`when`(menuRepository).findAllBy(pageable)
        val result = requireNotNull(service.getAllMenuItems(0).block())
        assertTrue(result.isEmpty)
        assertFalse(result.hasNext())
    }

    @Test
    fun `negative pages are rejected before querying repositories`() {
        for (page in listOf(-1, Int.MIN_VALUE)) {
            val error = assertThrows(ResponseStatusException::class.java) { service.getAllMenuItems(page).block() }
            assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
            assertEquals("Page must be non-negative", error.reason)
        }
        verifyNoInteractions(menuRepository, tagsRepository)
    }

    @Test
    fun `create persists all supplied fields and resolved tags`() {
        val request = request()
        val tags = listOf(TasteTag(2, "SWEET"), TasteTag(1, "SOUR"))
        doReturn(tags).`when`(tagsRepository).findAllById(request.tasteTagIds)
        val result = service.createMenuItem(request).block()
        assertEquals(MenuItemResponse(42, request.name, request.price, request.ingredients, listOf("SOUR", "SWEET")), result)
        val saved = savedItems.single()
        assertEquals(request.name, saved.name)
        assertEquals(request.price, saved.price)
        assertEquals(request.ingredients, saved.ingredients)
        assertEquals(tags.toSet(), saved.tasteTags)
    }

    @Test
    fun `create rejects unknown tags without saving`() {
        doReturn(listOf(TasteTag(1, "SOUR"))).`when`(tagsRepository).findAllById(setOf(1, 2))
        assertThrows(TasteTagsNotFoundException::class.java) { service.createMenuItem(request()).block() }
        verifyNoInteractions(menuRepository)
    }

    @Test
    fun `update replaces all editable fields and associations`() {
        val item = menuItem()
        val request = request().copy(tasteTagIds = setOf(3))
        val tag = TasteTag(3, "BITTER")
        doReturn(Optional.of(item)).`when`(menuRepository).findById(7)
        doReturn(listOf(tag)).`when`(tagsRepository).findAllById(setOf(3))
        assertEquals(MenuItemResponse(7, request.name, request.price, request.ingredients, listOf("BITTER")), service.updateMenuItem(7, request).block())
        assertSame(item, savedItems.single())
        assertEquals(setOf(tag), item.tasteTags)
    }

    @Test
    fun `unknown tags leave existing cocktail unchanged`() {
        val item = menuItem()
        val originalTags = item.tasteTags.toSet()
        doReturn(Optional.of(item)).`when`(menuRepository).findById(7)
        doReturn(emptyList<TasteTag>()).`when`(tagsRepository).findAllById(setOf(1, 2))
        assertThrows(TasteTagsNotFoundException::class.java) { service.updateMenuItem(7, request()).block() }
        assertEquals("Aurora", item.name)
        assertEquals(BigDecimal("450.50"), item.price)
        assertEquals(originalTags, item.tasteTags)
        assertTrue(savedItems.isEmpty())
    }

    @Test
    fun `updating missing cocktail does not query tags`() {
        doReturn(Optional.empty<MenuItem>()).`when`(menuRepository).findById(404)
        assertThrows(MenuItemNotFoundException::class.java) { service.updateMenuItem(404, request()).block() }
        verifyNoInteractions(tagsRepository)
    }

    @Test
    fun `delete removes loaded item after subscription`() {
        val item = menuItem()
        doReturn(Optional.of(item)).`when`(menuRepository).findById(7)
        val result = service.deleteMenuItem(7)
        verifyNoInteractions(menuRepository)
        result.block()
        verify(menuRepository).delete(item)
    }

    @Test
    fun `delete missing item never invokes delete`() {
        doReturn(Optional.empty<MenuItem>()).`when`(menuRepository).findById(404)
        assertThrows(MenuItemNotFoundException::class.java) { service.deleteMenuItem(404).block() }
        verify(menuRepository).findById(404)
        verifyNoMoreInteractions(menuRepository)
    }

    @Test
    fun `internal tag lookup maps sorted DTOs and accepts empty ids`() {
        doReturn(listOf(TasteTag(2, "SOUR"), TasteTag(1, "SWEET"))).`when`(tagsRepository).findAllById(setOf(1, 2))
        assertEquals(listOf(TasteTagResponse(1, "SWEET"), TasteTagResponse(2, "SOUR")), service.getTagsByIds(setOf(1, 2)).collectList().block())
        assertEquals(emptyList<TasteTagResponse>(), service.getTagsByIds(emptySet()).collectList().block())
        verify(tagsRepository).findAllById(setOf(1, 2))
        verifyNoMoreInteractions(tagsRepository)
    }

    @Test
    fun `recommendations use at most fifty sorted records`() {
        val tastes = setOf("SWEET", "SOUR")
        val pageable = PageRequest.of(0, 50, Sort.by("idPosition"))
        doReturn(SliceImpl(listOf(menuItem()), pageable, false)).`when`(menuRepository).findByTasteTags(tastes, pageable)
        val result = requireNotNull(service.getRecommendedMenuItems(tastes).collectList().block())
        assertEquals(listOf(7), result.map { it.idPosition })
        assertEquals(listOf("SOUR", "SWEET"), result.single().tasteTags)
    }

    @Test
    fun `empty preferences return empty recommendations without database query`() {
        assertEquals(emptyList<MenuItemResponse>(), service.getRecommendedMenuItems(emptySet()).collectList().block())
        verifyNoInteractions(menuRepository)
    }

    private fun request() = CreateMenuItemRequest("Northern Lights", BigDecimal("600.00"), "Vodka, cranberry", setOf(1, 2))

    private fun menuItem() = MenuItem(
        7, "Aurora", BigDecimal("450.50"), "Gin, lime",
        linkedSetOf(TasteTag(2, "SWEET"), TasteTag(1, "SOUR"))
    )
}
