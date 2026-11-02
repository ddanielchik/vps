package ru.severnoyesiyanieBack.entity

import jakarta.persistence.*
import jakarta.validation.constraints.DecimalMin
import java.math.BigDecimal
import java.time.LocalDateTime

@Entity
@Table(name = "orders")
class Order(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_order")
    var idOrder: Int? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_visit", nullable = false)
    var visit: Visit,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_waiter")
    var waiter: User? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id_worker")
    var worker: User? = null,

    @field: DecimalMin("0.0", inclusive = true)
    @Column(
        name = "total_amount",
        nullable = false,
        precision = 10,
        scale = 2
    )
    var totalAmount: BigDecimal,

    @Column(name = "created_at", nullable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @OneToMany(mappedBy = "order", cascade = [CascadeType.PERSIST])
    var items: MutableList<OrderItem> = mutableListOf()
)