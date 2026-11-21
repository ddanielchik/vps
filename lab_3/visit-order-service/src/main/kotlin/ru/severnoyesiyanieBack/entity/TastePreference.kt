package ru.severnoyesiyanieBack.entity

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size

@Embeddable
data class TastePreference(
    @field:Positive
    @Column(name = "id_tag", nullable = false)
    val idTag: Int,

    @field:NotBlank
    @field:Size(max = 50)
    @Column(name = "name", nullable = false, length = 50)
    val name: String
)
