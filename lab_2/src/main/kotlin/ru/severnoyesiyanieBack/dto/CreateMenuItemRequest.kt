package ru.severnoyesiyanieBack.dto

import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Size
import java.math.BigDecimal

data class CreateMenuItemRequest(

    @field:NotBlank
    @field:Size(max = 50)
    val name: String,

    @field:DecimalMin("0.0")
    val price: BigDecimal,

    @field:NotBlank
    @field:Size(max = 500)
    val ingredients: String,

    @field:NotEmpty
    val tasteTagIds: Set<Int>
)