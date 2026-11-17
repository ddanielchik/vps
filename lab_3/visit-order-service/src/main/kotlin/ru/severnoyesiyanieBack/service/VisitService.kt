package ru.severnoyesiyanieBack.service

import java.time.LocalDateTime
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.severnoyesiyanieBack.client.MenuClient
import ru.severnoyesiyanieBack.client.UserClient
import ru.severnoyesiyanieBack.dto.CreateVisitRequest
import ru.severnoyesiyanieBack.dto.MenuItemResponse
import ru.severnoyesiyanieBack.dto.UpdateVisitRequest
import ru.severnoyesiyanieBack.dto.VisitPreferencesRequest
import ru.severnoyesiyanieBack.dto.VisitResponse
import ru.severnoyesiyanieBack.entity.TastePreference
import ru.severnoyesiyanieBack.entity.UserStatus
import ru.severnoyesiyanieBack.entity.Visit
import ru.severnoyesiyanieBack.entity.VisitStatus
import ru.severnoyesiyanieBack.exception.TasteTagsNotFoundException
import ru.severnoyesiyanieBack.exception.VisitAlreadyClosedException
import ru.severnoyesiyanieBack.exception.VisitNotFoundException
import ru.severnoyesiyanieBack.repository.VisitRepository

@Service
class VisitService(
    private val visitRepository: VisitRepository,
    private val userClient: UserClient,
    private val menuClient: MenuClient
) {
    @Transactional(readOnly = true)
    fun getVisitById(id: Int): VisitResponse = toResponse(findVisit(id))

    @Transactional
    fun openVisit(request: CreateVisitRequest): VisitResponse {

        val client = userClient.getUser(request.clientId)
        if (client.status == UserStatus.BLOCKED) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Blocked users cannot open visits")
        }

        val visit = Visit(
            clientId = request.clientId,
            tableNumber = request.tableNumber
        )

        val savedVisit = visitRepository.save(visit)

        return toResponse(savedVisit)
    }

    @Transactional
    fun updateVisit(
        id: Int,
        request: UpdateVisitRequest
    ): VisitResponse {

        val visit = findOpenVisit(id)

        visit.tableNumber = request.tableNumber

        return toResponse(visitRepository.save(visit))
    }

    @Transactional(readOnly = true)
    fun getAllVisits(
        page: Int,
        size: Int
    ): Page<VisitResponse> {

        if (page < 0) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Page must be non-negative"
            )
        }

        val pageable = PageRequest.of(
            page,
            size.coerceIn(1, 50),
            Sort.by("idVisit")
        )

        return visitRepository.findAll(pageable)
            .map { visit -> toResponse(visit) }
    }

    @Transactional
    fun closeVisit(id: Int): VisitResponse {

        val visit = findOpenVisit(id)

        visit.status = VisitStatus.CLOSED
        visit.closedAt = LocalDateTime.now()
        visit.tastePreferences.clear()

        return toResponse(visitRepository.save(visit))
    }

    @Transactional
    fun updateTastePreferences(
        id: Int,
        request: VisitPreferencesRequest
    ): VisitResponse {

        val visit = findOpenVisit(id)

        val tags = if (request.tasteTagIds.isEmpty()) emptyList() else menuClient.getTags(request.tasteTagIds)

        if (tags.size != request.tasteTagIds.size) {
            throw TasteTagsNotFoundException()
        }

        visit.tastePreferences.clear()
        visit.tastePreferences.addAll(tags.map { TastePreference(it.idTag, it.name) })

        return toResponse(visitRepository.save(visit))
    }

    @Transactional(readOnly = true)
    fun getRecommendations(visitId: Int): List<MenuItemResponse> {
        val visit = findOpenVisit(visitId)
        val preferredTastes = visit.tastePreferences.map { it.name }.toSet()
        if (preferredTastes.isEmpty()) return emptyList()
        return menuClient.getRecommendations(preferredTastes)
    }

    private fun findVisit(id: Int): Visit = visitRepository.findById(id)
        .orElseThrow { VisitNotFoundException(id) }

    private fun findOpenVisit(id: Int): Visit {
        val visit = findVisit(id)
        if (visit.status != VisitStatus.OPEN) throw VisitAlreadyClosedException(id)
        return visit
    }

    private fun toResponse(visit: Visit): VisitResponse = VisitResponse.Builder()
        .idVisit(requireNotNull(visit.idVisit))
        .clientId(visit.clientId)
        .tableNumber(visit.tableNumber)
        .openedAt(visit.openedAt)
        .closedAt(visit.closedAt)
        .status(visit.status)
        .tastePreferences(visit.tastePreferences.map { it.name })
        .build()
}
