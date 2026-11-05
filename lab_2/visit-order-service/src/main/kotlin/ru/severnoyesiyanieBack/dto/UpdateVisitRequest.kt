package ru.severnoyesiyanieBack.dto

import jakarta.validation.constraints.Positive

data class UpdateVisitRequest(
    @field:Positive
    val tableNumber: Int
)