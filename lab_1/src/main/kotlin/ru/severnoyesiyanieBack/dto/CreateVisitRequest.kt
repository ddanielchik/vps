package ru.severnoyesiyanieBack.dto

import jakarta.validation.constraints.Positive

data class CreateVisitRequest(
    @field:Positive
    val clientId: Int,

    @field:Positive
    val tableNumber: Int
)