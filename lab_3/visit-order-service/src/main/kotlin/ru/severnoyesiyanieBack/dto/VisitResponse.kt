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
) {
    class Builder {
        private var idVisit: Int? = null
        private var clientId: Int? = null
        private var tableNumber: Int? = null
        private var openedAt: LocalDateTime? = null
        private var closedAt: LocalDateTime? = null
        private var status: VisitStatus? = null
        private var tastePreferences: List<String>? = null

        fun idVisit(value: Int) = apply { idVisit = value }
        fun clientId(value: Int) = apply { clientId = value }
        fun tableNumber(value: Int?) = apply { tableNumber = value }
        fun openedAt(value: LocalDateTime) = apply { openedAt = value }
        fun closedAt(value: LocalDateTime?) = apply { closedAt = value }
        fun status(value: VisitStatus) = apply { status = value }
        fun tastePreferences(value: List<String>) = apply { tastePreferences = value }

        fun build() = VisitResponse(
            idVisit = requireNotNull(idVisit) { "idVisit is required" },
            clientId = requireNotNull(clientId) { "clientId is required" },
            tableNumber = tableNumber,
            openedAt = requireNotNull(openedAt) { "openedAt is required" },
            closedAt = closedAt,
            status = requireNotNull(status) { "status is required" },
            tastePreferences = requireNotNull(tastePreferences) { "tastePreferences is required" }
        )
    }
}
