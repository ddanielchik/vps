package ru.severnoyesiyanieback.menu.dto
import java.math.BigDecimal


data class MenuItemResponse(
    val idPosition: Int,
    val name: String,
    val price: BigDecimal,
    val ingredients: String,
    val tasteTags: List<String>
) {
    class Builder {
        private var idPosition: Int? = null
        private var name: String? = null
        private var price: BigDecimal? = null
        private var ingredients: String? = null
        private var tasteTags: List<String>? = null

        fun idPosition(value: Int) = apply { idPosition = value }
        fun name(value: String) = apply { name = value }
        fun price(value: BigDecimal) = apply { price = value }
        fun ingredients(value: String) = apply { ingredients = value }
        fun tasteTags(value: List<String>) = apply { tasteTags = value }

        fun build() = MenuItemResponse(
            idPosition = requireNotNull(idPosition) { "idPosition is required" },
            name = requireNotNull(name) { "name is required" },
            price = requireNotNull(price) { "price is required" },
            ingredients = requireNotNull(ingredients) { "ingredients is required" },
            tasteTags = requireNotNull(tasteTags) { "tasteTags is required" }
        )
    }
}
