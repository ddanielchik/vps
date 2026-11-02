package ru.severnoyesiyanieBack.entity

import jakarta.persistence.*
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import java.math.BigDecimal
import jakarta.validation.constraints.Size


@Entity
@Table(name = "menu")

class MenuItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_position")
    var idPosition: Int? = null,

    @field:NotBlank
    @field:Size(max = 50)
    @Column(name = "name_product", nullable = false, length = 50)
    var name: String,

    @field:DecimalMin("0.0")
    @Column(
        name = "price",
        nullable = false,
        precision = 10,
        scale = 2
    )
    var price: BigDecimal,

    @field:Size(max = 500)
    @Column(name = "ingredients", length = 500)
    var ingredients: String? = null,


    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "menu_taste_tags",
        joinColumns = [JoinColumn(name = "id_position")],
        inverseJoinColumns = [JoinColumn(name = "id_tag")]
    )
    var tasteTags: MutableSet<TasteTag> = mutableSetOf()

)
