package ru.severnoyesiyanieBack.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.severnoyesiyanieBack.entity.Visit

interface VisitRepository : JpaRepository<Visit, Int> {
    fun findByIdVisit(idVisit: Int): Visit?
}