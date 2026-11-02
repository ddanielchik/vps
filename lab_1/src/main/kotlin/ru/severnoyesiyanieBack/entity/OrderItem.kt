package ru.severnoyesiyanieBack.entity

import jakarta.persistence.*
import jakarta.validation.constraints.Positive
import java.math.BigDecimal


@Entity
@Table(name = "order_items")
class OrderItem(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_order_item")
    var idItem: Int? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_order", nullable = false)
    var order: Order,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_product", nullable = false)
    var product: MenuItem,

    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    var unitPrice: BigDecimal,

    @field:Positive
    @Column(name = "quantity", nullable = false)
    var quantity: Int
)