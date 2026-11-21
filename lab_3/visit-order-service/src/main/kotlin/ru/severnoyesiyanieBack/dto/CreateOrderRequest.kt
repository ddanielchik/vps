package ru.severnoyesiyanieBack.dto

import jakarta.validation.Valid
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive

data class CreateOrderRequest(
    @field:Positive
    val visitId: Int,

    @field:NotEmpty
    @field:Valid
    val items: List<CreateOrderItemRequest>
)