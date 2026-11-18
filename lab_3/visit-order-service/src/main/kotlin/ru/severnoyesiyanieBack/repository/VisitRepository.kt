package ru.severnoyesiyanieBack.repository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import ru.severnoyesiyanieBack.entity.Visit

interface VisitRepository : JpaRepository<Visit, Int> {
    fun findByClientId(clientId: Int, pageable: Pageable): Page<Visit>
    fun existsByClientId(clientId: Int): Boolean
}
