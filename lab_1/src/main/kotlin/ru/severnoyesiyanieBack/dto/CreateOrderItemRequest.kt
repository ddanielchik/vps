package ru.severnoyesiyanieBack.dto

import jakarta.validation.constraints.Positive

data class CreateOrderItemRequest(
    @field:Positive
    val menuItemId: Int,

    @field:Positive
    val quantity: Int
)