package ru.severnoyesiyanieBack.entity

import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size


@Entity
@Table(name = "taste_tags")

class TasteTag (
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_tag")
    var idTag: Int? = null,

    @field:NotBlank
    @field:Size(max = 50)
    @Column(name = "name", nullable = false, unique = true,length = 50)
    var name: String
)
