package ru.severnoyesiyanieBack.service

import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.severnoyesiyanieBack.dto.CreateVisitRequest
import ru.severnoyesiyanieBack.dto.UpdateVisitRequest
import ru.severnoyesiyanieBack.dto.VisitPreferencesRequest
import ru.severnoyesiyanieBack.dto.VisitResponse
import ru.severnoyesiyanieBack.entity.Visit
import ru.severnoyesiyanieBack.entity.TastePreference
import ru.severnoyesiyanieBack.client.UserClient
import ru.severnoyesiyanieBack.client.MenuClient
import ru.severnoyesiyanieBack.entity.VisitStatus
import ru.severnoyesiyanieBack.exception.TasteTagsNotFoundException
import ru.severnoyesiyanieBack.exception.VisitAlreadyClosedException
import ru.severnoyesiyanieBack.exception.VisitNotFoundException
import ru.severnoyesiyanieBack.repository.VisitRepository
import java.time.LocalDateTime

@Service
class VisitService(
    private val visitRepository: VisitRepository,
    private val userClient: UserClient,
    private val menuClient: MenuClient,
    private val referenceLock: ReferenceLock
) {

    @Transactional(readOnly = true)
    fun getVisitById(id: Int): VisitResponse {

        val visit = visitRepository.findById(id)
            .orElseThrow { VisitNotFoundException(id) }

        return toResponse(visit)
    }

    @Transactional
    fun openVisit(request: CreateVisitRequest): VisitResponse {

        referenceLock.acquire(ReferenceLock.Kind.USER, listOf(request.clientId), shared = true)
        userClient.getUser(request.clientId)

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

        val visit = visitRepository.findById(id)
            .orElseThrow { VisitNotFoundException(id) }

        if (visit.status != VisitStatus.OPEN) {
            throw VisitAlreadyClosedException(id)
        }

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

        val visit = visitRepository.findById(id)
            .orElseThrow { VisitNotFoundException(id) }

        if (visit.status != VisitStatus.OPEN) {
            throw VisitAlreadyClosedException(id)
        }

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

        val visit = visitRepository.findById(id)
            .orElseThrow { VisitNotFoundException(id) }

        if (visit.status != VisitStatus.OPEN) {
            throw VisitAlreadyClosedException(id)
        }

        val tags = if (request.tasteTagIds.isEmpty()) emptyList() else menuClient.getTags(request.tasteTagIds)

        if (tags.size != request.tasteTagIds.size) {
            throw TasteTagsNotFoundException()
        }

        visit.tastePreferences.clear()
        visit.tastePreferences.addAll(tags.map { TastePreference(it.idTag, it.name) })

        return toResponse(visitRepository.save(visit))
    }

    private fun toResponse(visit: Visit): VisitResponse {

        return VisitResponse(
            idVisit = requireNotNull(visit.idVisit),
            clientId = visit.clientId,
            tableNumber = visit.tableNumber,
            openedAt = visit.openedAt,
            closedAt = visit.closedAt,
            status = visit.status,
            tastePreferences = visit.tastePreferences.map { it.name }
        )
    }
}