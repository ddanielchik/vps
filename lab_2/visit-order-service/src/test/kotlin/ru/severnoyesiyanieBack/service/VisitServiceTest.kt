
package ru.severnoyesiyanieBack.service

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Answers
import org.mockito.Mockito.*
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import ru.severnoyesiyanieBack.dto.CreateVisitRequest
import ru.severnoyesiyanieBack.dto.UpdateVisitRequest
import ru.severnoyesiyanieBack.dto.VisitPreferencesRequest
import ru.severnoyesiyanieBack.entity.TastePreference
import ru.severnoyesiyanieBack.dto.UserResponse
import ru.severnoyesiyanieBack.client.UserClient
import ru.severnoyesiyanieBack.client.MenuClient
import ru.severnoyesiyanieBack.client.TasteTagResponse
import ru.severnoyesiyanieBack.client.RemoteBusinessException
import ru.severnoyesiyanieBack.entity.UserRole
import ru.severnoyesiyanieBack.entity.Visit
import ru.severnoyesiyanieBack.entity.VisitStatus
import ru.severnoyesiyanieBack.exception.TasteTagsNotFoundException
import ru.severnoyesiyanieBack.exception.VisitAlreadyClosedException
import ru.severnoyesiyanieBack.exception.VisitNotFoundException
import ru.severnoyesiyanieBack.repository.VisitRepository
import java.time.LocalDateTime
import java.util.Optional

class VisitServiceTest {

    private lateinit var visitRepository: VisitRepository
    private lateinit var userClient: UserClient
    private lateinit var menuClient: MenuClient
    private lateinit var service: VisitService

    private var savedVisit: Visit? = null

    private val client = UserResponse(
        1, "Client", "client@example.com", UserRole.CLIENT
    )

    private val sweet = TastePreference(10, "Sweet")
    private val sour = TastePreference(11, "Sour")
    private val bitter = TastePreference(12, "Bitter")

    private val openedAt = LocalDateTime.of(2025, 4, 7, 20, 30)

    private val visit = Visit(
        idVisit = 4,
        clientId = client.idUser,
        tableNumber = 6,
        openedAt = openedAt,
        tastePreferences = mutableSetOf(sweet)
    )

    @BeforeEach
    fun setUp() {
        savedVisit = null

        visitRepository = mock(VisitRepository::class.java) { invocation ->
            if (invocation.method.name == "save") {
                invocation.getArgument<Visit>(0).also {
                    if (it.idVisit == null) {
                        it.idVisit = 4
                    }
                    savedVisit = it
                }
            } else {
                Answers.RETURNS_DEFAULTS.answer(invocation)
            }
        }

        userClient = mock(UserClient::class.java)
        menuClient = mock(MenuClient::class.java)

        service = VisitService(
            visitRepository,
            userClient,
            menuClient,
            mock(ReferenceLock::class.java)
        )
    }

    @Test
    fun `opening a visit creates an open visit for the requested client and table`() {
        doReturn(client)
            .`when`(userClient).getUser(1)

        val before = LocalDateTime.now()

        val response = service.openVisit(
            CreateVisitRequest(clientId = 1, tableNumber = 8)
        )

        val persisted = requireNotNull(savedVisit)

        assertEquals(4, response.idVisit)
        assertEquals(1, response.clientId)
        assertEquals(8, response.tableNumber)
        assertEquals(VisitStatus.OPEN, response.status)
        assertNull(response.closedAt)
        assertTrue(response.tastePreferences.isEmpty())

        assertFalse(response.openedAt.isBefore(before))
        assertFalse(response.openedAt.isAfter(LocalDateTime.now()))

        assertEquals(client.idUser, persisted.clientId)
        assertEquals(8, persisted.tableNumber)

        verify(userClient).getUser(1)
        verify(visitRepository).save(persisted)
    }

    @Test
    fun `opening a visit for a missing client does not save anything`() {
        doThrow(RemoteBusinessException(404, "User 99 not found"))
            .`when`(userClient).getUser(99)

        assertThrows(RemoteBusinessException::class.java) {
            service.openVisit(
                CreateVisitRequest(clientId = 99, tableNumber = 8)
            )
        }

        verifyNoInteractions(visitRepository, menuClient)
    }

    @Test
    fun `finding a visit maps its client timestamps status and taste preferences`() {
        visit.status = VisitStatus.CLOSED
        visit.closedAt = openedAt.plusHours(2)

        `when`(visitRepository.findById(4))
            .thenReturn(Optional.of(visit))

        val response = service.getVisitById(4)

        assertEquals(4, response.idVisit)
        assertEquals(1, response.clientId)
        assertEquals(6, response.tableNumber)
        assertEquals(openedAt, response.openedAt)
        assertEquals(openedAt.plusHours(2), response.closedAt)
        assertEquals(VisitStatus.CLOSED, response.status)
        assertEquals(listOf("Sweet"), response.tastePreferences)

        verifyNoInteractions(userClient, menuClient)
    }

    @Test
    fun `finding a missing visit reports not found`() {
        `when`(visitRepository.findById(99))
            .thenReturn(Optional.empty())

        assertThrows(VisitNotFoundException::class.java) {
            service.getVisitById(99)
        }
    }

    @Test
    fun `updating an open visit changes only its table`() {
        `when`(visitRepository.findById(4))
            .thenReturn(Optional.of(visit))

        val response = service.updateVisit(
            4,
            UpdateVisitRequest(tableNumber = 9)
        )

        assertEquals(9, visit.tableNumber)
        assertEquals(9, response.tableNumber)
        assertEquals(VisitStatus.OPEN, response.status)
        assertEquals(openedAt, response.openedAt)
        assertNull(response.closedAt)
        assertEquals(listOf("Sweet"), response.tastePreferences)

        assertSame(visit, savedVisit)
        verify(visitRepository).save(visit)
    }

    @Test
    fun `updating a missing visit reports not found`() {
        `when`(visitRepository.findById(99))
            .thenReturn(Optional.empty())

        assertThrows(VisitNotFoundException::class.java) {
            service.updateVisit(
                99,
                UpdateVisitRequest(tableNumber = 9)
            )
        }

        assertNull(savedVisit)
    }

    @Test
    fun `closed visits cannot be moved to another table`() {
        visit.status = VisitStatus.CLOSED

        `when`(visitRepository.findById(4))
            .thenReturn(Optional.of(visit))

        assertThrows(VisitAlreadyClosedException::class.java) {
            service.updateVisit(
                4,
                UpdateVisitRequest(tableNumber = 9)
            )
        }

        assertEquals(6, visit.tableNumber)
        verify(visitRepository, never()).save(visit)
    }

    @Test
    fun `listing visits preserves page totals and sorts by visit id`() {
        val pageable = PageRequest.of(
            2, 20, Sort.by("idVisit")
        )

        `when`(visitRepository.findAll(pageable))
            .thenReturn(PageImpl(listOf(visit), pageable, 63))

        val result = service.getAllVisits(
            page = 2,
            size = 20
        )

        assertEquals(2, result.number)
        assertEquals(20, result.size)
        assertEquals(63L, result.totalElements)
        assertEquals(4, result.totalPages)
        assertEquals(listOf(4), result.content.map { it.idVisit })
        assertEquals(
            listOf("Sweet"),
            result.content.single().tastePreferences
        )

        verify(visitRepository).findAll(pageable)
    }

    @Test
    fun `listing visits caps an oversized page at fifty records`() {
        val pageable = PageRequest.of(
            0, 50, Sort.by("idVisit")
        )

        `when`(visitRepository.findAll(pageable))
            .thenReturn(PageImpl(emptyList(), pageable, 0))

        val result = service.getAllVisits(
            page = 0,
            size = 100
        )

        assertEquals(50, result.size)
        assertTrue(result.isEmpty)
        verify(visitRepository).findAll(pageable)
    }

    @Test
    fun `listing visits coerces zero page size to one record`() {
        val pageable = PageRequest.of(
            0, 1, Sort.by("idVisit")
        )

        `when`(visitRepository.findAll(pageable))
            .thenReturn(PageImpl(listOf(visit), pageable, 1))

        val result = service.getAllVisits(
            page = 0,
            size = 0
        )

        assertEquals(1, result.size)
        assertEquals(4, result.content.single().idVisit)
        verify(visitRepository).findAll(pageable)
    }

    @Test
    fun `listing visits coerces negative page size to one record`() {
        val pageable = PageRequest.of(
            0, 1, Sort.by("idVisit")
        )

        `when`(visitRepository.findAll(pageable))
            .thenReturn(PageImpl(listOf(visit), pageable, 1))

        val result = service.getAllVisits(
            page = 0,
            size = -10
        )

        assertEquals(1, result.size)
        verify(visitRepository).findAll(pageable)
    }

    @Test
    fun `negative page number is rejected before repository access`() {
        val exception = assertThrows(ResponseStatusException::class.java) {
            service.getAllVisits(
                page = -1,
                size = 20
            )
        }

        assertEquals(HttpStatus.BAD_REQUEST, exception.statusCode)
        verifyNoInteractions(visitRepository)
    }

    @Test
    fun `closing a visit records its end time and removes preferences`() {
        `when`(visitRepository.findById(4))
            .thenReturn(Optional.of(visit))

        val before = LocalDateTime.now()

        val response = service.closeVisit(4)
        val closedAt = requireNotNull(response.closedAt)

        assertEquals(VisitStatus.CLOSED, visit.status)
        assertEquals(VisitStatus.CLOSED, response.status)

        assertFalse(closedAt.isBefore(before))
        assertFalse(closedAt.isAfter(LocalDateTime.now()))

        assertEquals(closedAt, visit.closedAt)
        assertEquals(openedAt, response.openedAt)
        assertEquals(6, response.tableNumber)

        assertTrue(visit.tastePreferences.isEmpty())
        assertTrue(response.tastePreferences.isEmpty())

        verify(visitRepository).findById(4)
        verify(visitRepository).save(visit)
    }

    @Test
    fun `closing a missing visit reports not found`() {
        `when`(visitRepository.findById(99))
            .thenReturn(Optional.empty())

        assertThrows(VisitNotFoundException::class.java) {
            service.closeVisit(99)
        }

        assertNull(savedVisit)
    }

    @Test
    fun `closing an already closed visit keeps its original end time`() {
        visit.status = VisitStatus.CLOSED
        visit.closedAt = openedAt.plusHours(1)

        `when`(visitRepository.findById(4))
            .thenReturn(Optional.of(visit))

        assertThrows(VisitAlreadyClosedException::class.java) {
            service.closeVisit(4)
        }

        assertEquals(openedAt.plusHours(1), visit.closedAt)
        assertEquals(setOf(sweet), visit.tastePreferences)

        verify(visitRepository, never()).save(visit)
    }

    @Test
    fun `updating preferences replaces previously selected tastes`() {
        val ids = setOf(11, 12)

        `when`(visitRepository.findById(4))
            .thenReturn(Optional.of(visit))

        doReturn(listOf(sour, bitter).map { TasteTagResponse(it.idTag, it.name) })
            .`when`(menuClient).getTags(ids)

        val response = service.updateTastePreferences(
            4,
            VisitPreferencesRequest(ids)
        )

        assertEquals(
            setOf(sour, bitter),
            visit.tastePreferences
        )

        assertEquals(
            setOf("Sour", "Bitter"),
            response.tastePreferences.toSet()
        )

        assertEquals(VisitStatus.OPEN, response.status)
        verify(menuClient).getTags(ids)
        verify(visitRepository).save(visit)
    }

    @Test
    fun `empty preferences clear all previously selected tastes`() {
        val ids = emptySet<Int>()

        `when`(visitRepository.findById(4))
            .thenReturn(Optional.of(visit))

        
        val response = service.updateTastePreferences(
            4,
            VisitPreferencesRequest(ids)
        )

        assertTrue(visit.tastePreferences.isEmpty())
        assertTrue(response.tastePreferences.isEmpty())

        verify(visitRepository).save(visit)
    }

    @Test
    fun `an unknown taste tag preserves original preferences`() {
        val ids = setOf(11, 99)

        `when`(visitRepository.findById(4))
            .thenReturn(Optional.of(visit))

        doReturn(listOf(TasteTagResponse(sour.idTag, sour.name)))
            .`when`(menuClient).getTags(ids)

        assertThrows(TasteTagsNotFoundException::class.java) {
            service.updateTastePreferences(
                4,
                VisitPreferencesRequest(ids)
            )
        }

        assertEquals(
            setOf(sweet),
            visit.tastePreferences
        )

        verify(visitRepository, never()).save(visit)
    }

    @Test
    fun `preferences cannot be changed on a closed visit`() {
        visit.status = VisitStatus.CLOSED

        `when`(visitRepository.findById(4))
            .thenReturn(Optional.of(visit))

        assertThrows(VisitAlreadyClosedException::class.java) {
            service.updateTastePreferences(
                4,
                VisitPreferencesRequest(setOf(11))
            )
        }

        assertEquals(
            setOf(sweet),
            visit.tastePreferences
        )

        verifyNoInteractions(menuClient)
        verify(visitRepository, never()).save(visit)
    }

    @Test
    fun `preferences of a missing visit cannot be updated`() {
        `when`(visitRepository.findById(99))
            .thenReturn(Optional.empty())

        assertThrows(VisitNotFoundException::class.java) {
            service.updateTastePreferences(
                99,
                VisitPreferencesRequest(setOf(11))
            )
        }

        verifyNoInteractions(menuClient)
        assertNull(savedVisit)
    }
}
