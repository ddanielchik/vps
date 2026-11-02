
package ru.severnoyesiyanieBack.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import ru.severnoyesiyanieBack.dto.MenuItemResponse
import ru.severnoyesiyanieBack.entity.TasteTag
import ru.severnoyesiyanieBack.entity.User
import ru.severnoyesiyanieBack.entity.Visit
import ru.severnoyesiyanieBack.entity.VisitStatus
import ru.severnoyesiyanieBack.exception.VisitAlreadyClosedException
import ru.severnoyesiyanieBack.exception.VisitNotFoundException
import ru.severnoyesiyanieBack.repository.VisitRepository
import java.math.BigDecimal
import java.util.Optional

class RecommendationServiceTest {

    private val visitRepository = mock(VisitRepository::class.java)
    private val menuItemService = mock(MenuItemService::class.java)

    private val service = RecommendationService(
        visitRepository,
        menuItemService
    )

    @Test
    fun `missing visit is rejected without reading the menu`() {
        doReturn(Optional.empty<Visit>())
            .`when`(visitRepository).findById(404)

        val exception = assertThrows(VisitNotFoundException::class.java) {
            service.getRecommendations(404)
        }

        assertTrue(exception.message.orEmpty().contains("404"))
        verifyNoInteractions(menuItemService)
    }

    @Test
    fun `closed visit is rejected even when preferences are empty`() {
        val visit = visit().apply {
            status = VisitStatus.CLOSED
        }

        doReturn(Optional.of(visit))
            .`when`(visitRepository).findById(7)

        assertThrows(VisitAlreadyClosedException::class.java) {
            service.getRecommendations(7)
        }

        verifyNoInteractions(menuItemService)
    }

    @Test
    fun `open visit without preferences has no recommendations and skips menu lookup`() {
        doReturn(Optional.of(visit()))
            .`when`(visitRepository).findById(7)

        val result = service.getRecommendations(7)

        assertTrue(result.isEmpty())
        verifyNoInteractions(menuItemService)
    }

    @Test
    fun `recommendations pass preferred tastes to menu service`() {
        val visit = visit(
            TasteTag(1, "Sour"),
            TasteTag(2, "Sweet")
        )

        val tastes = setOf("Sour", "Sweet")

        val expected = listOf(
            cocktailResponse(1, listOf("Sour", "Sweet")),
            cocktailResponse(3, listOf("Sweet"))
        )

        doReturn(Optional.of(visit))
            .`when`(visitRepository).findById(7)

        doReturn(expected)
            .`when`(menuItemService).getRecommendedMenuItems(tastes)

        val result = service.getRecommendations(7)

        assertEquals(expected, result)
        assertSame(expected, result)

        verify(visitRepository).findById(7)
        verify(menuItemService).getRecommendedMenuItems(tastes)
        verifyNoMoreInteractions(menuItemService)
    }

    @Test
    fun `empty menu result produces no recommendations`() {
        val tastes = setOf("Sour")

        doReturn(Optional.of(visit(TasteTag(1, "Sour"))))
            .`when`(visitRepository).findById(7)

        doReturn(emptyList<MenuItemResponse>())
            .`when`(menuItemService).getRecommendedMenuItems(tastes)

        val result = service.getRecommendations(7)

        assertTrue(result.isEmpty())

        verify(menuItemService).getRecommendedMenuItems(tastes)
        verifyNoMoreInteractions(menuItemService)
    }

    private fun visit(vararg tastes: TasteTag) = Visit(
        idVisit = 7,
        user = User(
            idUser = 1,
            name = "Anna",
            email = "anna@example.com"
        ),
        tastePreferences = tastes.toMutableSet()
    )

    private fun cocktailResponse(
        id: Int,
        tastes: List<String>
    ) = MenuItemResponse(
        idPosition = id,
        name = "Cocktail $id",
        price = BigDecimal("450.50"),
        ingredients = "Gin, lime",
        tasteTags = tastes
    )
}
