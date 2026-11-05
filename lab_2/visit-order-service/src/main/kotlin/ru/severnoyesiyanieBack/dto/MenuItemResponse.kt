package ru.severnoyesiyanieBack.dto
import java.math.BigDecimal


data class MenuItemResponse(
    val idPosition: Int,
    val name: String,
    val price: BigDecimal,
    val ingredients: String,
    val tasteTags: List<String>
)