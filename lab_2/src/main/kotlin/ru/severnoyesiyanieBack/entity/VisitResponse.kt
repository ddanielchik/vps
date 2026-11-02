package ru.severnoyesiyanieBack.dto

import ru.severnoyesiyanieBack.entity.VisitStatus
import java.time.LocalDateTime

data class VisitResponse(
    val idVisit: Int,
    val clientId: Int,
    val tableNumber: Int?,
    val openedAt: LocalDateTime,
    val closedAt: LocalDateTime?,
    val status: VisitStatus,
    val tastePreferences: List<String>
)